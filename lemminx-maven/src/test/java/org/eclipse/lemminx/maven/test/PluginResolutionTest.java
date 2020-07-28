/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.eclipse.lemminx.maven.MavenPlugin;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PluginResolutionTest {

	@Rule
	public NoMavenCentralIndexTestRule rule = new NoMavenCentralIndexTestRule();

	private XMLLanguageService languageService;

	private File initialMavenPluginApiDirectory;
	private File movedMavenPluginApiDirectory;

	@Before
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
		languageService.initializeIfNeeded();
		File mavenRepo = MavenPlugin.getRepositorySystemSession().getLocalRepository().getBasedir();
		initialMavenPluginApiDirectory = new File(mavenRepo, "org/apache/maven/maven-plugin-api/3.0");
		if (initialMavenPluginApiDirectory.exists()) {
			movedMavenPluginApiDirectory = new File(initialMavenPluginApiDirectory.getParent(), initialMavenPluginApiDirectory.getName() + "-moved");
			if (!movedMavenPluginApiDirectory.exists()) {
				initialMavenPluginApiDirectory.renameTo(movedMavenPluginApiDirectory);
			}
		}
		FileUtils.deleteDirectory(initialMavenPluginApiDirectory);
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException, IOException {
		languageService.dispose();
		languageService = null;
		if (movedMavenPluginApiDirectory != null) {
			movedMavenPluginApiDirectory.renameTo(initialMavenPluginApiDirectory);
			FileUtils.deleteDirectory(movedMavenPluginApiDirectory);
		}
	}

	@Test
	public void testPluginConfigurationHoverMissingTransitiveDependency()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertFalse(initialMavenPluginApiDirectory.exists());
		// <compilerArguments> hover
		
		SharedSettings settings = new SharedSettings();
		HoverCapabilities hover = new HoverCapabilities();
		String capabilities[] = { MarkupKind.MARKDOWN };
		hover.setContentFormat(Arrays.asList(capabilities));
		
		settings.getHoverSettings().setCapabilities(hover);
		
		String hoverContents = languageService
				.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
						new Position(15, 8), settings)
				.getContents().getRight().getValue();
		assertTrue(hoverContents.contains("**Type:** List&lt;String&gt;"));
		assertTrue(hoverContents.contains("Sets the arguments to be passed to the compiler"));
	}

}
