/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public class PluginValidator {
	private final MavenProjectCache cache;
	private final MavenPluginManager pluginManager;
	private final RepositorySystemSession repoSession;

	public PluginValidator(MavenProjectCache cache, RepositorySystemSession repoSession,
			MavenPluginManager pluginManager) {
		this.cache = cache;
		this.repoSession = repoSession;
		this.pluginManager = pluginManager;
	}
	
	public boolean validatePluginResolution(DiagnosticRequest diagnosticRequest) {
		try {
			MavenPluginUtils.getContainingPluginDescriptor(diagnosticRequest, cache, repoSession,
					pluginManager);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			//Add artifactId diagnostic
			e.printStackTrace();
			String errorMessage = e.getMessage();
			DOMNode pluginNode = DOMUtils.findClosestParentNode(diagnosticRequest, "plugin");
			Optional<DOMNode> artifactNode = pluginNode.getChildren().stream().filter(node -> node.getLocalName().equals("artifactId")).findAny();
			artifactNode.ifPresent(node -> {
				DiagnosticRequest artifactDiagnosticReq = new DiagnosticRequest(node,
						diagnosticRequest.getXMLDocument(), diagnosticRequest.getDiagnostics());
				Diagnostic unresolvablePlugin = new Diagnostic(artifactDiagnosticReq.getRange(),
						"Plugin could not be resolved. Ensure the plugin's groupId, artifactId and version are present." + MavenPluginUtils.LINE_BREAK + "Additional information: "
								+ errorMessage, DiagnosticSeverity.Warning,
				artifactDiagnosticReq.getXMLDocument().getDocumentURI(), "XML");
				List<Diagnostic> diagnostics = diagnosticRequest.getDiagnostics();
				if (!diagnostics.contains(unresolvablePlugin)) {
					diagnosticRequest.getDiagnostics().add(unresolvablePlugin);					
				}
			});
			return false;
		}
		return true;
	}

	public Diagnostic validateConfiguration(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return null;
		}
		if (!validatePluginResolution(diagnosticRequest)) {
			return null;
		}
		
		
		List<Parameter> parameters = new ArrayList<>();
		try {
			parameters = MavenPluginUtils.collectPluginConfigurationParameters(diagnosticRequest, cache, repoSession,
					pluginManager);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			e.printStackTrace();
			// A diagnostic was already added in validatePluginResolution()
		}
		if (parameters.isEmpty()) {
			return null;
		}
		if (node.isElement() && node.hasChildNodes()) {
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
		if (!validatePluginResolution(diagnosticRequest)) {
			return null;
		}
		if (node.isElement() && node.hasChildNodes()) {
			PluginDescriptor pluginDescriptor;
			try {
				pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(diagnosticRequest, cache, repoSession,
						pluginManager);
				Diagnostic diag = internalValidateGoal(diagnosticRequest, pluginDescriptor);
				if (diag != null) {
					diagnosticRequest.getDiagnostics().add(diag);
				}
			} catch (PluginResolutionException | PluginDescriptorParsingException
					| InvalidPluginDescriptorException e) {
				e.printStackTrace();
				// A diagnostic was already added in validatePluginResolution()
			}
		}
		return null;
	}

	private static Diagnostic internalValidateGoal(DiagnosticRequest diagnosticReq, PluginDescriptor pluginDescriptor) {
		DOMNode node = diagnosticReq.getNode();
		if (!node.hasChildNodes()) {
			return null;
		}

		String goal = node.getChild(0).getNodeValue();
		for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
			if (!goal.isEmpty() && goal.equals(mojo.getGoal())) {
				return null;
			}
		}

		return new Diagnostic(diagnosticReq.getRange(), "Invalid goal for this plugin: " + goal,
				DiagnosticSeverity.Warning, diagnosticReq.getXMLDocument().getDocumentURI(), "XML");

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
		return new Diagnostic(diagnosticReq.getRange(),
				"Invalid plugin configuration: " + diagnosticReq.getCurrentTag(), DiagnosticSeverity.Warning,
				diagnosticReq.getXMLDocument().getDocumentURI(), "XML");

	}

}
