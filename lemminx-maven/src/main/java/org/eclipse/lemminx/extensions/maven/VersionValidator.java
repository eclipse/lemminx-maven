/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public class VersionValidator {

	private static final Logger LOGGER = Logger.getLogger(VersionValidator.class.getName());

	public static Optional<List<Diagnostic>> validateVersion(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		Dependency model = MavenParseUtils.parseArtifact(node);
		Artifact artifact = null;
		try {
			artifact = new DefaultArtifact(model.getGroupId(), model.getArtifactId(), model.getVersion(), model.getScope(), model.getType(), model.getClassifier(), new DefaultArtifactHandler(model.getType()));
			if (!artifact.isSelectedVersionKnown()) {
				return Optional.of(Collections
						.singletonList(diagnosticRequest.createDiagnostic("Version Error", DiagnosticSeverity.Error)));
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getCause().toString(), e);
		}
		return Optional.empty();
	}
}
