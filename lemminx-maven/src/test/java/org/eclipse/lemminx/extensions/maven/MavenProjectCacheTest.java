/*******************************************************************************
 * Copyright (c) 2019-2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.services.extensions.IWorkspaceServiceParticipant;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralIndexExtension.class)
public class MavenProjectCacheTest {

	@Test
	public void testSimpleProjectIsParsed() throws Exception {
		URI uri = getClass().getResource("/pom-with-properties.xml").toURI();
		String content = FileUtils.readFileToString(new File(uri), "UTF-8");
		DOMDocument doc = new DOMDocument(new TextDocument(content, uri.toString()), null);
		MavenLemminxExtension plugin = new MavenLemminxExtension();
		MavenProjectCache cache = plugin.getProjectCache();
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		assertNotNull(project);
	}

	@Test
	public void testOnBuildError_ResolveProjectFromDocumentBytes() throws Exception {
		URI uri = getClass().getResource("/pom-with-module-error.xml").toURI();
		File pomFile = new File(uri);
		String content = FileUtils.readFileToString(pomFile, "UTF-8");
		DOMDocument doc = new DOMDocument(new TextDocument(content, uri.toString()), null);
		MavenLemminxExtension plugin = new MavenLemminxExtension();
		MavenProjectCache cache = plugin.getProjectCache();
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		assertNotNull(project);
	}

	@Test
	public void testParentChangeReflectedToChild()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException, Exception {
		MavenLemminxExtension plugin = new MavenLemminxExtension();
		MavenProjectCache cache = plugin.getProjectCache();
		DOMDocument doc = getDocument("/pom-with-properties-in-parent.xml");
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		assertTrue(project.getProperties().containsKey("myProperty"), project.getProperties().toString());
		URI parentUri = getClass().getResource("/pom-with-properties.xml").toURI();
		File parentPomFile = new File(parentUri);
		String initialContent = FileUtils.readFileToString(parentPomFile, "UTF-8");
		try {
			String content = initialContent.replaceAll("myProperty", "modifiedProperty");
			Files.writeString(parentPomFile.toPath(), content);
			doc.getTextDocument().setVersion(2); // Simulate some change
			MavenProject modifiedProject = cache.getLastSuccessfulMavenProject(doc);
			assertTrue(modifiedProject.getProperties().containsKey("modifiedProperty"),
					modifiedProject.getProperties().toString());
		} finally {
			Files.writeString(parentPomFile.toPath(), initialContent);
		}
	}

	@Test
	public void testAddFolders_didChangeWorkspaceFolders() throws Exception {
		XMLLanguageService languageService = new XMLLanguageService();
		IWorkspaceServiceParticipant workspaceService = languageService.getWorkspaceServiceParticipants().stream().filter(MavenWorkspaceService.class::isInstance).findAny().get();
		assertNotNull(workspaceService);
		
		URI folderUri = getClass().getResource("/modules").toURI();
		WorkspaceFolder wsFolder = new WorkspaceFolder(folderUri.toString());

		// Test add folders
		workspaceService.didChangeWorkspaceFolders(
				new DidChangeWorkspaceFoldersParams(
						new WorkspaceFoldersChangeEvent (
								Arrays.asList(new WorkspaceFolder[] {wsFolder}), 
								Arrays.asList(new WorkspaceFolder[0]))));
		
		// As ModuleA (module-a-pom.xml) is a parent of ModuleC (module-c-pom.xml) and
		// ModuleA folder was added it should be resolved and, as such, no diagnostics 
		// error messages should appear.
		//
		DOMDocument doc = createDOMDocument("/modules/dependent/module-c-pom.xml", languageService);
		List<Diagnostic> diagnostics = languageService.doDiagnostics(doc, new XMLValidationSettings(), () -> {});
		assertFalse(diagnostics.stream().anyMatch(diag -> (diag.getMessage().contains("ModuleA"))));
	}
	
//	@Test
	public void testRemoveFolders_didChangeWorkspaceFolders() throws Exception {
		XMLLanguageService languageService = new XMLLanguageService();
		IWorkspaceServiceParticipant workspaceService = languageService.getWorkspaceServiceParticipants().stream().filter(MavenWorkspaceService.class::isInstance).findAny().get();
		assertNotNull(workspaceService);
		
		URI folderUri = getClass().getResource("/modules").toURI();
		WorkspaceFolder wsFolder = new WorkspaceFolder(folderUri.toString());

		// Add folders
		workspaceService.didChangeWorkspaceFolders(
				new DidChangeWorkspaceFoldersParams(
						new WorkspaceFoldersChangeEvent (
								Arrays.asList(new WorkspaceFolder[] {wsFolder}), 
								Arrays.asList(new WorkspaceFolder[0]))));

		// Test remove folders 
		workspaceService.didChangeWorkspaceFolders(
				new DidChangeWorkspaceFoldersParams(
						new WorkspaceFoldersChangeEvent (
								Arrays.asList(new WorkspaceFolder[0]),
								Arrays.asList(new WorkspaceFolder[] {wsFolder}))));
		
		// As ModuleA (module-a-pom.xml) is a parent of ModuleC (module-c-pom.xml) and
		// ModuleA folder was removed it should not be resolved and, as such, a diagnostics 
		// error message should appear.
		//
		DOMDocument doc = createDOMDocument("/modules/dependent/module-c-pom.xml", languageService);
		List<Diagnostic> diagnostics = languageService.doDiagnostics(doc, new XMLValidationSettings(), () -> {});
		assertTrue(diagnostics.stream().anyMatch(diag -> (diag.getMessage().contains("ModuleA"))));
	}
	
	private DOMDocument getDocument(String resource) throws URISyntaxException, IOException {
		URI uri = getClass().getResource(resource).toURI();
		File pomFile = new File(uri);
		String content = FileUtils.readFileToString(pomFile, "UTF-8");
		DOMDocument doc = new DOMDocument(new TextDocument(content, uri.toString()), null);
		return doc;
	}
}
