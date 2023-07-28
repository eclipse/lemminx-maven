/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.hover;

import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.CancelCheckerCallCounter;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.PhaseCancelChecker;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenHoverCancellationTest {
	private XMLLanguageService languageService;

	@BeforeEach
	public void setUp() throws IOException {
		languageService = new MavenLanguageService();
	}

	@AfterEach
	public void tearDown() throws InterruptedException, ExecutionException {
		languageService.dispose();
		languageService = null;
	}
	
	@Test
	public void testHoverCancellationOnProperty()
			throws BadLocationException, IOException, URISyntaxException {
		DOMDocument document = createDOMDocument("/pom-with-properties-in-parent-for-definition.xml", languageService);
		String text = document.getText();
		// Will test an offset somewhere in the middle of the property name
		int offset = text.indexOf("${anotherProperty}")
				+ "${".length() + "anotherProperty".length() / 2; 
		TextDocument textDocument = document.getTextDocument();
		Position offsetPosition = textDocument.positionAt(offset);
		
		// Should return some hover
		CancelCheckerCallCounter cancelCheckerCallCounter = new CancelCheckerCallCounter();
		Hover hover = languageService.doHover(document, offsetPosition, new SharedSettings(), cancelCheckerCallCounter);
		assertNotNull(hover, "Hover Participant didn't return any Hover");
		int numberOfChecks = cancelCheckerCallCounter.getCounterValue();
		assertTrue( (numberOfChecks > 0), "No cancellation checks performed during the processing");

		// Should not return any hover
		for (int i = 1; i <= numberOfChecks; i++) {
			PhaseCancelChecker phaseChacker = new PhaseCancelChecker(i);
			hover = languageService.doHover(document, offsetPosition, new SharedSettings(), phaseChacker);
			assertNull(hover, "Hover Participant is not canceled at phase " + i);
		}	
	}

    @Test
    void testHoverCancellationOnDependency() throws BadLocationException, IOException, URISyntaxException  {
        DOMDocument document = createDOMDocument("/hierarchy2/child/grandchild/pom.xml", languageService);
		String text = document.getText();
		// Will test an offset somewhere in the middle of the dependency artifact ID
		int offset = text.indexOf("<artifactId>slf4j-api")
				+ "<artifactId>".length() + "slf4j-api".length() / 2; 
		TextDocument textDocument = document.getTextDocument();
		Position offsetPosition = textDocument.positionAt(offset);

		// Should return some hover
		CancelCheckerCallCounter cancelCheckerCallCounter = new CancelCheckerCallCounter();
		Hover hover = languageService.doHover(document, offsetPosition, new SharedSettings(), cancelCheckerCallCounter);
		assertNotNull(hover, "Hover Participant didn't return any Hover");
		int numberOfChecks = cancelCheckerCallCounter.getCounterValue();
		assertTrue( (numberOfChecks > 0), "No cancellation checks performed during the processing");

		// Should not return any hover
		for (int i = 1; i <= numberOfChecks; i++) {
			PhaseCancelChecker phaseChacker = new PhaseCancelChecker(i);
			hover = languageService.doHover(document, offsetPosition, new SharedSettings(), phaseChacker);
			assertNull(hover, "Hover Participant is not canceled at phase " + i);
		}	
    }
}
