/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

public class VersionValidator {

	public static Diagnostic validateVersion(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		DOMDocument xmlDocument = diagnosticRequest.getDOMDocument();
		Dependency model = MavenParseUtils.parseArtifact(node);
		Artifact artifact = null;
		Range range = diagnosticRequest.getRange();
		Diagnostic diagnostic = null;
		try {
			 artifact = new DefaultArtifact(model.getGroupId(), model.getArtifactId(), model.getVersion(), model.getScope(), model.getType(), model.getClassifier(), new DefaultArtifactHandler(model.getType()));
			 if (!artifact.isSelectedVersionKnown()) {
				diagnostic = new Diagnostic(range, "Version Error", DiagnosticSeverity.Error,
						xmlDocument.getDocumentURI(), "XML");
				diagnosticRequest.getDiagnostics().add(diagnostic);
			}
		} catch (Exception e) {
			e.printStackTrace();
			diagnostic = new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error,
					xmlDocument.getDocumentURI(), "XML");
			diagnosticRequest.getDiagnostics().add(diagnostic);
		}
		return diagnostic;
	}
}
