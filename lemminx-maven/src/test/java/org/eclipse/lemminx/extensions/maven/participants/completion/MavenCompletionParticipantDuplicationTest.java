/*******************************************************************************
 * Copyright (c) 2022, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.completion;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.lemminx.extensions.maven.searcher.RemoteCentralRepositorySearcher;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MavenCompletionParticipantDuplicationTest {
	private static  XMLLanguageService languageService;
	
	@BeforeAll
	public static void setUp() throws IOException, URISyntaxException {
		RemoteCentralRepositorySearcher.disableCentralSearch = false;
		languageService = new XMLLanguageService();

		// "Build" test project - instead of real build we'll
		// set up the local repository with a required test artifact
		File localRepoDirectory = System.getProperty("maven.repo.local") != null ?
				new File(System.getProperty("maven.repo.local")).getAbsoluteFile() :
				new File("target/testlocalrepo").getAbsoluteFile();
		assertTrue(localRepoDirectory.exists() && localRepoDirectory.isDirectory(), "Local Repo doesn't exist or not a directory: " + localRepoDirectory.getAbsolutePath());
		
		File source = new File(MavenLemminxTestsUtils.class.getResource("/io").toURI());
		File destinattion = new File(localRepoDirectory, "io");
		copy(source.getAbsoluteFile().toPath(), destinattion.getAbsoluteFile().toPath());
	}

	@AfterAll
	public static void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}
	
	@Test
	public void testDuplicateCompletionGroupIds() throws IOException, URISyntaxException {
		// Check Content Assist result
		List<CompletionItem> completions = languageService.doComplete(
					createDOMDocument("/pom-duplicate-groupid-completion.xml", languageService),
					new Position(7, 26), new SharedSettings())
				.getItems();
		assertDuplications(completions);
		assertTrue(completions.stream().filter(i -> "io.quarkus.arc".equals(i.getLabel())).findFirst().isPresent());
	}
	
	@Test
	public void testDuplicateCompletionVersion() throws IOException, URISyntaxException {
		// Check Content Assist result
		List<CompletionItem> completions = languageService.doComplete(
					createDOMDocument("/pom-duplicate-groupid-completion.xml", languageService),
					new Position(9, 16), new SharedSettings())
				.getItems();
		assertDuplications(completions);
		assertTrue(completions.stream().filter(i -> "2.7.1.Final".equals(i.getLabel())).findFirst().isPresent());
	}
	
	@Test
	public void testDuplicateCompletionVersionWithRemoteRepo() throws IOException, URISyntaxException {
		// Check Content Assist result
		List<CompletionItem> completions = languageService.doComplete(
					createDOMDocument("/pom-duplicate-version-completion.xml", languageService),
					new Position(15, 17), new SharedSettings())
				.getItems();
		assertDuplications(completions);
		assertTrue(completions.stream().filter(i -> "3.8.1".equals(i.getLabel())).findFirst().isPresent());
	}

	@Test
	public void testDuplicateCompletionVersionOrder() throws IOException, URISyntaxException {
		// Check Content Assist result
		List<CompletionItem> completions = languageService.doComplete(
					createDOMDocument("/pom-duplicate-version-completion.xml", languageService),
					new Position(15, 13), new SharedSettings())
				.getItems();
		
		List<CompletionItem> orderedCompletions = completions.stream()
				.sorted(new Comparator<CompletionItem>() {
					// Backward order
					@Override
					public int compare(CompletionItem o1, CompletionItem o2) {
						String sortText1 = o1.getSortText() != null ? o1.getSortText() : o1.getLabel();
						String sortText2 = o2.getSortText() != null ? o2.getSortText() : o2.getLabel();
						return new DefaultArtifactVersion(sortText2).compareTo(new DefaultArtifactVersion(sortText1));
					}
				}).toList();
		assertEquals(orderedCompletions, completions);
	}
	
	private void assertDuplications(List<CompletionItem> completions) {
		Set<Object> labels = Collections.synchronizedSet(new HashSet<>());
		for (CompletionItem i : completions) {
			assertFalse(labels.contains(i.getLabel()), "Duplicate completion label found: " + i.getLabel());
			labels.add(i.getLabel());
		}
	}

	private static void copy(Path src, Path dest) throws IOException {
		try (Stream<Path> stream = Files.walk(src)) {
			stream.forEach(source -> {
				try {
					Files.copy(source, dest.resolve(src.relativize(source)), StandardCopyOption.REPLACE_EXISTING);
				} catch (DirectoryNotEmptyException e) {
					// ignore
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			});
		}
	}
}
