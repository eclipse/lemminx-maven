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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.settings.XMLHoverSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class ManagedVersionHoverTest {

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
	@Timeout(90000)
	public void testManagedVersionHoverInDependencyChild() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-dependencyManagement-child.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(15, 25), createSharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testManagedVersionHoverInDependencyChild: [" + value + "]");
		assertNotNull(value);
		assertTrue(value.contains("The managed version is"));
		assertTrue(value.contains("2.22.2"));
		assertTrue(value.contains("The artifact is managed in"));
		assertTrue(value.contains("dependencyManagement:parent:0.0.1-SNAPSHOT"));
		assertTrue(value.contains("pom-dependencyManagement-parent.xml"));
	}

	@Test
	@Timeout(90000)
	public void testManagedVersionHoverInDependencyParent() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-dependencyManagement-parent.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(13, 29), createSharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testManagedVersionHoverInDependencyParent: [" + value + "]");
		assertNotNull(value);
		assertFalse(value.contains("The managed version is"));
		assertFalse(value.contains("The artifact is managed in"));
	}

	@Test
	@Timeout(90000)
	public void testManagedVersionHoverInPluginChild() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-pluginManagement-child.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(18, 33), createSharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testManagedVersionHoverInPluginChild: [" + value + "]");
		assertNotNull(value);
		assertTrue(value.contains("The managed version is"));
		assertTrue(value.contains("2.22.2"));
		assertTrue(value.contains("The artifact is managed in"));
		assertTrue(value.contains("pluginManagement:parent:0.0.1-SNAPSHOT"));
		assertTrue(value.contains("pom-pluginManagement-parent.xml"));
	}

	@Test
	@Timeout(90000)
	public void testManagedVersionHoverInPluginParent() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-pluginManagement-parent.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(14, 33), createSharedSettings());
		String value = ((MarkupContent) hover.getContents().getRight()).getValue();
		System.out.println("testManagedVersionHoverInPluginParent: [" + value + "]");
		assertNotNull(value);
		assertFalse(value.contains("The managed version is"));
		assertFalse(value.contains("The artifact is managed in"));
	}
	
	// Enable MARKDOWN format
	private static SharedSettings createSharedSettings() {
		HoverCapabilities hoverCapabilities = new HoverCapabilities();
		hoverCapabilities.setContentFormat(Arrays.asList(MarkupKind.MARKDOWN));
		SharedSettings sharedSettings = new SharedSettings();
		sharedSettings.getHoverSettings().setCapabilities(hoverCapabilities);
		return  sharedSettings;
	}
}
