/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.definition;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

//@ExtendWith(NoMavenCentralExtension.class)
public class MavenHyperlinkDetectorParticipantTest {
	
	@Test
	public void testPluginManagementPluginFullyQualified() throws Exception {
		System.out.println("testPluginManagementFullyQualifiedPlugin()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
//		System.out.println("testPluginManagementFullyQualifiedPlugin(): TEXT: " + document.getText());
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(14, 32), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginManagementFullyQualifiedPlugin(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-jar-plugin-3.2.2.pom")));
	}

	@Test
	public void testPluginManagementPluginWithVersionAsProperty() throws Exception {
		System.out.println("testPluginManagementPluginWithVersionAsProperty()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(19, 32), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginManagementPluginWithVersionAsProperty(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-antrun-plugin-3.0.0.pom")));
	}

	@Test
	public void testPluginManagementPluginWithNoGroupId() throws Exception {
		System.out.println("testPluginManagementPluginWithNoGroupId()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(23, 32), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginManagementPluginWithNoGroupId(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-assembly-plugin-3.3.0.pom")));
	}

	@Test
	public void testPluginFullyQualified() throws Exception {
		System.out.println("testPluginFullyQualified()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(31, 28), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginFullyQualified(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-clean-plugin-3.1.0.pom")));
	}

	@Test
	public void testPluginWithVersionAsProperty() throws Exception {
		System.out.println("testPluginWithVersionAsProperty()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(36, 28), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginWithVersionAsProperty(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-compiler-plugin-3.9.0.pom")));
	}

	@Test
	public void testPluginWithNoGroupId() throws Exception {
		System.out.println("testPluginWithNoGroupId()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(40, 28), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginWithNoGroupId(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-antrun-plugin-3.0.0.pom")));
	}

	@Test
	public void testPluginWithNoVersion() throws Exception {
		System.out.println("testPluginWithNoVersion()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(45, 28), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginWithNoVersion(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-jar-plugin-3.2.2.pom")));
	}
	
	@Test
	public void testPluginWithNoGroupIdNoVersion() throws Exception {
		System.out.println("testPluginWithNoGroupIdNoVersion()");
		XMLLanguageService languageService = new MavenLanguageService();
		DOMDocument document = createDOMDocument("/pom-remote-test-plugin-hypertlink.xml", languageService);
		List<? extends LocationLink> definitions = languageService.findDefinition(document, new Position(48, 28), ()->{});
		definitions.stream().map(LocationLink::getTargetUri).forEach(uri -> System.out.println("testPluginWithNoGroupIdNoVersion(): " + uri));
		
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-assembly-plugin-3.3.0.pom")));
	}

}