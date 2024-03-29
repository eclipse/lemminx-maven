/*******************************************************************************
 * Copyright (c) 2019-2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

class SubModuleValidator {
	Model model;
	MavenXpp3Reader mavenreader = new MavenXpp3Reader();
	private CancelChecker cancelChecker;
	
	public SubModuleValidator(CancelChecker cancelChecker) {
		this.cancelChecker = cancelChecker;
	}
	
	// TODO: This class shouldn't be instantiating the maven model, but be receiving
	// it from a context class instead
	public void setPomFile(File pomFile) throws FileNotFoundException, IOException, XmlPullParserException, CancellationException {
		cancelChecker.checkCanceled();
		model = mavenreader.read(new FileReader(pomFile));
		cancelChecker.checkCanceled();
	}

	public Optional<List<Diagnostic>> validateSubModuleExistence(DiagnosticRequest diagnosticRequest) throws CancellationException {
		cancelChecker.checkCanceled();
		DOMNode node = diagnosticRequest.getNode();
		String tagContent = null;
		if (node.hasChildNodes()) {
			tagContent = node.getChild(0).getNodeValue(); // tagContent is the module to validate eg.
															// <module>tagContent</module>
		}
		if (node.hasChildNodes() && !model.getModules().contains(tagContent)) {
			cancelChecker.checkCanceled();
			return Optional.of(Collections.singletonList(diagnosticRequest.createDiagnostic(
					String.format("Module '%s' does not exist", tagContent), DiagnosticSeverity.Error)));
		}
		cancelChecker.checkCanceled();
		return Optional.empty();
	}

}
