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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalRepoTests {


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

	@Test(timeout=90000)
	public void testCompleteDependency()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-with-dependency.xml", languageService), new Position(11, 7), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("org.apache.maven:maven-core")));
	}

	@Test(timeout=90000)
	public void testCompleteLocalGroupdId()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-local-groupId-complete.xml", languageService), new Position(11, 12), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("org.apache.maven")));
	}
	
	@Test(timeout=90000)
	public void testDoNotCompleteNonExistingArtifact()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-non-existing-dep.xml", languageService), new Position(10, 27), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).noneMatch(label -> label.contains("some.fake.group")));
	}
}
