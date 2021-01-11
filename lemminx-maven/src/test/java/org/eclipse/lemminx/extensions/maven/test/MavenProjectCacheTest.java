/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MavenProjectCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.io.Files;

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
			Files.write(content.getBytes(Charset.defaultCharset()), parentPomFile);
			doc.getTextDocument().setVersion(2); // Simulate some change
			MavenProject modifiedProject = cache.getLastSuccessfulMavenProject(doc);
			assertTrue(modifiedProject.getProperties().containsKey("modifiedProperty"), modifiedProject.getProperties().toString());
		} finally {
			Files.write(initialContent.getBytes(Charset.defaultCharset()), parentPomFile);
		}
	}

	private DOMDocument getDocument(String resource) throws URISyntaxException, IOException {
		URI uri = getClass().getResource(resource).toURI();
		File pomFile = new File(uri);
		String content = FileUtils.readFileToString(pomFile, "UTF-8");
		DOMDocument doc = new DOMDocument(new TextDocument(content, uri.toString()), null);
		return doc;
	}
}
