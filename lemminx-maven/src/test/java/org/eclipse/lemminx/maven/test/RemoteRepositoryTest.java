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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.maven.MavenLemminxExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RemoteRepositoryTest {

	@Rule public NoMavenCentralIndexTestRule rule = new NoMavenCentralIndexTestRule();

	private XMLLanguageService languageService;

	@Before
	public  void setUp() throws IOException {
		languageService = new XMLLanguageService();
	}

	@After
	public  void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}

	private void loopUntilCompletionItemFound(DOMDocument document, Position position, String expectedLabel) throws InterruptedException {
		final SharedSettings settings = new SharedSettings();
		List<CompletionItem> items = Collections.emptyList();
		do {
			items = languageService.doComplete(document, position, settings).getItems();
			Thread.sleep(500);
		} while (items.stream().map(CompletionItem::getLabel).noneMatch(expectedLabel::equals));
	}

	@Test(timeout=15000)
	public void testRemoteGroupIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-groupId-complete.xml", languageService), //
				new Position(11, 20), "signaturacaib");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}
	
	@Test(timeout=150000)
	public void testRemoteArtifactIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-artifactId-complete.xml", languageService), //
				new Position(12, 15), "signaturacaib.core - signaturacaib:signaturacaib.core:3.3.0");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}
	
	@Test(timeout=15000)
	public void testRemoteVersionCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-version-complete.xml", languageService), //
				new Position(13, 13), "3.3.0");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test(timeout=15000)
 	public void testRemoteArtifactHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		String description = "A custom implementation of XPath 1.0 based upon apache commons jxpath 1.3";
		final DOMDocument document = createDOMDocument("/pom-remote-artifact-hover.xml", languageService);
		final Position position = new Position(14, 18);
 		Hover hover;
 		do {
 	 		hover = languageService.doHover(document, position, new SharedSettings());
 	 		Thread.sleep(500);
 		} while (!(((MarkupContent) hover.getContents().getRight()).getValue().contains(description)));
 		// if got out of the loop without timeout, then test is PASSED
	}
	
	@Test(timeout=15000)
 	public void testDownloadArtifactOnHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		languageService.initializeIfNeeded();
		File mavenRepo = languageService.getExtensions().stream() //
			.filter(MavenLemminxExtension.class::isInstance) //
			.map(MavenLemminxExtension.class::cast) //
			.findAny() //
			.map(mavenLemminxPlugin -> mavenLemminxPlugin.getMavenSession().getRepositorySession().getLocalRepository().getBasedir())
			.get();
		File artifactDirectory = new File(mavenRepo, "org/glassfish/jersey/project/2.19");
		String description = "Jersey is the open source (under dual CDDL+GPL license) JAX-RS 2.0 (JSR 339)";
		final DOMDocument document = createDOMDocument("/pom-remote-artifact-download-hover.xml", languageService);
		final Position position = new Position(14, 18);
		assertFalse(artifactDirectory.exists());
 		Hover hover;
 		do {
 	 		hover = languageService.doHover(document, position, new SharedSettings());
 	 		Thread.sleep(500);
 		} while (hover == null);
 		
 		assertTrue(artifactDirectory.exists());
 		assertTrue(artifactDirectory.listFiles().length > 0);
 		assertTrue(hover.getContents().getRight().getValue().contains(description));
	}
	
	@Test(timeout=15000)
	public void testRemotePluginGroupIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-groupId-complete.xml", languageService), //
				new Position(11, 14), "org.codehaus.mojo");
		// if got out of the loop without timeout, then test is PASSED
	}
	
	@Test(timeout=15000)
	public void testRemotePluginArtifactIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-artifactId-complete.xml", languageService), //
				new Position(12, 15), "deb-maven-plugin - org.codehaus.mojo:deb-maven-plugin:1.0-beta-1");
		// if got out of the loop without timeout, then test is PASSED
	}
	
	@Test(timeout=15000)
	public void testRemotePluginVersionCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-version-complete.xml", languageService), //
				new Position(13, 12), "1.0-beta-1");
		// if got out of the loop without timeout, then test is PASSED
	}

}
