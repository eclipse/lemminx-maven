/*******************************************************************************
 * Copyright (c) 2019-2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.CONFIGURATION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenDiagnosticParticipant implements IDiagnosticsParticipant {

	private final MavenLemminxExtension plugin;

	public MavenDiagnosticParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics,
			XMLValidationSettings validationSettings, CancelChecker monitor) {
		if (!MavenLemminxExtension.match(xmlDocument)) {
			return;
		}

		Collection<ModelProblem> problems = plugin.getProjectCache().getProblemsFor(xmlDocument);
		if (problems != null) {
			problems.stream().map(this::toDiagnostic).forEach(diagnostics::add);
		}
		
		DOMElement documentElement = xmlDocument.getDocumentElement();
		Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> tagDiagnostics = configureDiagnosticFunctions();

		Deque<DOMNode> nodes = new ArrayDeque<>();
		for (DOMNode node : documentElement.getChildren()) {
			nodes.push(node);
		}
		while (!nodes.isEmpty()) {
			DOMNode node = nodes.pop();
			for (Entry<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> entry : tagDiagnostics
					.entrySet()) {
				if (node.getLocalName() != null && node.getLocalName().equals(entry.getKey())) {
					entry.getValue().apply(new DiagnosticRequest(node, xmlDocument)).ifPresent(diagnosticList -> {
						// Don't add a diagnostic if it already exists
						diagnostics.addAll(diagnosticList.stream()
								.filter(diagnostic -> !diagnostics.contains(diagnostic)).collect(Collectors.toList()));
					});
				}
			}
			if (node.hasChildNodes()) {
				for (DOMNode childNode : node.getChildren()) {
					nodes.push(childNode);
				}
			}
		}
	}

	private Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> configureDiagnosticFunctions() {
		PluginValidator pluginValidator = new PluginValidator(plugin);

		Function<DiagnosticRequest, Optional<List<Diagnostic>>> validatePluginConfiguration = pluginValidator::validateConfiguration;
		Function<DiagnosticRequest, Optional<List<Diagnostic>>> validatePluginGoal = pluginValidator::validateGoal;

		Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> tagDiagnostics = new HashMap<>();
		tagDiagnostics.put(CONFIGURATION_ELT, validatePluginConfiguration);
		tagDiagnostics.put(GOAL_ELT, validatePluginGoal);
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
		return switch (severity) {
			case ERROR, FATAL -> DiagnosticSeverity.Error;
			case WARNING -> DiagnosticSeverity.Warning;
			default -> DiagnosticSeverity.Information;
		};
	}

}
