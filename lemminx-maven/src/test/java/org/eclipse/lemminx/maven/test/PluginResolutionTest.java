package org.eclipse.lemminx.maven.test;

import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.maven.MavenPlugin;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.XMLHoverSettings;
import org.eclipse.lsp4j.Position;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PluginResolutionTest {

	@Rule
	public NoMavenCentralIndexTestRule rule = new NoMavenCentralIndexTestRule();

	private XMLLanguageService languageService;

	private File mavenPluginApiDirectory;

	@Before
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
		languageService.initializeIfNeeded();
		File mavenRepo = MavenPlugin.getRepositorySystemSession().getLocalRepository().getBasedir();
		System.out.println("Maven repo: " + mavenRepo);
		mavenPluginApiDirectory = new File(mavenRepo, "/org/apache/maven/maven-plugin-api/3.0");
		System.out.println(mavenPluginApiDirectory);
		if (mavenPluginApiDirectory.exists()) {
			System.out.println("[NOTICE]: Deleting maven-plugin-api:3.0 files...");
			// Iteratively delete all of the directory's contents
			for (File file : mavenPluginApiDirectory.listFiles()) {
				System.out.println("[NOTICE]: Deleting " + file);
				file.delete();
			}
			// Delete the now-empty directory
			mavenPluginApiDirectory.delete();
		} else {
			System.out.println("[NOTICE]: maven-plugin-api:3.0 was already non-existent");
		}
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
		// TODO: Restore the mavenPluginApiDirectory?
	}

	@Test
	public void testPluginConfigurationHover()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertFalse(mavenPluginApiDirectory.exists());
		
		// <compilerArguments> hover
		String hoverContents = languageService
				.doHover(createDOMDocument("/pom-plugin-nested-configuration-hover.xml", languageService),
						new Position(15, 8), new XMLHoverSettings())
				.getContents().getRight().getValue();
		
		assertTrue(hoverContents.contains("**Type:** List&lt;String&gt;"));
		assertTrue(hoverContents.contains("Sets the arguments to be passed to the compiler"));

	}



}
