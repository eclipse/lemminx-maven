/*******************************************************************************
 * Copyright (c) 2022, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MavenProjectCache;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class ParticipantUtilsTest {
	private MavenLanguageService languageService;

	@BeforeEach
	public void setUp() throws IOException {
		languageService = new MavenLanguageService();
	}

	@AfterEach
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}

	@Test
	public void testResolveValueWithProperties() throws IOException, URISyntaxException {
		MavenLemminxExtension plugin = new MavenLemminxExtension();
		plugin.start(null,languageService);

		DOMDocument document = createDOMDocument("/pom-with-properties.xml", languageService);
		languageService.didOpen(document);
		
		MavenProjectCache cache = plugin.getProjectCache();
		MavenProject project = cache.getLastSuccessfulMavenProject(document);
		assertNotNull(project);

		List<DOMNode> dependencyNodes = DOMUtils.findNodesByLocalName(document, DEPENDENCY_ELT);
		Optional<DOMNode> groupIdNode = dependencyNodes.stream().filter(DOMNode::isElement)
				.flatMap(node -> node.getChildren().stream())
				.filter(node -> GROUP_ID_ELT.equals(node.getLocalName())).findFirst();
		assertFalse(groupIdNode.isEmpty(), "groupID node not found");

		Optional<String> groupId = groupIdNode.get().getChildren().stream().map(DOMNode::getTextContent)
						.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).findFirst();
		assertTrue(groupId.isPresent(), "groupId value doesn't contain a property");

		String value =  groupId.get();
		String resolvedValue = ParticipantUtils.resolveValueWithProperties(project, value);
		assertTrue(value.contains("${"), "groupId value doesn't contain a property");
		assertEquals(resolvedValue, "org.$.test.0.0.1-SNAPSHOT");
	}
}
