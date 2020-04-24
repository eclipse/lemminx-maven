/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.List;

import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.descriptor.Parameter;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public class PluginValidator {
	private final MavenProjectCache cache;
	private final MavenPluginManager pluginManager;

	public PluginValidator(MavenProjectCache cache, MavenPluginManager pluginManager) {
		this.cache = cache;
		this.pluginManager = pluginManager;
	}

	public Diagnostic validateConfiguration(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return null;
		}
		if (node.isElement() && node.hasChildNodes()) {
			List<Parameter> parameters = MavenPluginUtils.collectPluginConfigurationParameters(diagnosticRequest, cache,
					pluginManager);
			for (DOMNode childNode : node.getChildren()) {
				DiagnosticRequest childDiagnosticReq = new DiagnosticRequest(childNode,
						diagnosticRequest.getXMLDocument(), diagnosticRequest.getDiagnostics());
				Diagnostic diag = internalValidateConfiguration(childDiagnosticReq, parameters);
				if (diag != null) {
					diagnosticRequest.getDiagnostics().add(diag);
				}
			}
		}
		return null;
	}

	public Diagnostic validateGoal(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return null;
		}
		if (node.isElement() && node.hasChildNodes()) {
			List<Parameter> parameters = MavenPluginUtils.collectPluginConfigurationParameters(diagnosticRequest, cache,
					pluginManager);
			Diagnostic diag = internalValidateGoal(diagnosticRequest, parameters);
			if (diag != null) {
				diagnosticRequest.getDiagnostics().add(diag);
			}
		}
		return null;
	}

	private static Diagnostic internalValidateGoal(DiagnosticRequest diagnosticReq, List<Parameter> parameters) {
		DOMNode node = diagnosticReq.getNode();
		if (!node.hasChildNodes()) {
			return null;
		}
		for (Parameter parameter : parameters) {
			if (node.getChild(0).getNodeValue().equals(parameter.getName())) {
				return null;
			}
		}
		return new Diagnostic(diagnosticReq.getRange(), "Invalid goal for this plugin", DiagnosticSeverity.Warning,
				diagnosticReq.getXMLDocument().getDocumentURI(), "XML");

	}

	private static Diagnostic internalValidateConfiguration(DiagnosticRequest diagnosticReq,
			List<Parameter> parameters) {
		DOMNode node = diagnosticReq.getNode();
		if (node.getLocalName() == null) {
			return null;
		}
		for (Parameter parameter : parameters) {
			if (node.getLocalName().equals(parameter.getName())) {
				return null;
			}
		}
		return new Diagnostic(diagnosticReq.getRange(), "Invalid plugin configuration: " + diagnosticReq.getCurrentTag(), DiagnosticSeverity.Warning,
				diagnosticReq.getXMLDocument().getDocumentURI(), "XML");

	}

}
