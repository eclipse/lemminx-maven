/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
import java.util.concurrent.TimeUnit;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.searcher.RemoteCentralRepositorySearcher;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RemoteCentralRepoAssistanceTest {

	private XMLLanguageService languageService;

	@BeforeAll
	static void setup() {
		RemoteCentralRepositorySearcher.disableCentralSearch = false;
	}	
	
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
		} while (items.stream().map(CompletionItem::getLabel)
				.map(l-> { 
						int index = l.lastIndexOf(':');
						return index >= 0 ? l.substring(0, index) : l;
					})
				.noneMatch(expectedLabel::startsWith));
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralGroupIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-groupId-complete.xml"), //
				new Position(11, 33), "org.fujion.webjars");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralArtifactIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-artifactId-complete.xml"), //
				new Position(12, 24), "webjar-angular - org.fujion.webjars:webjar-angular:");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralVersionCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-version-complete.xml"), //
				new Position(13, 21), "13.1.1-1");
		// if we get out of the loop, then it's OK; otherwise we get a timeout
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralPluginGroupIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-plugin-groupId-complete.xml"), //
				new Position(12, 45), "com.github.gianttreelp.proguardservicesmapper");
		// if got out of the loop without timeout, then test is PASSED
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralPluginArtifactIdCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-plugin-artifactId-complete.xml"), //
				new Position(13, 24), "proguard-services-mapper-maven - com.github.gianttreelp.proguardservicesmapper:proguard-services-mapper-maven:");
		// if got out of the loop without timeout, then test is PASSED
	}

	@Test
	@Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
	void testRemoteCentralPluginVersionCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		loopUntilCompletionItemFound(createDOMDocument("/pom-remote-central-plugin-version-complete.xml"), //
				new Position(14, 21), "1.0");
		// if got out of the loop without timeout, then test is PASSED
	}
}
