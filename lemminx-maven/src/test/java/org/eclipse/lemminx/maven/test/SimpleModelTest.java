/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createDOMDocument;
import static org.eclipse.lemminx.maven.test.MavenLemminxTestsUtils.createTextDocumentItem;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.maven.MavenPlugin;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.settings.XMLHoverSettings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SimpleModelTest {

	@BeforeClass
	public static void skipCentral() {
		System.err.println("local repo=" + MavenPlugin.LOCAL_REPOSITORY);
		RemoteRepositoryIndexSearcher.disableCentralIndex = true;
	}
	
	private ClientServerConnection connection;
	private XMLLanguageService languageService;

	@Before
	public void setUp() throws IOException {
		connection = new ClientServerConnection();
		languageService = new XMLLanguageService();
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		connection.stop();
		languageService = null;
	}

	@Test(timeout=10000)
	public void testScopeCompletion() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		CompletionList completion = languageService.doComplete(createDOMDocument("/pom-with-module-error.xml", languageService),
				new Position(12, 10), new SharedSettings());
		assertTrue(completion.getItems().stream().map(CompletionItem::getLabel).anyMatch("runtime"::equals));
	}


	@Test(timeout=10000)
	public void testPropertyCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		CompletionList completion = languageService.doComplete(createDOMDocument("/pom-with-properties.xml", languageService),
				new Position(11, 15), new SharedSettings());
		assertTrue(completion.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("myProperty")));
		assertTrue(completion.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("project.build.directory")));
	}


	@Test(timeout=10000)
	public void testParentPropertyCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		assertTrue(languageService.doComplete(createDOMDocument("/pom-with-properties-in-parent.xml", languageService), new Position(15, 20), new SharedSettings())
				.getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.contains("myProperty")));
	}

	@Test(timeout=15000)
	public void testLocalParentGAVCompletion()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException, TimeoutException {
		// * if relativePath is set and resolve to a pom or a folder containing a pom, GAV must be available for completion
		assertTrue(languageService.doComplete(createDOMDocument("/hierarchy/child/grandchild/pom.xml", languageService),
				new Position(4, 2), new SharedSettings()).getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.startsWith("test-parent")));
		// * if relativePath is not set and parent contains a pom, complete GAV from parent
		assertTrue(languageService.doComplete(createDOMDocument("/hierarchy/child/pom.xml", languageService),
				new Position(4, 2), new SharedSettings()).getItems().stream().map(CompletionItem::getLabel).anyMatch(label -> label.startsWith("test-parent")));
		// TODO:
		// * if relativePath is not set, complete with local repo artifacts with "pom" packaging
		// * if relativePath is not set, complete with remote repo artifacts with "pom" packaging
	}

	@Test(timeout=15000)
	public void testMissingArtifactIdError()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		TextDocumentItem textDocumentItem = createTextDocumentItem("/pom-without-artifactId.xml");
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
		connection.languageServer.getTextDocumentService().didOpen(params);
		assertTrue(connection.waitForDiagnostics(diagnostics -> diagnostics.stream().map(Diagnostic::getMessage)
				.anyMatch(message -> message.contains("artifactId")), 15000));
		DidChangeTextDocumentParams didChange = new DidChangeTextDocumentParams();
		didChange.setTextDocument(new VersionedTextDocumentIdentifier(textDocumentItem.getUri(), 2));
		didChange.setContentChanges(Collections.singletonList(new TextDocumentContentChangeEvent(
				new Range(new Position(5, 28), new Position(5, 28)), 0, "<artifactId>a</artifactId>")));
		connection.languageServer.getTextDocumentService().didChange(didChange);
		assertTrue(connection.waitForDiagnostics(Collection<Diagnostic>::isEmpty, 20000));
	}

	@Test(timeout=15000)
	public void testCompleteScope() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/scope.xml", languageService);
		assertTrue(languageService.doComplete(document, new Position(0, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("compile"::equals));
		assertTrue(languageService.doComplete(document, new Position(1, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("compile</scope>"::equals));
	}

	@Test(timeout=15000)
	public void testCompletePhase() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/phase.xml", languageService);
		assertTrue(languageService.doComplete(document, new Position(0, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("generate-resources"::equals));
		assertTrue(languageService.doComplete(document, new Position(1, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("generate-resources</phase>"::equals));
	}
	
	@Test(timeout=15000)
 	public void testPropertyHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-properties.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(15, 20), new XMLHoverSettings());
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("$")));

 		hover = languageService.doHover(document, new Position(15, 35), new XMLHoverSettings());
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("0.0.1-SNAPSHOT")));

 		hover = languageService.doHover(document, new Position(15, 13), new XMLHoverSettings());
 		assertNull(hover);
	}

}
