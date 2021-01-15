/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.completion;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralIndexExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralIndexExtension.class)
public class PathsTest {

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

	@Test
	public void testRelativePathNoPrefix() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(0, 14), new SharedSettings()).getItems();
		// Some typical conventions
		assertEquals("../pom.xml", doComplete.get(0).getLabel());
		assertEquals("../sibling-parent/pom.xml", doComplete.get(1).getLabel());
		assertEquals("child-parent/pom.xml", doComplete.get(2).getLabel());
		// parent folder, then direct children
		assertEquals("..", doComplete.get(3).getLabel());
		assertEquals("child-parent", doComplete.get(4).getLabel());
		assertEquals("folder1", doComplete.get(5).getLabel());
		assertEquals("folder2", doComplete.get(6).getLabel());
	}

	@Test
	public void testRelativePathWithPrefix() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(2, 17), new SharedSettings()).getItems();
		// Some typical conventions
		assertEquals("../pom.xml", doComplete.get(0).getLabel());
		assertEquals("../sibling-parent/pom.xml", doComplete.get(1).getLabel());
	}

	@Test
	public void testModulePathNoPrefix() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(1, 8), new SharedSettings()).getItems();
		// Some typical conventions: children folder with pom
		assertEquals("child-parent", doComplete.get(0).getLabel());
		assertEquals("folder2", doComplete.get(1).getLabel());
		// then children folders without pom
		assertEquals("folder1", doComplete.get(2).getLabel());
		// then parent
		assertEquals("..", doComplete.get(3).getLabel());
	}

	@Test
	public void testModuleWithPrefix() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(3, 16), new SharedSettings()).getItems();
		// Some typical conventions
		assertEquals("folder1/subfolder1", doComplete.get(0).getLabel());
		assertEquals("folder1/subfolderNoPom", doComplete.get(1).getLabel());
	}
}
