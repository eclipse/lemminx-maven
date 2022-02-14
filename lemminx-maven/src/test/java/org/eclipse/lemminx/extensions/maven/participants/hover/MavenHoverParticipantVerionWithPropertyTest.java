/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.hover;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.searcher.RemoteCentralRepositorySearcher;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MavenHoverParticipantVerionWithPropertyTest {
	private XMLLanguageService languageService;
	
	@BeforeEach
	public void setUp() throws IOException {
		RemoteCentralRepositorySearcher.disableCentralSearch = false;
		languageService = new XMLLanguageService();
	}

	@AfterEach
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}

	@Test
 	public void testPluginManagementPluginWithVerionProperty() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-property-in-version.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(14, 30), new SharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginManagementPluginWithVerionProperty: on group: " + value);
		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Apache Maven AntRun Plugin")));

 		hover = languageService.doHover(document, new Position(15, 33), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginManagementPluginWithVerionProperty: on artifact: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Apache Maven AntRun Plugin")));

 		hover = languageService.doHover(document, new Position(16, 32), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginManagementPluginWithVerionProperty: on version: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("1.8")));
	}

	@Test
 	public void testPluginWithVerionProperty() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-property-in-version.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(22, 26), new SharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginWithVerionProperty: on group: " + value);
		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Apache Maven Compiler Plugin")));

 		hover = languageService.doHover(document, new Position(23, 29), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginWithVerionProperty: on artifact: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Apache Maven Compiler Plugin")));

 		hover = languageService.doHover(document, new Position(24, 28), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testPluginWithVerionProperty: on version: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("3.5.1")));
	}

	@Test
 	public void testDependencyWithVerionProperty() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-property-in-version.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(30, 23), new SharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testDependencyWithVerionProperty: on group: " + value);
		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Maven Embedder")));

 		hover = languageService.doHover(document, new Position(31, 25), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testDependencyWithVerionProperty: on artifact: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Maven Embedder")));

 		hover = languageService.doHover(document, new Position(32, 24), new SharedSettings());
		value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testDependencyWithVerionProperty: on version: " + value);
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("3.0")));
	}

}
