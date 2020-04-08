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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalRepoTests {

	private ClientServerConnection connection;

	@Before
	public void setUp() throws IOException {
		connection = new ClientServerConnection();
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		connection.stop();
	}

	@Test(timeout=90000)
	public void testCompleteDependency()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-with-dependency.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		Either<List<CompletionItem>, CompletionList> completion = connection.languageServer.getTextDocumentService()
				.completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()),
						new Position(11, 7)))
				.get();
		List<CompletionItem> items = completion.getRight().getItems();
		Optional<String> mavenCoreCompletionItem = items.stream().map(CompletionItem::getLabel)
				.filter(label -> label.contains("org.apache.maven:maven-core")).findAny();
		assertTrue(mavenCoreCompletionItem.isPresent());
	}

	@Test(timeout=90000)
	public void testCompleteLocalGroupdId()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-local-groupId-complete.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		Either<List<CompletionItem>, CompletionList> completion = connection.languageServer.getTextDocumentService()
				.completion(new CompletionParams(new TextDocumentIdentifier(textDocumentItem.getUri()),
						new Position(11, 12)))
				.get();
		List<CompletionItem> items = completion.getRight().getItems();
		Optional<String> mavenGroupCompletionItem = items.stream().map(CompletionItem::getLabel)
				.filter(label -> label.contains("org.apache.maven")).findAny();
		assertTrue(mavenGroupCompletionItem.isPresent());
	}
}
