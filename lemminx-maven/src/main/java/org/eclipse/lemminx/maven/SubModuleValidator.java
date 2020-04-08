/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

public class SubModuleValidator {
	Model model;
	MavenXpp3Reader mavenreader = new MavenXpp3Reader();

	// TODO: This class shouldn't be instantiating the maven model, but be receiving
	// it from a context class instead
	public void setPomFile(File pomFile) throws FileNotFoundException, IOException, XmlPullParserException {
		model = mavenreader.read(new FileReader(pomFile));
	}

	public Diagnostic validateSubModuleExistence(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		DOMDocument xmlDocument = diagnosticRequest.getDOMDocument();
		Diagnostic diagnostic = null;
		Range range = diagnosticRequest.getRange();
		String tagContent = null;
		if (node.hasChildNodes()) {
			tagContent = node.getChild(0).getNodeValue(); // tagContent is the module to validate eg.
															// <module>tagContent</module>
		}
		if (node.hasChildNodes() && !model.getModules().contains(tagContent)) {
			diagnostic = new Diagnostic(range, String.format("Module '%s' does not exist", tagContent),
					DiagnosticSeverity.Error, xmlDocument.getDocumentURI(), "XML");
			diagnosticRequest.getDiagnostics().add(diagnostic);
		}
		return diagnostic;

	}

}
