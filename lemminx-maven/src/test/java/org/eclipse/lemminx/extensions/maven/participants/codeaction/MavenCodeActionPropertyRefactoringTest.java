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
import static org.eclipse.lemminx.XMLAssert.te;
import static org.eclipse.lemminx.XMLAssert.teOp;
import static org.eclipse.lemminx.XMLAssert.testCodeActionsFor;
import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenCodeActionPropertyRefactoringTest {
	private XMLLanguageService xmlLanguageService = new XMLLanguageService();
	private SharedSettings sharedSettings = new SharedSettings();
	
	@Test
	public void testCodeActionsInlineSinglePropertyUse() throws Exception {
		DOMDocument xmlDocument = createDOMDocument("/property-refactoring/pom-with-property.xml", xmlLanguageService);
		String text = xmlDocument.getText();
		TextDocument document = xmlDocument.getTextDocument();
		
		// ${myPropertyGroupId}
		String propertyUse = "${myPropertyGroupId}";
		int propertyUseStart = text.indexOf(propertyUse);
		int propertyUseEnd = propertyUseStart + propertyUse.length();

		Position positionStart = document.positionAt(propertyUseStart);
		Position positionEnd = document.positionAt(propertyUseEnd);
		
		CodeAction expectedCodeAction = ca(null, 
				teOp(xmlDocument.getDocumentURI(), positionStart.getLine(), positionStart.getCharacter(), 
						positionEnd.getLine(), positionEnd.getCharacter(), "my-property-group-value"));
		// Test for expected code actions returned
		testCodeActionsFor(xmlDocument.getText(), null, new Range(positionStart, positionEnd), 
				(String) null, xmlDocument.getDocumentURI(),  sharedSettings, xmlLanguageService, -1, 
				expectedCodeAction);
	}
	
	@Test
	public void testCodeActionsInlineMultiplePropertyUses() throws Exception {
		DOMDocument xmlDocument = createDOMDocument("/property-refactoring/pom-with-multiple-property-uses.xml", xmlLanguageService);
		String text = xmlDocument.getText();
		TextDocument document = xmlDocument.getTextDocument();
		
		// ${myPropertyGroupId}
		String propertyUse = "${myPropertyGroupId}";
		List<Range> ranges = new ArrayList<>();

		int index = 0;
		for (index = text.indexOf(propertyUse); index != -1; index = text.indexOf(propertyUse, index + propertyUse.length())) {
			try {
				int propertyUseStart = index;
				int propertyUseEnd = propertyUseStart + propertyUse.length();

				Position positionStart = document.positionAt(propertyUseStart);
				Position positionEnd = document.positionAt(propertyUseEnd);
				ranges.add(new Range(positionStart, positionEnd));
			} catch (BadLocationException e) {
				fail("Cannot find all property uses in test data", e);
			}
		}
		assertTrue(ranges.size() > 1, "There should be more than one prperty use");
		
		// Inline for the selected single property use 
		CodeAction expectedCodeAction_1 = ca(null, 
				teOp(xmlDocument.getDocumentURI(), 
						ranges.get(0).getStart().getLine(), ranges.get(0).getStart().getCharacter(), 
						ranges.get(0).getEnd().getLine(), ranges.get(0).getEnd().getCharacter(), 
						"my-property-group-value"));
		
		// Inline for all the property uses in pom.xml 
		List<TextEdit> textEdits = ranges.stream().map(range -> 
			te(range.getStart().getLine(), range.getStart().getCharacter(), 
				range.getEnd().getLine(), range.getEnd().getCharacter(), 
				"my-property-group-value")).collect(Collectors.toList()) ;
		assertTrue(textEdits.size() == ranges.size(), "The TextEdits size should be equl to the size of ranges");
		CodeAction expectedCodeAction_2 = ca(null,  teOp(xmlDocument.getDocumentURI(), textEdits.toArray(new TextEdit[textEdits.size()])));

		// Test for expected code actions returned
		testCodeActionsFor(xmlDocument.getText(), null, ranges.get(0), 
				(String) null, xmlDocument.getDocumentURI(),  sharedSettings, xmlLanguageService, -1, 
				expectedCodeAction_1, expectedCodeAction_2);
	}
}
