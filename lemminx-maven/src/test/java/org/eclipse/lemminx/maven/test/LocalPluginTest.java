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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LocalPluginTest {

	@Rule public NoMavenCentralIndexTestRule rule = new NoMavenCentralIndexTestRule();

	private XMLLanguageService languageService;
	
	private SharedSettings getMarkdownSharedSettings () {
		SharedSettings settings = new SharedSettings();
		HoverCapabilities hover = new HoverCapabilities();
		String capabilities[] = { MarkupKind.MARKDOWN };
		hover.setContentFormat(Arrays.asList(capabilities));
		
		settings.getHoverSettings().setCapabilities(hover);
		
		return settings;
	}

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
		List<CompletionItem> completions = languageService.doComplete(createDOMDocument("/pom-complete-plugin-goal.xml", languageService),
				new Position(23, 7), new SharedSettings())
			.getItems();
		assertTrue(completions.stream().map(CompletionItem::getLabel).filter("failIfNoTests"::equals).count() == 1);
	}

	@Test
	public void testCompleteConfigurationParametersInTag() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-plugin-config-tag.xml", languageService),
				new Position(20, 9), new SharedSettings())
			.getItems().stream().map(CompletionItem::getLabel).anyMatch("failIfNoTests"::equals));
	}
	
	@Test
	public void testCompleteConfigurationParametersInTagDuplicates() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		List<CompletionItem> completions = languageService.doComplete(createDOMDocument("/pom-plugin-config-tag.xml", languageService),
				new Position(20, 9), new SharedSettings())
			.getItems();
		assertTrue(completions.stream().map(CompletionItem::getLabel).filter("failIfNoTests"::equals).count() == 1);
	}
	
	@Test
	public void testCompleteConfigurationParametersDuplicates() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		List<CompletionItem> completions = languageService.doComplete(createDOMDocument("/pom-duplicate-configuration-completion.xml", languageService),
				new Position(15, 4), new SharedSettings())
			.getItems();
		assertTrue(completions.stream().map(CompletionItem::getLabel).filter("compilePath"::equals).count() == 1);
	}
	
	// Test related to https://github.com/eclipse/lemminx-maven/issues/75
	@Test
	public void testCompleteConfigurationParametersMissingBug() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		Position pos = new Position(11, 23);
		List<CompletionItem> completions = languageService.doComplete(createDOMDocument("/pom-plugin-diagnostic-missing-groupid.xml", languageService),
				pos, new SharedSettings())
			.getItems();
		assertTrue(completions.stream().map(CompletionItem::getLabel).anyMatch("release"::equals));
	}
	
	// Test related to https://github.com/eclipse/lemminx-maven/issues/114
	@Test
	public void testFileCompletionDuplicatePrefix() throws IOException, URISyntaxException {
		Position pos = new Position(11, 26);
		List<CompletionItem> completions = languageService.doComplete(createDOMDocument("/pom-complete-file-path.xml", languageService),
				pos, new SharedSettings())
			.getItems();
		// Expected replace range is the whole text node range
		Range expectedReplaceRange = new Range(new Position(11, 10), new Position(11, 26));
		
		assertTrue(completions.stream().map(item -> item.getTextEdit().getRange()).anyMatch(expectedReplaceRange::equals));
	}
	
	// Hover related tests
	
	@Test
 	public void testPluginConfigurationHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-configuration-hover.xml", languageService),
				new Position(15, 6), new SharedSettings()).getContents().getRight().getValue().contains("cause a failure if there are no tests to run"));
	}
	
	@Test
 	public void testPluginNestedConfigurationHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		//<compilerArguments> hover
		String hoverContents = languageService.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
				new Position(15, 8), getMarkdownSharedSettings()).getContents().getRight().getValue();
		assertTrue(hoverContents.contains("**Type:** List&lt;String&gt;"));
		assertTrue(hoverContents.contains("Sets the arguments to be passed to the compiler"));
		
		//<arg> hover, child of compilerArguments
		//Should have a different type, but sames description
		hoverContents = languageService.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
				new Position(16, 8), getMarkdownSharedSettings()).getContents().getRight().getValue();
		assertTrue(hoverContents.contains("**Type:** String"));
		assertTrue(hoverContents.contains("Sets the arguments to be passed to the compiler"));
		
		//<annotationProcessorPath> hover
		hoverContents = languageService.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
				new Position(20, 9), getMarkdownSharedSettings()).getContents().getRight().getValue();
		//annotationProcessorPath type should be a DependencyCoordinate and its description should be that of annotationsProcessorPath
		assertTrue(hoverContents.contains("org.apache.maven.plugin.compiler.DependencyCoordinate"));
		assertTrue(hoverContents.contains("Classpath elements to supply as annotation processor path."));
		
		
		//<groupId> hover
		hoverContents = languageService.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
				new Position(21, 9), getMarkdownSharedSettings()).getContents().getRight().getValue();
		//GroupId type should be a string and its description should be that of annotationsProcessorPath
		assertTrue(hoverContents.contains("**Type:** String"));
		assertTrue(hoverContents.contains("Classpath elements to supply as annotation processor path."));

	}
	
	@Test
 	public void testPluginGoalHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-goal-hover.xml", languageService),
				new Position(18, 22), new SharedSettings()).getContents().getRight().getValue().contains("Run tests using Surefire."));
	}
	
	@Test
 	public void testPluginArtifactHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException, TimeoutException {
		assertTrue(languageService.doHover(createDOMDocument("/pom-plugin-artifact-hover.xml", languageService),
				new Position(14, 18), new SharedSettings()).getContents().getRight().getValue().contains("Maven Surefire MOJO in maven-surefire-plugin"));
	}
	
	// Diagnostic related tests

	@Test(timeout=30000)
	public void testPluginConfigurationDiagnostics() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-plugin-configuration-diagnostic.xml", languageService);
		assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage)
				.anyMatch(message -> message.contains("Invalid plugin configuration")));
		assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).size() == 2);
	}
	
	@Test
	public void testPluginGoalDiagnostics() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-plugin-goal-diagnostic.xml", languageService);
		assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage)
				.anyMatch(message -> message.contains("Invalid goal for this plugin")));
		assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).size() == 2);
	}
	
	@Test
	public void testDiagnosticMissingGroupId() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-plugin-diagnostic-missing-groupid.xml", languageService);
		List<Diagnostic> diagnostics = languageService.doDiagnostics(document, () -> {
		}, new XMLValidationSettings());
		assertTrue(diagnostics.size() == 0); 
	}
	
	@Test
	public void testGoalDiagnosticsNoFalsePositives()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-plugin-valid-goal-diagnostic.xml", languageService);
		assertTrue(languageService.doDiagnostics(document, () -> {
		}, new XMLValidationSettings()).size() == 0);
	}

	@Test
	public void testParentPluginManagementResolved() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-pluginManagement-child.xml", languageService);
		assertEquals(Collections.emptyList(), languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()));
	}
}
