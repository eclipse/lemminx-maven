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
	private static final String FALSE = "false";

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
				// Now check if the node's child is the right type
				validateConfigurationType(diagnosticReq, parameter);
				return null;
			}
		}
		return new Diagnostic(diagnosticReq.getRange(),
				"Invalid plugin configuration: " + diagnosticReq.getCurrentTag(), DiagnosticSeverity.Warning,
				diagnosticReq.getXMLDocument().getDocumentURI(), "XML");

	}

	private static void validateConfigurationType(DiagnosticRequest diagnosticReq, Parameter parameter) {
		DOMNode node = diagnosticReq.getNode();
		System.out.println("Processing: " + node.getLocalName());
		System.out.println("Children: ");
		node.getChildren().forEach(n -> System.out.println("local name: " + n.getLocalName()));
		node.getChildren().forEach(n -> System.out.println("value: " + n.getNodeValue()));
		// TODO: Type needs to be parsed to account for array '[] 'string
		String type = parameter.getType();
		if (!node.hasChildNodes()) {
			if (parameter.isRequired()) {
				diagnosticReq.getDiagnostics()
						.add(new Diagnostic(diagnosticReq.getRange(),
								"Missing required parameter: " + parameter.getName(), DiagnosticSeverity.Warning,
								diagnosticReq.getXMLDocument().getDocumentURI(), "XML"));

			}
			return;
		}

		if (type.endsWith("[]")) {
			// should have child nodes which are tags
			if (!node.getChildren().stream().allMatch(n -> n.isElement())) {
				diagnosticReq.getDiagnostics()
						.add(new Diagnostic(diagnosticReq.getRange(),
								"This configuration parameter requires a child tag", DiagnosticSeverity.Warning,
								diagnosticReq.getXMLDocument().getDocumentURI(), "XML"));
				return;
			}

			for (DOMNode childNode : node.getChildren()) {
				DiagnosticRequest childDiagnosticReq = new DiagnosticRequest(childNode, diagnosticReq.getXMLDocument(),
						diagnosticReq.getDiagnostics());
				System.out.println("Processing child node: " + childNode.getLocalName());
					internalValidateConfigurationType(childDiagnosticReq, type.substring(0, type.length() - 2));					

			}
		} else {
			internalValidateConfigurationType(diagnosticReq, type);
		}

	}

	private static void internalValidateConfigurationType(DiagnosticRequest diagnosticReq, String type) {
		DOMNode node = diagnosticReq.getNode();
		if (!node.hasChildNodes()) {
			return;
		}
		if (node.getChildren().size() > 1) {
			for (DOMNode childNode : node.getChildren()) {
				DiagnosticRequest childDiagnosticReq = new DiagnosticRequest(childNode, diagnosticReq.getXMLDocument(),
						diagnosticReq.getDiagnostics());
				System.out.println("Processing child node: " + childNode.getLocalName());
				internalValidateConfigurationType(childDiagnosticReq, type);			
			}
		}
		
		DOMNode childNode = node.getChild(0);
		if (childNode.isComment()) {
			return;
		}
		
		String value = childNode.getNodeValue();
		switch (type) {
		case "java.lang.Integer":
			try {
				Integer.parseInt(value);
			} catch (NumberFormatException e) {
				diagnosticReq.getDiagnostics()
				.add(new Diagnostic(diagnosticReq.getRange(),
						"This parameter must be an integer", DiagnosticSeverity.Warning,
						diagnosticReq.getXMLDocument().getDocumentURI(), "XML"));
			}

			break;
		case "java.lang.String":
			break;
		case "boolean":
			Boolean result = Boolean.parseBoolean(value);
			if (!result && !value.equalsIgnoreCase(FALSE)) {
				diagnosticReq.getDiagnostics()
				.add(new Diagnostic(diagnosticReq.getRange(),
						"This parameter must be a boolean", DiagnosticSeverity.Warning,
						diagnosticReq.getXMLDocument().getDocumentURI(), "XML"));
			}
			break;
		default:
			break;
		}
	}

}
