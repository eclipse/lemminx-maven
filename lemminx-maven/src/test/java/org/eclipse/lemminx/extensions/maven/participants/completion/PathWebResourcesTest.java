/*******************************************************************************
* Copyright (c) 2022 Red Hat Inc. and others.
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
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class PathWebResourcesTest {

	private XMLLanguageService languageService;

	@BeforeEach
	public void setUp() throws IOException {
		languageService = new MavenLanguageService();
	}

	@AfterEach
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}

//	public static final String DIRECTORY_ELT = "directory";
	@Test
	public void testWebResourcesDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(0, 11), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}
	
//	public static final String TARGET_PATH_ELT = "targetPath";
	@Test
	public void testWebResourcesTargetPath() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(9, 12), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}

//	public static final String SOURCE_DIRECTORY_ELT = "sourceDirectory";
	@Test
	public void testWebResourcesSourceDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(1, 17), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}

//	public static final String SCRIPT_SOURCE_DIRECTORY_ELT = "scriptSourceDirectory";
	@Test
	public void testWebResourcesScriptSourceDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(2, 23), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}

//	public static final String TEST_SOURCE_DIRECTORY_ELT = "testSourceDirectory";
	@Test
	public void testWebResourcesTestSourceDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(3, 21), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}

//	public static final String OUTPUT_DIRECTORY_ELT = "outputDirectory";
	@Test
	public void testWebResourcesOutputDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(4, 17), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}

//	public static final String TEST_OUTPUT_DIRECTORY_ELT = "testOutputDirectory";
	@Test
	public void testWebResourcesTestOutputDirectory() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(5, 21), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("..", doComplete.get(0).getLabel());
		assertEquals("child-parent", doComplete.get(1).getLabel());
		assertEquals("folder1", doComplete.get(2).getLabel());
		assertEquals("folder2", doComplete.get(3).getLabel());
	}
	
//	public static final String FILTERS_ELT = "filters";
//	public static final String FILTER_ELT = "filter";
	@Test
	public void testWebResourcesFilter() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(6, 17), new SharedSettings()).getItems();

		// parent folder, then direct children
		assertEquals("filter.properties", doComplete.get(0).getLabel());
		assertEquals("child-parent/filter.properties", doComplete.get(1).getLabel());
		assertEquals("folder1/filter.properties", doComplete.get(2).getLabel());
		assertEquals("folder2/filter.properties", doComplete.get(3).getLabel());
	}

//	public static final String FILE_ELT = "file";
//	public static final String EXISTS_ELT = "exists";
	@Test
	public void testWebResourcesFileExists() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(7, 14), new SharedSettings()).getItems();

		// files
		assertEquals("filter.properties", doComplete.get(0).getLabel());
		assertEquals("pom-pluginManagement-configuration.xml", doComplete.get(1).getLabel());
		assertEquals("pom-webresource.xml", doComplete.get(2).getLabel());
		assertEquals("pom.xml", doComplete.get(3).getLabel());

		// parent folder, then direct children
		assertEquals("..", doComplete.get(4).getLabel());
		assertEquals("child-parent", doComplete.get(5).getLabel());
		assertEquals("folder1", doComplete.get(6).getLabel());
		assertEquals("folder2", doComplete.get(7).getLabel());
	}

//	public static final String FILE_ELT = "file";
//	public static final String MISSING_ELT = "missing";
	@Test
	public void testWebResourcesFileMissing() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-webresource.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(8, 15), new SharedSettings()).getItems();

		// files
		assertEquals("filter.properties", doComplete.get(0).getLabel());
		assertEquals("pom-pluginManagement-configuration.xml", doComplete.get(1).getLabel());
		assertEquals("pom-webresource.xml", doComplete.get(2).getLabel());
		assertEquals("pom.xml", doComplete.get(3).getLabel());

		// parent folder, then direct children
		assertEquals("..", doComplete.get(4).getLabel());
		assertEquals("child-parent", doComplete.get(5).getLabel());
		assertEquals("folder1", doComplete.get(6).getLabel());
		assertEquals("folder2", doComplete.get(7).getLabel());
	}

	@Test
	public void testMojoParametersDirectories() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-pluginManagement-configuration.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(18, 42), new SharedSettings()).getItems();

		// Some typical conventions for directories
		// Default values
		assertEquals("${project.build.outputDirectory}", doComplete.get(0).getLabel());
		// parent
		assertEquals("..", doComplete.get(1).getLabel());
		// then child folders
		assertEquals("child-parent", doComplete.get(2).getLabel());
		assertEquals("folder1", doComplete.get(3).getLabel());
		assertEquals("folder2", doComplete.get(4).getLabel());
	}

	@Test
	public void testMojoParametersFiles() throws Exception {
		DOMDocument document = createDOMDocument("/local-path/pom-pluginManagement-configuration.xml", languageService);
		List<CompletionItem> doComplete = languageService.doComplete(document, new Position(19, 39), new SharedSettings()).getItems();

		// Some typical conventions
		assertEquals("filter.properties", doComplete.get(0).getLabel());
		assertEquals("pom-pluginManagement-configuration.xml", doComplete.get(1).getLabel());
		assertEquals("pom-webresource.xml", doComplete.get(2).getLabel());
		assertEquals("pom.xml", doComplete.get(3).getLabel());
		// parent folder, then direct children
		assertEquals("..", doComplete.get(4).getLabel());
		assertEquals("child-parent", doComplete.get(5).getLabel());
		assertEquals("folder1", doComplete.get(6).getLabel());
		assertEquals("folder2", doComplete.get(7).getLabel());
	}

}