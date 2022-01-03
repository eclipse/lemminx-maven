/*******************************************************************************
 * Copyright (c) 2020-2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import java.util.List;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.uriresolver.URIResolverExtensionManager;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
public class MatchTest {

	@Test
	public void testDoesntFailNonHierarchicalURIs() {
		XMLLanguageService languageService = new XMLLanguageService();
		DOMDocument doc = DOMParser.getInstance().parse("blah", "untitled:Untitled-1",
				new URIResolverExtensionManager());
		List<Diagnostic> doDiagnostics = languageService.doDiagnostics(doc, new XMLValidationSettings(), () -> {
		});
	}

}
