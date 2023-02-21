/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.diagnostics;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CLASSIFIER_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROFILE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROFILES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TYPE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.InputSource;
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

	public static final String ATTR_MANAGED_VERSION_LOCATION = "managedVersionLocation"; //$NON-NLS-1$
	public static final String ATTR_MANAGED_VERSION_LINE = "managedVersionLine"; //$NON-NLS-1$
	public static final String ATTR_MANAGED_VERSION_COLUMN = "managedVersionColumn"; //$NON-NLS-1$

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
	 * Validates if a dependency version duplicates or overrides a managed dependency version
	 * 
	 * @param diagnosticRequest
	 * @return
	 */
	private Optional<List<Diagnostic>> validateManagedDependencies(DiagnosticRequest diagnosticRequest, MavenProject mavenProject) {
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		List<DOMElement> candidates = new ArrayList<>();
		Optional<DOMElement> dependencies = DOMUtils.findChildElement(node, DEPENDENCIES_ELT);
		dependencies.ifPresent(dependenciesElement -> {
			DOMUtils.findChildElements(dependenciesElement, DEPENDENCY_ELT).stream()
				.filter(dependency -> DOMUtils.findChildElement(dependency, VERSION_ELT).isPresent())
				.forEach(candidates::add);
		});
	
		// we should also consider <dependencies> section in the profiles, but profile
		// are optional and so is their
		// dependencyManagement section.. that makes handling our markers more complex.
		// see MavenProject.getInjectedProfileIds() for a list of currently active
		// profiles in effective pom
		String currentProjectKey = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" //$NON-NLS-1$//$NON-NLS-2$
				+ mavenProject.getVersion();
		List<String> activeprofiles = mavenProject.getInjectedProfileIds().get(currentProjectKey);
		// remember what profile we found the dependency in.
		Optional<DOMElement> profiles = DOMUtils.findChildElement(node, PROFILES_ELT);
		profiles.ifPresent(profilesElement -> {
			DOMUtils.findChildElements(profilesElement, PROFILE_ELT).stream()
				.forEach(profile -> {
					Optional<String> idString = DOMUtils.findChildElementText(profile, ID_ELT);
					if (idString.isPresent() && activeprofiles.contains(idString.get())) {
						Optional<DOMElement> profileDependencies = DOMUtils.findChildElement(profile, DEPENDENCIES_ELT);
						profileDependencies.ifPresent(dependenciesElement -> {
							DOMUtils.findChildElements(dependenciesElement, DEPENDENCY_ELT).stream()
							.filter(dependency -> DOMUtils.findChildElement(dependency, VERSION_ELT).isPresent())
							.forEach(dependency -> {
								candidates.add(dependency);
							});
						});
					}
				});
		});

		//collect the managed dep ids
		Map<String, Dependency> managed = new HashMap<>();
		DependencyManagement dm = mavenProject.getDependencyManagement();
		if(dm != null) {
			List<Dependency> deps = dm.getDependencies();
			deps.stream().filter(dep -> dep.getVersion() != null)
				.forEach(dep -> managed.put(dep.getManagementKey(), dep));
		}

		//now we have all the candidates, match them against the effective managed set
		List<Diagnostic> diagnostics = new ArrayList<>();
		candidates.stream().forEach(dep -> {
			Optional<DOMElement> version = DOMUtils.findChildElement(dep, VERSION_ELT);
			version.ifPresent(v -> {
				String grpString = DOMUtils.findChildElementText(dep, GROUP_ID_ELT).orElse(null);
				String artString = DOMUtils.findChildElementText(dep, ARTIFACT_ID_ELT).orElse(null);
				String versionString = DOMUtils.findElementText(v).orElse(null);
				if(grpString != null && artString != null && versionString != null) {
			        String typeString = DOMUtils.findChildElementText(dep, TYPE_ELT).orElse(null);
			        String classifier = DOMUtils.findChildElementText(dep, CLASSIFIER_ELT).orElse(null);
			        String id = getDependencyKey(grpString, artString, typeString, classifier);
			        if(managed.containsKey(id)) {
			            Dependency managedDep = managed.get(id);
			            String managedVersion = managedDep == null ? null : managedDep.getVersion();
			            String msg = versionString.equals(managedVersion)
			                    ? "Duplicating managed version %s for %s"
			                    : "Overriding managed version %s for %s";
			            
						DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
						Diagnostic diagnostic = new Diagnostic(
								XMLPositionUtility.createRange(v.getStartTagCloseOffset() + 1, 
										v.getEndTagOpenOffset(), xmlDocument), 
								String.format(msg, managedVersion, artString),
								DiagnosticSeverity.Warning,
								xmlDocument.getDocumentURI(), 
								MavenSyntaxErrorCode.OverridingOfManagedDependency.getCode());
						setManagedVersionAttributes(diagnostic, mavenProject, managedDep);
						diagnostics.add(diagnostic);
			        }
				}
			});
		});

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
	
	private static String getDependencyKey(String groupId, String artifactId, String type, String classifier) {
		StringBuilder key = new StringBuilder(groupId).append(":").append(artifactId).append(":") //$NON-NLS-1$ //$NON-NLS-2$
				.append(type == null ? "jar" : type);//$NON-NLS-1$
		if (classifier != null) {
			key.append(":").append(classifier);//$NON-NLS-1$
		}
		return key.toString();
	}

	private static void setManagedVersionAttributes(Diagnostic diagnostic, MavenProject mavenproject,
			InputLocationTracker dependencyOrPlugin) {
		InputLocation location = dependencyOrPlugin == null ? null : dependencyOrPlugin.getLocation("version");
		if (location != null) {
			InputSource source = location.getSource();
			if (source != null) {
				String loc = source.getLocation();
				if (loc != null) {
					File file = new File(loc);
					int lineNumber = location != null ? location.getLineNumber() : -1;
					int columnNumber = location != null ? location.getColumnNumber() : -1;
					if (file != null) {
						Map<String, String> managedLocationData = new HashMap<>();
						managedLocationData.put(ATTR_MANAGED_VERSION_LOCATION, file.toURI().toString());
						if (lineNumber > -1) {
							managedLocationData.put(ATTR_MANAGED_VERSION_LINE, String.valueOf(lineNumber));
							if (columnNumber > -1) {
								managedLocationData.put(ATTR_MANAGED_VERSION_COLUMN, String.valueOf(columnNumber));
							}
						}
						diagnostic.setData(managedLocationData);
					}
				}
			}
		}
	}
}
