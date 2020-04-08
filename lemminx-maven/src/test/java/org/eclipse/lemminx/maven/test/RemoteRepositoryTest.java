/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.completionContains;
import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createTextDocumentItem;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteRepositoryTest {

	private ClientServerConnection connection;

	@Before
	public  void setUp() throws IOException {
		connection = new ClientServerConnection();
	}

	@After
	public  void tearDown() throws InterruptedException, ExecutionException {
		connection.stop();
	}

	@Test(timeout=60000)
	public void testRemoteGroupIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-groupId-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		final Position pos = new Position(11, 20);
		String desiredCompletion = "signaturacaib";
		List<CompletionItem> items = Collections.emptyList();
		do {
			items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion));
		assertTrue(completionContains(items, desiredCompletion));
	}
	
	@Test(timeout=60000)
	public void testRemoteArtifactIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-artifactId-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		final Position pos = new Position(12, 15);
		String desiredCompletion = "signaturacaib.core";
		List<CompletionItem> items = Collections.emptyList();
		do {
			 items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion) && items.size() < 30);
		assertTrue(completionContains(items, desiredCompletion));
	}
	
	@Test(timeout=60000)
	public void testRemoteVersionCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-version-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		Position pos = new Position(13, 13);
		String desiredCompletion = "3.3.0";
		List<CompletionItem> items = Collections.emptyList();
		do {
			 items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion));
		assertTrue(completionContains(items, desiredCompletion));
	}

	@Test(timeout=60000)
 	public void testRemoteArtifactHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
 		TextDocumentItem textDocumentItem = MavenLemminxTestsUtils.createTextDocumentItem("/pom-remote-artifact-hover.xml");
 		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
 		connection.languageServer.getTextDocumentService().didOpen(params);
 		Hover hover;
 		TextDocumentPositionParams pos = new TextDocumentPositionParams( new TextDocumentIdentifier(textDocumentItem.getUri()), new Position(14, 6));
 		do {
 	 		hover = connection.languageServer.getTextDocumentService().hover(pos).get();
 		} while ((((MarkupContent) hover.getContents().getRight()).getValue().contains("Updating")));
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("Converts between version 3.0.0 and version 4.0.0 models.")));

	}
	
	@Test(timeout=60000)
	public void testRemotePluginGroupIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-plugin-groupId-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		final Position pos = new Position(11, 14);
		String desiredCompletion = "org.codehaus.mojo";
		List<CompletionItem> items = Collections.emptyList();
		do {
			items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion));
		assertTrue(completionContains(items, desiredCompletion));
	}
	
	@Test(timeout=60000)
	public void testRemotePluginArtifactIdCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-plugin-artifactId-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		final Position pos = new Position(12, 15);
		String desiredCompletion = "deb-maven-plugin";
		List<CompletionItem> items = Collections.emptyList();
		do {
			 items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion) && items.size() < 30);
		assertTrue(completionContains(items, desiredCompletion));
	}
	
	@Test(timeout=60000)
	public void testRemotePluginVersionCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-remote-plugin-version-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		Position pos = new Position(13, 12);
		String desiredCompletion = "1.0-beta-1";
		List<CompletionItem> items = Collections.emptyList();
		do {
			 items = connection.languageServer.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()), pos)).get().getRight().getItems();
		} while (!completionContains(items, desiredCompletion));
		assertTrue(completionContains(items, desiredCompletion));
	}

}
