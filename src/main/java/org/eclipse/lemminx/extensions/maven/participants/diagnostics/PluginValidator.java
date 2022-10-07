/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MarkdownUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

class PluginValidator {

	private static final Logger LOGGER = Logger.getLogger(PluginValidator.class.getName());

	private MavenLemminxExtension plugin;

	public PluginValidator(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	public Optional<List<Diagnostic>> validatePluginResolution(DiagnosticRequest diagnosticRequest) {
		try {
			MavenPluginUtils.getContainingPluginDescriptor(diagnosticRequest, plugin);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.WARNING, "Could not resolve plugin description", e);

			// Add artifactId diagnostic
			String errorMessage = e.getMessage();
			DOMNode pluginNode = DOMUtils.findClosestParentNode(diagnosticRequest, "plugin");
			Optional<DOMNode> artifactNode = pluginNode.getChildren().stream().filter(node -> !node.isComment())
					.filter(node -> node.getLocalName().equals("artifactId")).findAny();
			List<Diagnostic> diagnostics = new ArrayList<>();
			artifactNode.ifPresent(node -> {
				DiagnosticRequest artifactDiagnosticReq = new DiagnosticRequest(artifactNode.get(),
						diagnosticRequest.getXMLDocument());
				diagnostics.add(artifactDiagnosticReq.createDiagnostic(
						"Plugin could not be resolved. Ensure the plugin's groupId, artifactId and version are present."
								+ MarkdownUtils.LINE_BREAK + "Additional information: " + errorMessage,
						DiagnosticSeverity.Warning));
			});
			if (!diagnostics.isEmpty()) {
				return Optional.of(diagnostics);
			}
		}
		return Optional.empty();
	}

	public Optional<List<Diagnostic>> validateConfiguration(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}
		Optional<List<Diagnostic>> pluginResolutionError = validatePluginResolution(diagnosticRequest);
		if (pluginResolutionError.isPresent()) {
			return pluginResolutionError;
		}

		Set<Parameter> parameters = new HashSet<>();
		try {
			parameters = MavenPluginUtils.collectPluginConfigurationParameters(diagnosticRequest, plugin);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			// A diagnostic was already added in validatePluginResolution()
		}
		if (parameters.isEmpty()) {
			return Optional.empty();
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		if (node.isElement() && node.hasChildNodes()) {
			for (DOMNode childNode : node.getChildren()) {
				DiagnosticRequest childDiagnosticReq = new DiagnosticRequest(childNode, diagnosticRequest.getXMLDocument());
				validateConfigurationElement(childDiagnosticReq, parameters).ifPresent(diagnostics::add);
			}
		}

		return Optional.of(diagnostics);
	}

	public Optional<List<Diagnostic>> validateGoal(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}
		Optional<List<Diagnostic>> pluginResolutionError = validatePluginResolution(diagnosticRequest);
		if (pluginResolutionError.isPresent()) {
			return pluginResolutionError;
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		if (node.isElement() && node.hasChildNodes()) {
			PluginDescriptor pluginDescriptor;
			try {
				pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(diagnosticRequest, plugin);
				if (pluginDescriptor != null) {
					internalValidateGoal(diagnosticRequest, pluginDescriptor).ifPresent(diagnostics::add);;
				}
			} catch (PluginResolutionException | PluginDescriptorParsingException
					| InvalidPluginDescriptorException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				// A diagnostic was already added in validatePluginResolution()
			}
		}
		return Optional.of(diagnostics);
	}

	private static Optional<Diagnostic> internalValidateGoal(DiagnosticRequest diagnosticReq, PluginDescriptor pluginDescriptor) {
		DOMNode node = diagnosticReq.getNode();
		if (!node.hasChildNodes()) {
			return Optional.empty();
		}

		String goal = node.getChild(0).getNodeValue();
		for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
			if (!goal.isEmpty() && goal.equals(mojo.getGoal())) {
				return Optional.empty();
			}
		}
		return Optional.of(diagnosticReq.createDiagnostic("Invalid goal for this plugin: " + goal, DiagnosticSeverity.Warning));
	}

	private static Optional<Diagnostic> validateConfigurationElement(DiagnosticRequest diagnosticReq, Set<Parameter> parameters) {
		DOMNode node = diagnosticReq.getNode();
		if (node.getLocalName() == null) {
			return Optional.empty();
		}
		for (Parameter parameter : parameters) {
			if (node.getLocalName().equals(parameter.getName()) || node.getLocalName().equals(parameter.getAlias())) {
				return Optional.empty();
			}
		}
		return Optional.of(diagnosticReq.createDiagnostic("Invalid plugin configuration: " + diagnosticReq.getCurrentTag(),
				DiagnosticSeverity.Warning));
	}

}
