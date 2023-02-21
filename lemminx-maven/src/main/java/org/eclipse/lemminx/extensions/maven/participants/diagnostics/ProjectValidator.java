/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.maven.project.MavenProject;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenSyntaxErrorCode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public class ProjectValidator {
	private static final Logger LOGGER = Logger.getLogger(ProjectValidator.class.getName());

	private MavenLemminxExtension plugin;

	public ProjectValidator(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	/**
	 * Validates if parent groupId and/or version match the project's ones
	 * 
	 * @param diagnosticRequest
	 * @return
	 */
	public Optional<List<Diagnostic>> validateProject(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(diagnosticRequest.getXMLDocument());
		List<Diagnostic> diagnostics = new ArrayList<>();
		diagnostics.addAll(validateParentMatchingGroupIdVersion(diagnosticRequest).get());
		if (project != null) {
			diagnostics.addAll(validateManagedDependencies(diagnosticRequest, project).get());
			diagnostics.addAll(validateManagedPlugins(diagnosticRequest, project).get());
		}
		return Optional.of(diagnostics);
	}
	
	/**
	 * Validates if parent groupId and/or version match the project's ones
	 * 
	 * @param diagnosticRequest
	 * @return
	 */
	private Optional<List<Diagnostic>> validateParentMatchingGroupIdVersion(DiagnosticRequest diagnosticRequest) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		Optional<DOMElement> parent = DOMUtils.findChildElement(node, PARENT_ELT);
		parent.ifPresent(p -> {
			Optional<DOMElement> groupId = DOMUtils.findChildElement(node, GROUP_ID_ELT);
		    groupId.ifPresent(g -> {
					//now compare the values of parent and project groupid..
					String parentString = DOMUtils.findChildElementText(p, GROUP_ID_ELT).orElse(null);
					String childString = DOMUtils.findElementText(g).orElse(null);
					if(parentString != null && parentString.equals(childString)) {
						DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
						diagnostics.add(new Diagnostic(
								XMLPositionUtility.createRange(g.getStartTagCloseOffset() + 1, 
										g.getEndTagOpenOffset(), xmlDocument), 
								"GroupId is duplicate of parent groupId",
								DiagnosticSeverity.Warning,
								xmlDocument.getDocumentURI(), 
								MavenSyntaxErrorCode.DuplicationOfParentGroupId.getCode()));
					}
			    });

		    Optional<DOMElement> version = DOMUtils.findChildElement(node, VERSION_ELT);
		    version.ifPresent(v -> {
					//now compare the values of parent and project version..
					String parentString = DOMUtils.findChildElementText(p, VERSION_ELT).orElse(null);
					String childString = DOMUtils.findElementText(v).orElse(null);
					if(parentString != null && parentString.equals(childString)) {
						DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
						diagnostics.add(new Diagnostic(
								XMLPositionUtility.createRange(v.getStartTagCloseOffset() + 1, 
										v.getEndTagOpenOffset(), xmlDocument), 
								"Version is duplicate of parent version",
								DiagnosticSeverity.Warning,
								xmlDocument.getDocumentURI(), 
								MavenSyntaxErrorCode.DuplicationOfParentVersion.getCode()));
					}
			    });
		});

		return Optional.of(diagnostics);
	}
	    
	/**
	 * TODO: Validates if a dependency version duplicates or overrides a managed dependency version
	 * 
	 * @param diagnosticRequest
	 * @return
	 */
	private Optional<List<Diagnostic>> validateManagedDependencies(DiagnosticRequest diagnosticRequest, MavenProject mavenProject) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		return Optional.of(diagnostics);
	}

	// TODO: Move here the validation from
	// org.eclipse.m2e.core.ui.internal.markers.MarkerLocationService.checkManagedPlugins(IMavenMarkerManager,
	// Element, IResource, MavenProject, String, IStructuredDocument)
	// checkManagedPlugins(mavenMarkerManager, root, pomFile, mavenProject, type,
	// document);
	private Optional<List<Diagnostic>> validateManagedPlugins(DiagnosticRequest diagnosticRequest, MavenProject project) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}
		List<Diagnostic> diagnostics = new ArrayList<>();
		return Optional.of(diagnostics);
	}
}
