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

import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenDefinitionParticipantTest {
	
	@Test
	public void testFullyQualifiedDependency() throws Exception {
		XMLLanguageService languageService = new MavenLanguageService();
		List<? extends LocationLink> definitions = languageService.findDefinition(createDOMDocument("/pom-localrepo-test-dependencies.xml", languageService), new Position(14, 20), ()->{});
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-compiler-plugin-3.8.1.pom")));
	}

	@Test
	public void testDependencyWithVersionAsProperty() throws Exception {
		XMLLanguageService languageService = new MavenLanguageService();
		List<? extends LocationLink> definitions = languageService.findDefinition(createDOMDocument("/pom-localrepo-test-dependencies-propertyVersion.xml", languageService), new Position(17, 20), ()->{});
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("maven-compiler-plugin-3.8.1.pom")), definitions.toString());
	}

	
	@Test
	public void testParentFromRepo() throws Exception {
		XMLLanguageService languageService = new MavenLanguageService();
		List<? extends LocationLink> definitions = languageService.findDefinition(createDOMDocument("/parent-from-repo.pom", languageService), new Position(6, 30), ()->{});
		assertTrue(definitions.stream().map(LocationLink::getTargetUri).anyMatch(uri -> uri.endsWith("spring-boot-starter-parent-2.6.2.pom")));
	}
}