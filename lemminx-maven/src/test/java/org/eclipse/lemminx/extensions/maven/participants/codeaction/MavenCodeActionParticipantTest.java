/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import static org.eclipse.lemminx.XMLAssert.ca;
import static org.eclipse.lemminx.XMLAssert.d;
import static org.eclipse.lemminx.XMLAssert.teOp;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lemminx.XMLAssert;
import org.eclipse.lemminx.XMLAssert.SettingsSaveContext;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.extensions.contentmodel.participants.XMLSyntaxErrorCode;
import org.eclipse.lemminx.extensions.contentmodel.settings.ContentModelSettings;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationRootSettings;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.utils.StringUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.Gson;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenCodeActionParticipantTest {
	private XMLLanguageService xmlLanguageService = new XMLLanguageService();
	private SharedSettings sharedSettings = new SharedSettings();

	// EDITOR_HINT_MISSING_SCHEMA == NoGrammarConstraints
	@Test
	public void testCodeActionsForNoGrammarConstraints() throws Exception {
		String pom = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
			  <modelVersion>4.0.0</modelVersion>
			  <artifactId>just-a-pom</artifactId>
			  <groupId>com.datho7561</groupId>
			  <version>0.1.0</version>
			  <dependencies>
			    <dependency>
			      <groupId>com.fasterxml.jackson.core</groupId>
			      <artifactId>jackson-core</artifactId>
			      <version>2.11.3</version>
			    </dependency>
			    maven-core
			  </dependencies>
			</project>
				""";

		// Test for expected diagnostics is returned
		Diagnostic expected = d(1, 1, 1, 8, 
				XMLSyntaxErrorCode.NoGrammarConstraints,
				"No grammar constraints (DTD or XML Schema).");
		
		ContentModelSettings settings = new ContentModelSettings();
		settings.setUseCache(false);
		XMLValidationRootSettings problems = new XMLValidationRootSettings();
		// Do not ignore "No-grammar" errors
		settings.setValidation(problems);

		TextDocument document = new TextDocument(pom, "pom.xml");
		DOMDocument xmlDocument = DOMParser.getInstance().parse(document,
				xmlLanguageService.getResolverExtensionManager());
		xmlLanguageService.setDocumentProvider((uri) -> xmlDocument);

		xmlLanguageService.doSave(new SettingsSaveContext(settings));
		
		List<Diagnostic> actual = xmlLanguageService.doDiagnostics(xmlDocument, settings.getValidation(),
				Collections.emptyMap(), () -> {
				});

//		if (expected == null) {
//			assertTrue(actual.isEmpty());
//			return;
//		}
		assertDiagnostics(actual, Arrays.asList(expected), true);		

		// Test for expected code action is returned
		expected = d(1, 8, 1, 8, 
				XMLSyntaxErrorCode.NoGrammarConstraints,
				"No grammar constraints (DTD or XML Schema).");
		
		testCodeActionsFor(pom, expected, (String) null, "pom.xml", sharedSettings, xmlLanguageService, -1,
				ca(expected, teOp("pom.xml", 1, 8, 1, 8, MavenNoGrammarConstraintsCodeAction.XSI_VALUE)));
	}
	
//	The following org.eclipse.m2e.core.internal.IMavenConstants hint quickfixes are to be moved to LemMinX-Maven:
//
//		EDITOR_HINT_PARENT_VERSION
//		EDITOR_HINT_PARENT_GROUP_ID
//		EDITOR_HINT_MANAGED_DEPENDENCY_OVERRIDE
//		EDITOR_HINT_MANAGED_PLUGIN_OVERRIDE
//		EDITOR_HINT_CONFLICTING_LIFECYCLEMAPPING
//		EDITOR_HINT_NOT_COVERED_MOJO_EXECUTION
//		EDITOR_HINT_MISSING_CONFIGURATOR
//		EDITOR_HINT_IMPLICIT_LIFECYCLEMAPPING
	
	public static void assertDiagnostics(List<Diagnostic> actual, List<Diagnostic> expected, boolean filter) {
		List<Diagnostic> received = actual;
		final boolean filterMessage;
		if (expected != null && !expected.isEmpty() && !StringUtils.isEmpty(expected.get(0).getMessage())) {
			filterMessage = true;
		} else {
			filterMessage = false;
		}
		if (filter) {
			received = actual.stream().map(d -> {
				Diagnostic simpler = new Diagnostic(d.getRange(), "");
				if (d.getCode() != null && !StringUtils.isEmpty(d.getCode().getLeft())) {
					simpler.setCode(d.getCode());
				}
				if (filterMessage) {
					simpler.setMessage(d.getMessage());
				}
				return simpler;
			}).collect(Collectors.toList());
		}
		// Don't compare message of diagnosticRelatedInformation
		for (Diagnostic diagnostic : received) {
			List<DiagnosticRelatedInformation> diagnosticRelatedInformations = diagnostic.getRelatedInformation();
			if (diagnosticRelatedInformations != null) {
				for (DiagnosticRelatedInformation diagnosticRelatedInformation : diagnosticRelatedInformations) {
					diagnosticRelatedInformation.setMessage("");
				}
			}
		}
		assertIterableEquals(expected, received, "Unexpected diagnostics:\n" + actual);
	}
	
	// Copied from XMLAssert due to the linking problem
	private static final String FILE_URI = "test.xml";
	public static List<CodeAction> testCodeActionsFor(String xml, Diagnostic diagnostic, String catalogPath,
			String fileURI, SharedSettings sharedSettings, XMLLanguageService xmlLanguageService, int index,
			CodeAction... expected) throws BadLocationException {
		int offset = xml.indexOf('|');
		Range range = null;

		if (offset != -1) {
			xml = xml.substring(0, offset) + xml.substring(offset + 1);
		}
		TextDocument document = new TextDocument(xml.toString(), fileURI != null ? fileURI : FILE_URI);

		if (offset != -1) {
			Position position = document.positionAt(offset);
			range = new Range(position, position);
		} else {
			range = diagnostic.getRange();
		}

		if (xmlLanguageService == null) {
			xmlLanguageService = new XMLLanguageService();
		}

		ContentModelSettings cmSettings = new ContentModelSettings();
		cmSettings.setUseCache(false);
		if (catalogPath != null) {
			// Configure XML catalog for XML schema
			cmSettings.setCatalogs(new String[] { catalogPath });
		}
		xmlLanguageService.doSave(new SettingsSaveContext(cmSettings));

		CodeActionContext context = new CodeActionContext();
		context.setDiagnostics(Arrays.asList(diagnostic));
		DOMDocument xmlDoc = DOMParser.getInstance().parse(document, xmlLanguageService.getResolverExtensionManager());
		xmlLanguageService.setDocumentProvider((uri) -> xmlDoc);

		List<CodeAction> actual = xmlLanguageService.doCodeActions(context, range, xmlDoc, sharedSettings, () -> {
		});

		// Clone
		// Creating a gson object
		Gson gson = new Gson();
		// Converting the list into a json string
		String jsonstring = gson.toJson(actual);

		// Converting the json string
		// back into a list
		CodeAction[] cloned_list = gson.fromJson(jsonstring, CodeAction[].class);

		// Only test the code action at index if a proper index is given
		if (index >= 0) {
			XMLAssert.assertCodeActions(Arrays.asList(actual.get(index)), Arrays.asList(expected).get(index));
			return Arrays.asList(cloned_list);
		}
		XMLAssert.assertCodeActions(actual, expected);
		return Arrays.asList(cloned_list);
	}
}