/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.hover;

import static org.eclipse.lemminx.XMLAssert.assertHover;
import static org.eclipse.lemminx.XMLAssert.r;
import static org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils.createDOMDocument;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.settings.ContentModelSettings;
import org.eclipse.lemminx.extensions.maven.MavenLanguageService;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.extensions.maven.utils.MarkdownUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class MavenPropertyHoverTest {
	private XMLLanguageService languageService;

	private static String NL = MarkdownUtils.getLineBreak(true);

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
	public void testHoverForVariablePropertyDefinedInSameDocument()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException, BadLocationException {
		DOMDocument document = createDOMDocument("/pom-with-properties-in-parent-for-definition.xml", languageService);
		String text = document.getText();
		int offset = text.indexOf("${anotherProperty}") + "${".length();
		text = text.substring(0, offset) + '|' + text.substring(offset);
		String expectedHoverText = "**Property:** anotherProperty" + NL //
				+ "**Value:** $" + NL //
				+ "**The property is defined in [org.test:child:0.0.1-SNAPSHOT](%s#L16,3-L16,39)**";

		ContentModelSettings settings = new ContentModelSettings();
		settings.setUseCache(false);
		assertHover(languageService, text, null, document.getDocumentURI(), 
				String.format(expectedHoverText, document.getDocumentURI()),
				r(28, 15,28, 30), settings);
	}

	@Test
	public void testHoverForVariablePropertyDefinedInParentDocument()
			throws IOException, InterruptedException, ExecutionException, URISyntaxException, BadLocationException {
		DOMDocument document = createDOMDocument("/pom-with-properties-in-parent-for-definition.xml", languageService);
		String text = document.getText();
		int offset = text.indexOf("${myProperty}") + "${".length();
		text = text.substring(0, offset) + '|' + text.substring(offset);
		String expectedHoverText = "**Property:** myProperty" + NL //
				+ "**Value:** $" + NL //
				+ "**The property is defined in [org.test:test:0.0.1-SNAPSHOT](%s#L12,3-L12,29)**";
		
		File documentFile = new File(ParticipantUtils.normalizedUri(document.getDocumentURI()).getPath());
		File expectedFile = new File(documentFile.getParent(), "pom-with-properties-for-definition.xml");
		ContentModelSettings settings = new ContentModelSettings();
		settings.setUseCache(false);
		assertHover(languageService, text, null, document.getDocumentURI(), 
				String.format(expectedHoverText, expectedFile.toURI().toString()), 
				r(23, 15, 23, 25), settings);
	}
}
