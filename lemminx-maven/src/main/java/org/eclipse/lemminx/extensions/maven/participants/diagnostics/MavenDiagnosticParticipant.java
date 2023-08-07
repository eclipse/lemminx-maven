/*******************************************************************************
 * Copyright (c) 2019-2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.CONFIGURATION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROJECT_ELT;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.apache.maven.project.DependencyResolutionResult;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.extensions.maven.MavenInitializationException;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MavenModelOutOfDatedException;
import org.eclipse.lemminx.extensions.maven.project.LoadedMavenProject;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenDiagnosticParticipant implements IDiagnosticsParticipant {
	private static final Logger LOGGER = Logger.getLogger(MavenDiagnosticParticipant.class.getName());

	private final MavenLemminxExtension plugin;

	public MavenDiagnosticParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics,
			XMLValidationSettings validationSettings, CancelChecker cancelChecker) throws CancellationException {
		if (!MavenLemminxExtension.match(xmlDocument)) {
			return;
		}

		try {
			CompletableFuture<LoadedMavenProject> project = plugin.getProjectCache().getLoadedMavenProject(xmlDocument);
			if (MavenLemminxExtension.isUnitTestMode()) {			
				try {
					project.get();
				} catch (InterruptedException | ExecutionException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			if (!project.isDone()) {
				// The pom.xml takes some times to load it, to avoid blocking the XML syntax validation, XML validation based on XSD
				// we retrigger the validation when the pom.xml is loaded.
				project.thenAccept( unused -> plugin.getValidationService()
						.validate(xmlDocument));
				return;
			}

			LoadedMavenProject loadedMavenProject = project.getNow(null);
			Collection<ModelProblem> problems = loadedMavenProject != null ? loadedMavenProject.getProblems() : null;
			if (problems != null) {
				problems.stream().map(problem -> toDiagnostic(problem, xmlDocument))
					.forEach(diagnostics::add);
			}
			DependencyResolutionResult dependencyResolutionResult = 
					loadedMavenProject != null ?
							loadedMavenProject.getDependencyResolutionResult() : null;

			cancelChecker.checkCanceled();
			DOMElement documentElement = xmlDocument.getDocumentElement();
			if (documentElement == null) {
				return;
			}
			Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> tagDiagnostics = 
					configureDiagnosticFunctions(cancelChecker);

			// Validate project element
			cancelChecker.checkCanceled();
			if (PROJECT_ELT.equals(documentElement.getNodeName())) {
				ProjectValidator projectValidator = new ProjectValidator(plugin, dependencyResolutionResult, cancelChecker);
				projectValidator.validateProject(new DiagnosticRequest(documentElement, xmlDocument))
						.ifPresent(diagnosticList -> {
							cancelChecker.checkCanceled();
							diagnostics.addAll(
									diagnosticList.stream().filter(diagnostic -> !diagnostics.contains(diagnostic))
											.collect(Collectors.toList()));
						});
			}

			cancelChecker.checkCanceled();
			Deque<DOMNode> nodes = new ArrayDeque<>();
			documentElement.getChildren().stream().filter(DOMElement.class::isInstance).forEach(nodes::push);
			while (!nodes.isEmpty()) {
				cancelChecker.checkCanceled();
				DOMNode node = nodes.pop();
				String nodeName = node.getLocalName();
				if (nodeName != null) {
					tagDiagnostics.entrySet().stream().filter(entry -> nodeName.equals(entry.getKey()))
							.map(entry -> entry.getValue().apply(new DiagnosticRequest(node, xmlDocument)))
							.filter(Optional::isPresent).map(dl -> dl.get()).forEach(diagnosticList -> {
								cancelChecker.checkCanceled();
								diagnostics.addAll(
										diagnosticList.stream().filter(diagnostic -> !diagnostics.contains(diagnostic))
												.collect(Collectors.toList()));
							});
				}
				cancelChecker.checkCanceled();
				if (node.hasChildNodes()) {
					node.getChildren().stream().filter(DOMElement.class::isInstance).forEach(nodes::push);
				}
			}
		} catch (MavenInitializationException e) {
			// - Maven is initializing
			CompletableFuture<Void> initFuture = e.getFuture();
			if (initFuture != null) {
				initFuture.thenAccept( unused -> plugin.getValidationService()
						.validate(xmlDocument));
			}
		} catch (MavenModelOutOfDatedException e) {
			// - Parse of maven model with DOM document is out of dated
			// -> catch the error to avoid breaking XML diagnostics from LemMinX
		}
	}

	private Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> configureDiagnosticFunctions(
			CancelChecker cancelChecker) {
		PluginValidator pluginValidator = new PluginValidator(plugin, cancelChecker);

		Function<DiagnosticRequest, Optional<List<Diagnostic>>> validatePluginConfiguration = pluginValidator::validateConfiguration;
		Function<DiagnosticRequest, Optional<List<Diagnostic>>> validatePluginGoal = pluginValidator::validateGoal;

		Map<String, Function<DiagnosticRequest, Optional<List<Diagnostic>>>> tagDiagnostics = new HashMap<>();
		tagDiagnostics.put(CONFIGURATION_ELT, validatePluginConfiguration);
		tagDiagnostics.put(GOAL_ELT, validatePluginGoal);
		return tagDiagnostics;
	}

	private Diagnostic toDiagnostic(@Nonnull ModelProblem problem, @Nonnull DOMDocument xmlDocument) {
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setMessage(problem.getMessage());
		diagnostic.setSeverity(toDiagnosticSeverity(problem.getSeverity()));
		Range range = null;

		// The Maven problem returns only line / column position.
		// We try to get the DOM text node at this offset to improve the error range.
		Position start = new Position(problem.getLineNumber() - 1, problem.getColumnNumber() - 1);
		try {
			int offset = xmlDocument.offsetAt(start) + 1;
			DOMNode node = xmlDocument.findNodeAt(offset);
			if (node != null && node.isText()) {
				// The maven problem position higlight a text node use this range
				range = XMLPositionUtility.createRange(node);
			}
		} catch (BadLocationException e) {
			//	Do nothing
		}

		if (range == null) {
			Position end = new Position(problem.getLineNumber() - 1, problem.getColumnNumber());
			range = new Range(start, end);
		}
		diagnostic.setRange(range);
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
