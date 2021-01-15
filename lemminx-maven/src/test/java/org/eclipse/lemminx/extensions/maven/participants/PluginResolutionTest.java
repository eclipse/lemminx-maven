/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralIndexExtension;
import org.eclipse.lemminx.extensions.maven.participants.diagnostics.MavenDiagnosticParticipant;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralIndexExtension.class)
public class PluginResolutionTest {

	private XMLLanguageService languageService;

	private File initialMavenPluginApiDirectory;
	private File movedMavenPluginApiDirectory;

	@BeforeEach
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
		languageService.initializeIfNeeded();
		File mavenRepo = languageService.getExtensions().stream() //
				.filter(MavenLemminxExtension.class::isInstance) //
				.map(MavenLemminxExtension.class::cast) //
				.findAny() //
				.map(mavenLemminxPlugin -> mavenLemminxPlugin.getMavenSession().getRepositorySession().getLocalRepository().getBasedir())
				.get();
		initialMavenPluginApiDirectory = new File(mavenRepo, "org/apache/maven/maven-plugin-api/3.0");
		if (initialMavenPluginApiDirectory.exists()) {
			movedMavenPluginApiDirectory = new File(initialMavenPluginApiDirectory.getParent(), initialMavenPluginApiDirectory.getName() + "-moved");
			if (!movedMavenPluginApiDirectory.exists()) {
				initialMavenPluginApiDirectory.renameTo(movedMavenPluginApiDirectory);
			}
		}
		FileUtils.deleteDirectory(initialMavenPluginApiDirectory);
	}

	@AfterEach
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

	@Test
    public void testPluginGoalValidationWithPluginWithJDKProfiles()
            throws IOException, InterruptedException, ExecutionException, URISyntaxException {
        MavenLemminxExtension plugin = new MavenLemminxExtension();
        MavenDiagnosticParticipant mavenDiagnosticParticipant = new MavenDiagnosticParticipant(plugin);
        languageService.getDiagnosticsParticipants().add(mavenDiagnosticParticipant);
        List<Diagnostic> diagnostics = languageService.doDiagnostics(createDOMDocument("/pom-plugin-goal-resolution.xml", languageService),
                new XMLValidationSettings(),
                () -> {});

        assertTrue(diagnostics.isEmpty(), "No validation error or warning found");
    }

}
