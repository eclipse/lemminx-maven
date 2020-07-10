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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.maven.DOMUtils;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class SimpleModelTest {

	public @Rule TestRule noCentralIndexRule = new NoMavenCentralIndexTestRule();
	
	private XMLLanguageService languageService;

	@Before
	public void setUp() throws IOException {
		languageService = new XMLLanguageService();
	}

	@After
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
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

	@Test
	public void testMissingArtifactIdError()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-without-artifactId.xml", languageService);
		assertTrue(languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()).stream().map(Diagnostic::getMessage)
				.anyMatch(message -> message.contains("artifactId")));
		// simulate an edit
		TextDocument textDocument = document.getTextDocument();
		textDocument.setText(textDocument.getText().replace("</project>", "<artifactId>a</artifactId></project>"));
		textDocument.setVersion(textDocument.getVersion() + 1);
		document = DOMParser.getInstance().parse(textDocument, languageService.getResolverExtensionManager());
		assertEquals(Collections.emptyList(), languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()));
	}
	
	@Test
	public void testSystemPathDiagnosticBug()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-environment-variable-property.xml", languageService);
		List<Diagnostic> diagnostics = languageService.doDiagnostics(document, () -> {
		}, new XMLValidationSettings());
		assertFalse(diagnostics.stream().anyMatch(diag -> diag.getMessage().contains("${env")));
	}

	@Test(timeout=15000)
	public void testCompleteScope() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-scope.xml", languageService);
		assertTrue(languageService.doComplete(document, new Position(0, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("compile"::equals));
		assertTrue(languageService.doComplete(document, new Position(1, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("compile</scope>"::equals));
	}

	@Test(timeout=15000)
	public void testCompletePhase() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-phase.xml", languageService);
		assertTrue(languageService.doComplete(document, new Position(0, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("generate-resources"::equals));
		assertTrue(languageService.doComplete(document, new Position(1, 7), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).map(TextEdit::getNewText)
				.anyMatch("generate-resources</phase>"::equals));
	}
	
	@Test
 	public void testPropertyHover() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-properties.xml", languageService);
		Hover hover = languageService.doHover(document, new Position(15, 20), new SharedSettings());
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("$")));

 		hover = languageService.doHover(document, new Position(15, 35), new SharedSettings());
 		assertTrue((((MarkupContent) hover.getContents().getRight()).getValue().contains("0.0.1-SNAPSHOT")));

 		hover = languageService.doHover(document, new Position(15, 13), new SharedSettings());
 		assertNull(hover);
	}
	
	@Test
 	public void testPropertyDefinitionSameDocument() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-properties-for-definition.xml", languageService);
		Position pos = new Position(14, 22);
		List<? extends LocationLink> definitionLinks = languageService.findDefinition(document, pos, () -> {
		});
		
		DOMDocument targetDocument = document;
		DOMNode propertyNode = DOMUtils.findNodesByLocalName(targetDocument, "myProperty").stream().filter(node -> node.getParentElement().getLocalName().equals("properties")).collect(Collectors.toList()).get(0);;
		Range expectedTargetRange = XMLPositionUtility.createRange(propertyNode);
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetUri().equals(targetDocument.getDocumentURI())));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetRange().equals(expectedTargetRange)));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetSelectionRange().equals(expectedTargetRange)));
	}
	
	@Test
 	public void testPropertyDefinitionSameDocumentBug() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-definition-wrong-tag-bug.xml", languageService);
		Position pos = new Position(22, 40);
		List<? extends LocationLink> definitionLinks = languageService.findDefinition(document, pos, () -> {
		});
		
		DOMDocument targetDocument = document;
		
		DOMNode propertyNode = DOMUtils.findNodesByLocalName(targetDocument, "lemminx.maven.indexDirectory").stream().filter(node -> node.getParentElement().getLocalName().equals("properties")).collect(Collectors.toList()).get(0);
		assertTrue(propertyNode.getParentNode().getLocalName().equals("properties"));
		Range expectedTargetRange = XMLPositionUtility.createRange(propertyNode);
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetUri().equals(targetDocument.getDocumentURI())));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetRange().equals(expectedTargetRange)));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetSelectionRange().equals(expectedTargetRange)));
	}
	
	@Test
 	public void testPropertyDefinitionParentDocument() throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-properties-in-parent-for-definition.xml", languageService);
		Position pos = new Position(23, 16);
		List<? extends LocationLink> definitionLinks = languageService.findDefinition(document, pos, () -> {
		});
		
		//Verify the LocationLink points to the right file and node
		DOMDocument targetDocument = createDOMDocument("/pom-with-properties-for-definition.xml", languageService);
		DOMNode propertyNode = DOMUtils.findNodesByLocalName(targetDocument, "myProperty").stream().filter(node -> node.getParentElement().getLocalName().equals("properties")).collect(Collectors.toList()).get(0);;
		Range expectedTargetRange = XMLPositionUtility.createRange(propertyNode);
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetUri().equals(targetDocument.getDocumentURI())));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetRange().equals(expectedTargetRange)));
		assertTrue(definitionLinks.stream().anyMatch(link -> link.getTargetSelectionRange().equals(expectedTargetRange)));
	}

	@Test
	public void testBOMDependency() throws IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-bom.xml", languageService);
		assertEquals(Collections.emptyList(), languageService.doDiagnostics(document, () -> {}, new XMLValidationSettings()));
	}

	@Test
	public void testCompleteSNAPSHOT() throws Exception {
		DOMDocument document = createDOMDocument("/pom-version.xml", languageService);
		Optional<TextEdit> edit = languageService.doComplete(document, new Position(0, 11), new SharedSettings()).getItems().stream().map(CompletionItem::getTextEdit).findFirst();
		assertTrue(edit.isPresent());
		assertEquals("-SNAPSHOT", edit.get().getNewText());
		assertEquals(new Range(new Position(0, 9), new Position(0, 11)), edit.get().getRange());
	}
	

	@Test
	public void testEnvironmentVariablePropertyHover()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException {
		String hoverContents = languageService.doHover(createDOMDocument("/pom-environment-variable-property.xml", languageService),
				new Position(16, 16), new SharedSettings()).getContents().getRight().getValue();
		
		System.out.println(hoverContents);

	}
}
