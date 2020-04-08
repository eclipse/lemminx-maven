/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenDiagnosticParticipant implements IDiagnosticsParticipant {

	private MavenProjectCache projectCache;

	public MavenDiagnosticParticipant(MavenProjectCache projectCache) {
		this.projectCache = projectCache;
	}

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics, CancelChecker monitor) {
		projectCache.getProblemsFor(xmlDocument).stream().map(this::toDiagnostic).forEach(diagnostics::add);
		DOMElement documentElement = xmlDocument.getDocumentElement();
		HashMap<String, Function<DiagnosticRequest, Diagnostic>> tagDiagnostics = configureDiagnosticFunctions(
				xmlDocument);

		Deque<DOMNode> nodes = new ArrayDeque<>();
		for (DOMNode node : documentElement.getChildren()) {
			nodes.push(node);
		}
		while (!nodes.isEmpty()) {
			DOMNode node = nodes.pop();
			for (String tagToValidate : tagDiagnostics.keySet()) {
				if (node.getLocalName() != null && node.getLocalName().equals(tagToValidate)) {
					Diagnostic diagnostic = null;
					try {
						diagnostic = tagDiagnostics.get(tagToValidate)
								.apply(new DiagnosticRequest(node, xmlDocument, diagnostics));
					} catch (Exception e) {
						// TODO: Use plug-in error logger
						e.printStackTrace();
					}

					if (diagnostic != null) {
						diagnostics.add(diagnostic);
					}
				}
			}
			if (node.hasChildNodes()) {
				for (DOMNode childNode : node.getChildren()) {
					nodes.push(childNode);
				}
			}
		}
	}

	private HashMap<String, Function<DiagnosticRequest, Diagnostic>> configureDiagnosticFunctions(
			DOMDocument xmlDocument) {
//		SubModuleValidator subModuleValidator= new SubModuleValidator();
//		try {
//			subModuleValidator.setPomFile(new File(xmlDocument.getDocumentURI().substring(5)));
//		} catch (IOException | XmlPullParserException e) {
//			// TODO: Use plug-in error logger
//			e.printStackTrace();
//		}
		//Function<DiagnosticRequest, Diagnostic> versionFunc = VersionValidator::validateVersion;
		//Function<DiagnosticRequest, Diagnostic> submoduleExistenceFunc = subModuleValidator::validateSubModuleExistence;
		// Below is a mock Diagnostic function which creates a warning between inside
		// <configuration> tags
		//Function<DiagnosticRequest, Diagnostic> configFunc = diagnosticReq -> new Diagnostic(diagnosticReq.getRange(),
		//		"Configuration Error", DiagnosticSeverity.Warning, xmlDocument.getDocumentURI(), "XML");

		HashMap<String, Function<DiagnosticRequest, Diagnostic>> tagDiagnostics = new HashMap<>();
		//tagDiagnostics.put("version", versionFunc);
		//tagDiagnostics.put("configuration", configFunc);
		//tagDiagnostics.put("module", submoduleExistenceFunc);
		return tagDiagnostics;
	}

	private Diagnostic toDiagnostic(@Nonnull ModelProblem problem) {
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setMessage(problem.getMessage());
		diagnostic.setSeverity(toDiagnosticSeverity(problem.getSeverity()));
		diagnostic.setRange(new Range(new Position(problem.getLineNumber() - 1, problem.getColumnNumber() - 1),
				new Position(problem.getLineNumber() - 1, problem.getColumnNumber())));
		return diagnostic;
	}

	private DiagnosticSeverity toDiagnosticSeverity(Severity severity) {
		switch (severity) {
		case ERROR:
		case FATAL:
			return DiagnosticSeverity.Error;
		case WARNING:
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}

}
