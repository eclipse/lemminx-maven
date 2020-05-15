/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.settings.XMLHoverSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class LocalPluginTest {

	@Rule public NoMavenCentralIndexTestRule rule = new NoMavenCentralIndexTestRule();

	private XMLLanguageService languageService;

	@Before
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}
	
	// Completion related tests

	@Test
	public void testCompleteGoal() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-complete-plugin-goal.xml", languageService),
				new Position(18, 19), new SharedSettings())
			.getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText).anyMatch("test"::equals));
	}

	@Test
	public void testCompleteConfigurationParameters() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-complete-plugin-goal.xml", languageService),
				new Position(23, 7), new SharedSettings())
			.getItems().stream().map(CompletionItem::getLabel).anyMatch("failIfNoTests"::equals));
	}
	
	@Test
	public void testDuplicateCompletionConfigurationParameters() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-complete-plugin-goal.xml", languageService),
				new Position(23, 7), new SharedSettings())
			.getItems().stream().map(CompletionItem::getLabel).filter("failIfNoTests"::equals).count() == 1);
	}

	@Test
	public void testCompleteConfigurationParametersInTag() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-plugin-config-tag.xml", languageService),
				new Position(20, 9), new SharedSettings())
			.getItems().stream().map(CompletionItem::getLabel).anyMatch("failIfNoTests"::equals));
	}
	
	@Test
	public void testCompleteConfigurationParametersInTagDuplicates() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-plugin-config-tag.xml", languageService),
				new Position(20, 9), new SharedSettings())
			.getItems().stream().map(CompletionItem::getLabel).filter("failIfNoTests"::equals).count() == 1);
	}
	
	// Hover related tests
	
	@Test
 	public void testPluginConfigurationHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-configuration-hover.xml", languageService),
				new Position(15, 6), new XMLHoverSettings()).getContents().getRight().getValue().contains("cause a failure if there are no tests to run"));
	}
	
	@Test
 	public void testPluginGoalHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-goal-hover.xml", languageService),
				new Position(18, 22), new XMLHoverSettings()).getContents().getRight().getValue().contains("Run tests using Surefire."));
	}
	
	@Test
 	public void testPluginArtifactHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException, TimeoutException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-artifact-hover.xml", languageService),
				new Position(14, 18), new XMLHoverSettings()).getContents().getRight().getValue().contains("Maven Surefire MOJO in maven-surefire-plugin"));
	}
	
	// Diagnostic related tests

		@Test(timeout=30000)
	 	public void testPluginConfigurationDiagnostics() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
			DOMDocument document = createDOMDocument("/pom-plugin-configuration-diagnostic.xml", languageService);
			assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage)
					.anyMatch(message -> message.contains("Invalid plugin configuration")));
			assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).size() == 2);
		}
		
		// Temporarily disabled
		@Test
		@Ignore
	 	public void testPluginGoalDiagnostics() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
			DOMDocument document = createDOMDocument("/pom-plugin-goal-diagnostic.xml", languageService);
			assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage)
					.anyMatch(message -> message.contains("Invalid goal for this plugin")));
			assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).size() == 2);
		}
		

		// Tests regressions related to
		// https://github.com/eclipse/lemminx-maven/issues/48
		@Test
		public void testPluginDiagnosticMissingPlugin()
				throws IOException, InterruptedException, ExecutionException, URISyntaxException {
			DOMDocument document = createDOMDocument("/pom-plugin-diagnostic-missing-plugin.xml", languageService);
			System.out.println(languageService.doDiagnostics(document, () -> {
			}, new XMLValidationSettings()));
			// One diagnostic should be from a missing groupID (incomplete GAV), another should be
			// from unresolvable plugin (maven-shade-plugin:3.2.2)
			assertTrue(languageService.doDiagnostics(document, () -> {
			}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage).anyMatch(message -> message.contains(
					"Plugin org.apache.maven.plugins:maven-shade-plugin:3.2.2 or one of its dependencies could not be resolved")));
			assertTrue(languageService.doDiagnostics(document, () -> {
			}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage).anyMatch(message -> message.contains(
					"Incomplete GAV")));
			assertTrue(languageService.doDiagnostics(document, () -> {
			}, new XMLValidationSettings()).size() == 2); 
		}
}
