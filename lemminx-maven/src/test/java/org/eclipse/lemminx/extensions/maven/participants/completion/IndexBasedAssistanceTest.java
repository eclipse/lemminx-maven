/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.completion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralIndexExtension;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralIndexExtension.class)
public class IndexBasedAssistanceTest {

	private XMLLanguageService languageService;

	@BeforeEach
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
	}

	@AfterEach
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}

	private DOMDocument createDOMDocument(String path) throws IOException, URISyntaxException {
		Properties props = new Properties();
		String remoteRepoURL = System.getProperty("remoteRepoURL");
		if (remoteRepoURL != null) {
			props.put("remoteRepoURL", remoteRepoURL.replace('\\', '/'));
		}
		return MavenLemminxTestsUtils.createDOMDocument(path, props, languageService);
	}

	private void loopUntilCompletionItemFound(DOMDocument document, Position position, String expectedLabel)
			throws InterruptedException {
		final SharedSettings settings = new SharedSettings();
		List<CompletionItem> items = Collections.emptyList();
		do {
			items = languageService.doComplete(document, position, settings).getItems();
			Thread.sleep(500);
		} while (items.stream().map(CompletionItem::getLabel).noneMatch(expectedLabel::equals));
	}

	@Test
	public void testRemoteGroupIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-groupId-complete.xml"), //
				new Position(11, 20), "remote.repo.test");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	public void testRemoteArtifactIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-artifactId-complete.xml"), //
				new Position(12, 15), "test1-jar - remote.repo.test:test1-jar:2.0.0");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	public void testRemoteVersionCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-version-complete.xml"), //
				new Position(13, 13), "1.0.0");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	@Timeout(15000)
	public void testRemoteArtifactHover()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		String description = "This is a test description for test1";
		final DOMDocument document = createDOMDocument("/pom-remote-artifact-hover.xml");
		final Position position = new Position(14, 18);
		Hover hover;
		do {
			hover = languageService.doHover(document, position, new SharedSettings());
			Thread.sleep(500);
		} while (!(((MarkupContent) hover.getContents().getRight()).getValue().contains(description)));
		// if got out of the loop without timeout, then test is PASSED
	}

	@Test
	public void testRemotePluginGroupIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-groupId-complete.xml"), //
				new Position(11, 14), "remote.repo.test");
		// if got out of the loop without timeout, then test is PASSED
	}

	@Test
	public void testRemotePluginArtifactIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-artifactId-complete.xml"), //
				new Position(12, 15), "test-maven-plugin - remote.repo.test:test-maven-plugin:1.0.0");
		// if got out of the loop without timeout, then test is PASSED
	}

	@Test
	public void testRemotePluginVersionCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-plugin-version-complete.xml"), //
				new Position(13, 12), "1.0.0");
		// if got out of the loop without timeout, then test is PASSED
	}

}
