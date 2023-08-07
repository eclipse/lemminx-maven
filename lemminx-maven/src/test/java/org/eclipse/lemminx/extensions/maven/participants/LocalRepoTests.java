/*******************************************************************************
 * Copyright (c) 2019, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.extensions.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class LocalRepoTests {


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

	@Test
	@Timeout(90000)
	public void testCompleteDependency()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-with-dependency.xml", languageService), new Position(11, 7), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("org.apache.maven:maven-core")));
	}

	@Test
	@Timeout(90000)
	public void testCompleteLocalGroupdId()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-local-groupId-complete.xml", languageService), new Position(11, 12), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("org.apache.maven")));
	}

	@Test
	@Timeout(90000)
	public void testDoNotCompleteNonExistingArtifact()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-non-existing-dep.xml", languageService), new Position(10, 27), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).noneMatch(label -> label.contains("some.fake.group")));
	}

	@Test
	public void testDefinitionManagedDependency() throws IOException, URISyntaxException {
		assertTrue(languageService.findDefinition(createDOMDocument("/pom-dependencyManagement-child.xml", languageService), new Position(17, 6), () -> {})
				.stream().map(LocationLink::getTargetUri)
				.anyMatch(uri -> uri.endsWith("/pom-dependencyManagement-parent.xml")));
	}
	
	@Test
	public void testLoadLocalRepo() throws IOException, URISyntaxException, InterruptedException {
		MavenLemminxExtension plugin = new MavenLemminxExtension();
		plugin.start(null,languageService);
		try {
			LocalRepositorySearcher searcher = plugin.getLocalRepositorySearcher();
			Path localTempRepo = plugin.getMavenSession().getRequest().getLocalRepositoryPath().toPath();
			assertTrue(localTempRepo.toString().contains("lemminx-maven"),
					"Temporary local repository isn't set");
			
			final String groupSeparator = ".";
			final String separator = localTempRepo.getFileSystem().getSeparator();
	
			long start = System.currentTimeMillis();
			Collection<Artifact> artifacts = searcher.getLocalArtifactsLastVersion();
			assertNotNull(artifacts, "Temporary local repository is NULLy");
			assertTrue(artifacts.size() > 0, "Temporary local repository is empty");
			
			System.out.println("\ntestLoadLocalRepo: loaded artifacts:\n");
			long stop = System.currentTimeMillis();
			artifacts.stream()
				.map(a -> 
					new File((a.getGroupId().replace(groupSeparator, separator)) 
							+ separator + a.getArtifactId()).toPath().toString()
						+ " => " + a) 
				.sorted().forEach(System.out::println);
			System.out.println("\ntestLoadLocalRepo: " + artifacts.size() + " loaded in " + (stop - start) + " ms");
		} finally {
			plugin.stop(languageService);
		}
	}
}
