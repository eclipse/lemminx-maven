/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createTextDocumentItem;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PluginTest {

	private ClientServerConnection connection;

	@Before
	public void setUp() throws IOException {
		connection = new ClientServerConnection();
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		connection.stop();
	}
	
	// Completion related tests

	@Test(timeout=15000)
	public void testCompleteGoal() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-complete-plugin-goal.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		List<CompletionItem> items = Collections.emptyList();
		do {
			Either<List<CompletionItem>, CompletionList> completion = connection.languageServer.getTextDocumentService()
					.completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()),
							new Position(18, 19)))
					.get();
			items = completion.getRight().getItems();
		} while (items.stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.noneMatch("copy-dependencies"::equals));
	}

	@Test(timeout=15000)
	public void testCompleteConfigurationParameters() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-complete-plugin-goal.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		List<CompletionItem> items = Collections.emptyList();
		do {
			Either<List<CompletionItem>, CompletionList> completion = connection.languageServer.getTextDocumentService()
					.completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()),
							new Position(23, 7)))
					.get();
			items = completion.getRight().getItems();
		} while (items.stream().map(CompletionItem::getLabel).noneMatch("appendOutput"::equals));
	}

	@Test(timeout=15000)
	public void testCompleteConfigurationParametersInTag() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-plugin-config-tag.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		List<CompletionItem> items = Collections.emptyList();
		do {
			Either<List<CompletionItem>, CompletionList> completion = connection.languageServer.getTextDocumentService()
					.completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()),
							new Position(20, 9)))
					.get();
			items = completion.getRight().getItems();
		} while (items.stream().map(CompletionItem::getLabel).noneMatch("appendOutput"::equals));
	}
	
	// Hover related tests
	
	@Test(timeout=15000)
 	public void testPluginConfigurationHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
 		TextDocumentItem textDocumentItem = MavenLemminxTestsUtils.createTextDocumentItem("/pom-plugin-configuration-hover.xml");
 		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
 		connection.languageServer.getTextDocumentService().didOpen(params);
 		TextDocumentPositionParams pos = new TextDocumentPositionParams( new TextDocumentIdentifier(textDocumentItem.getUri()), new Position(15, 5));
 		Hover hover = connection.languageServer.getTextDocumentService().hover(pos).get();
 		assertTrue(((MarkupContent) hover.getContents().getRight()).getValue().contains("The -source argument for the Java compiler."));
	}
	
	@Test(timeout=15000)
 	public void testPluginGoalHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
 		TextDocumentItem textDocumentItem = MavenLemminxTestsUtils.createTextDocumentItem("/pom-plugin-goal-hover.xml");
 		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
 		connection.languageServer.getTextDocumentService().didOpen(params);
 		TextDocumentPositionParams pos = new TextDocumentPositionParams( new TextDocumentIdentifier(textDocumentItem.getUri()), new Position(18, 18));
 		Hover hover = connection.languageServer.getTextDocumentService().hover(pos).get();
 		assertTrue(((MarkupContent) hover.getContents().getRight()).getValue().contains("determines the duplicate declared dependencies."));
	}
	
	@Test(timeout=15000)
 	public void testPluginArtifactHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException, TimeoutException {
 		TextDocumentItem textDocumentItem = MavenLemminxTestsUtils.createTextDocumentItem("/pom-plugin-artifact-hover.xml");
 		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
 		connection.languageServer.getTextDocumentService().didOpen(params);
 		Hover hover;
 		TextDocumentPositionParams pos = new TextDocumentPositionParams( new TextDocumentIdentifier(textDocumentItem.getUri()), new Position(14, 6));
 		do {
 	 		hover = connection.languageServer.getTextDocumentService().hover(pos).get();
 		} while ((((MarkupContent) hover.getContents().getRight()).getValue().contains("Updating")));
		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue()
				.contains("Provides a maven plugin that supports creating an OSGi bundle")));
	}
}
