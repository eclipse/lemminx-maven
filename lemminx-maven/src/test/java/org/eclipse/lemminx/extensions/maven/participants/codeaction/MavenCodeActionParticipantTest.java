/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import static org.eclipse.lemminx.XMLAssert.assertDiagnostics;
import static org.eclipse.lemminx.XMLAssert.ca;
import static org.eclipse.lemminx.XMLAssert.d;
import static org.eclipse.lemminx.XMLAssert.teOp;
import static org.eclipse.lemminx.XMLAssert.testCodeActionsFor;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lemminx.XMLAssert.SettingsSaveContext;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.participants.XMLSyntaxErrorCode;
import org.eclipse.lemminx.extensions.contentmodel.settings.ContentModelSettings;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationRootSettings;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eclipse.lemminx.extensions.maven.MavenSyntaxErrorCode;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenCodeActionParticipantTest {
	private XMLLanguageService xmlLanguageService = new XMLLanguageService();
	private SharedSettings sharedSettings = new SharedSettings();

	// EDITOR_HINT_MISSING_SCHEMA == NoGrammarConstraints
	@Test
	public void testCodeActionsForNoGrammarConstraints() throws Exception {
		DOMDocument xmlDocument = createDOMDocument("/codeactions-test/pom-no-grammar-constraints.xml", xmlLanguageService);
		
		// Test diagnostic and code action for absent grammar constraints
		Diagnostic expectedDiagnostic = d(1, 1, 1, 8, 
				XMLSyntaxErrorCode.NoGrammarConstraints,
				"No grammar constraints (DTD or XML Schema).");
		CodeAction expectedCodeAction = ca(expectedDiagnostic, teOp("pom.xml", 1, 8, 1, 8, MavenNoGrammarConstraintsCodeAction.XSI_VALUE));
		testCodeAction(xmlDocument,false, expectedDiagnostic, expectedCodeAction);
	}
	
//	The following org.eclipse.m2e.core.internal.IMavenConstants hint quickfixes are to be moved to LemMinX-Maven:
//
//	EDITOR_HINT_PARENT_VERSION (from m2e.ui)
	@Test
	public void testCodeActionsForParentVersionRemoval() throws Exception {
		DOMDocument xmlDocument = createDOMDocument("/codeactions-test/pom-duplication-of-version.xml", xmlLanguageService);

		// Test diagnostic and code action for duplicated parent version
		Diagnostic expectedDiagnostic = d(11, 11, 11, 16, 
				MavenSyntaxErrorCode.DuplicationOfParentVersion,
				"Version is duplicate of parent version");
		CodeAction expectedCodeAction = ca(expectedDiagnostic, teOp("pom.xml", 11, 2, 11, 26, ""));
		testCodeAction(xmlDocument,true, expectedDiagnostic, expectedCodeAction);
	}

//	EDITOR_HINT_PARENT_GROUP_ID (from m2e.ui)
	@Test
	public void testCodeActionsForParentGroupIdRemoval() throws Exception {
		DOMDocument xmlDocument = createDOMDocument("/codeactions-test/pom-duplication-of-groupid.xml", xmlLanguageService);

		// Test diagnostic and code action for duplicated parent group ID
		Diagnostic expectedDiagnostic = d(11, 11, 11, 27, 
				MavenSyntaxErrorCode.DuplicationOfParentGroupId,
				"GroupId is duplicate of parent groupId");
		CodeAction expectedCodeAction = ca(expectedDiagnostic, teOp("pom.xml", 11, 2, 11, 37, ""));
		testCodeAction(xmlDocument,true, expectedDiagnostic, expectedCodeAction);
	}
	
//	@TODO: EDITOR_HINT_MANAGED_DEPENDENCY_OVERRIDE (from m2e.ui)
//	@TODO: EDITOR_HINT_MANAGED_PLUGIN_OVERRIDE (from m2e.ui)
//	@TODO: EDITOR_HINT_CONFLICTING_LIFECYCLEMAPPING (from m2e.core)
//	@TODO: EDITOR_HINT_NOT_COVERED_MOJO_EXECUTION (from m2e.core)
//	@TODO: EDITOR_HINT_MISSING_CONFIGURATOR (from m2e.core)
//	@TODO: EDITOR_HINT_IMPLICIT_LIFECYCLEMAPPINGEDITOR_HINT_IMPLICIT_LIFECYCLEMAPPING
	
	
	private void testCodeAction(DOMDocument xmlDocument, boolean ignoreNoGrammar, Diagnostic expectedDiagnostic, CodeAction expectedCodeAction) throws BadLocationException {
		// Test for expected diagnostics is returned
		ContentModelSettings settings = createContentModelSettings(ignoreNoGrammar);
		xmlLanguageService.setDocumentProvider((uri) -> xmlDocument);
		xmlLanguageService.doSave(new SettingsSaveContext(settings));

		List<Diagnostic> actual = xmlLanguageService.doDiagnostics(xmlDocument, settings.getValidation(),
				Collections.emptyMap(), () -> {
				}).stream().filter(a -> a.getCode()!= null & a.getCode().getLeft().equals(expectedDiagnostic.getCode().getLeft()))
				.collect(Collectors.toList());
//		if (expected == null) {
//			assertTrue(actual.isEmpty());
//			return;
//		}
		assertDiagnostics(actual, Arrays.asList(expectedDiagnostic), true);		

		// Test for expected code action is returned
		testCodeActionsFor(xmlDocument.getText(), expectedCodeAction.getDiagnostics().get(0), (String) null, "pom.xml", 
				sharedSettings, xmlLanguageService, -1, expectedCodeAction);
	}
	
	
	private static ContentModelSettings  createContentModelSettings(boolean ignoreNoGrammar) {
		ContentModelSettings settings = new ContentModelSettings();
		settings.setUseCache(false);
		XMLValidationRootSettings problems = new XMLValidationRootSettings();
		if (ignoreNoGrammar) {
			problems.setNoGrammar("ignore");
		}
		settings.setValidation(problems);
		return settings;
	}
}