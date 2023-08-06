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
import static org.eclipse.lemminx.extensions.maven.DOMConstants.BUILD_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CLASSIFIER_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGINS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROFILES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROFILE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TYPE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import javax.annotation.Nonnull;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.dom.DOMComment;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenParseUtils;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class ProjectValidator {

	public static final String ATTR_MANAGED_VERSION_LOCATION = "managedVersionLocation"; //$NON-NLS-1$
	public static final String ATTR_MANAGED_VERSION_LINE = "managedVersionLine"; //$NON-NLS-1$
	public static final String ATTR_MANAGED_VERSION_COLUMN = "managedVersionColumn"; //$NON-NLS-1$

	/**
	 * string that gets included in pom.xml file comments right after the given "<version/>" element
	 * and makes the project validator to ignore the managed version override problem
	 */
	public static String MARKER_IGNORE_MANAGED = "$NO-MVN-MAN-VER$";//$NON-NLS-1$

	private final MavenLemminxExtension plugin;
	private final DependencyResolutionResult dependencyResolutionResult;
	
	private final CancelChecker cancelChecker;

	public ProjectValidator(MavenLemminxExtension plugin, DependencyResolutionResult dependencyResolutionResult, @Nonnull CancelChecker cancelChecker) {
		this.plugin = plugin;
		this.dependencyResolutionResult = dependencyResolutionResult;
		this.cancelChecker = cancelChecker;
	}

	/**
	 * Validates if parent groupId and/or version match the project's ones
	 * 
	 * @param diagnosticRequest
	 * @return
	 */
	public Optional<List<Diagnostic>> validateProject(DiagnosticRequest diagnosticRequest) throws CancellationException {
		cancelChecker.checkCanceled();
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		MavenProject project = plugin.getProjectCache()
				.getLastSuccessfulMavenProject(diagnosticRequest.getXMLDocument());
		cancelChecker.checkCanceled();
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
				// now compare the values of parent and project groupid..
				String parentString = DOMUtils.findChildElementText(p, GROUP_ID_ELT).orElse(null);
				String childString = DOMUtils.findElementText(g).orElse(null);
				if (parentString != null && parentString.equals(childString)) {
					DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
					diagnostics.add(new Diagnostic(
							XMLPositionUtility.createRange(g.getStartTagCloseOffset() + 1, g.getEndTagOpenOffset(),
									xmlDocument),
							"GroupId is duplicate of parent groupId", DiagnosticSeverity.Warning,
							xmlDocument.getDocumentURI(), MavenSyntaxErrorCode.DuplicationOfParentGroupId.getCode()));
				}
			});

			Optional<DOMElement> version = DOMUtils.findChildElement(node, VERSION_ELT);
			version.ifPresent(v -> {
				// now compare the values of parent and project version..
				String parentString = DOMUtils.findChildElementText(p, VERSION_ELT).orElse(null);
				String childString = DOMUtils.findElementText(v).orElse(null);
				if (parentString != null && parentString.equals(childString)) {
					DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
					diagnostics.add(new Diagnostic(
							XMLPositionUtility.createRange(v.getStartTagCloseOffset() + 1, v.getEndTagOpenOffset(),
									xmlDocument),
							"Version is duplicate of parent version", DiagnosticSeverity.Warning,
							xmlDocument.getDocumentURI(), MavenSyntaxErrorCode.DuplicationOfParentVersion.getCode()));
				}
			});
		});

		return Optional.of(diagnostics);
	}

	/**
	 * Validates if a dependency version duplicates or overrides a managed dependency version
	 * 
	 * @param diagnosticRequest
	 * @param mavenProject
	 * @return
	 */
	private Optional<List<Diagnostic>> validateManagedDependencies(DiagnosticRequest diagnosticRequest,
			MavenProject mavenProject) {
		cancelChecker.checkCanceled();
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		cancelChecker.checkCanceled();
		List<DOMElement> candidates = new ArrayList<>();
		Optional<DOMElement> dependencies = DOMUtils.findChildElement(node, DEPENDENCIES_ELT);
		dependencies.ifPresent(dependenciesElement -> {
			cancelChecker.checkCanceled();
			DOMUtils.findChildElements(dependenciesElement, DEPENDENCY_ELT)
					.stream()
					.filter(dependency -> validateDependency(dependency, diagnosticRequest, diagnostics))
					.filter(dependency -> DOMUtils.findChildElement(dependency, VERSION_ELT).isPresent())
					.forEach(candidates::add);
		});

		// we should also consider <dependencies> section in the profiles, but profile
		// are optional and so is their
		// dependencyManagement section.. that makes handling our markers more complex.
		// see MavenProject.getInjectedProfileIds() for a list of currently active
		// profiles in effective pom
		cancelChecker.checkCanceled();
		String currentProjectKey = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" //$NON-NLS-1$//$NON-NLS-2$
				+ mavenProject.getVersion();
		List<String> activeprofiles = mavenProject.getInjectedProfileIds().get(currentProjectKey);
		Map<DOMElement, String> candidateProfile = new HashMap<>();
		if (activeprofiles != null && !activeprofiles.isEmpty()) {
			// remember what profile we found the dependency in.
			cancelChecker.checkCanceled();
			Optional<DOMElement> profiles = DOMUtils.findChildElement(node, PROFILES_ELT);
			profiles.ifPresent(profilesElement -> {
				cancelChecker.checkCanceled();
				DOMUtils.findChildElements(profilesElement, PROFILE_ELT).stream().forEach(profile -> {
					cancelChecker.checkCanceled();
					Optional<String> idString = DOMUtils.findChildElementText(profile, ID_ELT);
					if (idString.isPresent() && activeprofiles.contains(idString.get())) {
						cancelChecker.checkCanceled();
						Optional<DOMElement> profileDependencies = DOMUtils.findChildElement(profile, DEPENDENCIES_ELT);
						profileDependencies.ifPresent(dependenciesElement -> {
							cancelChecker.checkCanceled();
							DOMUtils.findChildElements(dependenciesElement, DEPENDENCY_ELT).stream()
									.filter(dependency -> DOMUtils.findChildElement(dependency, VERSION_ELT).isPresent())
									.forEach(dependency -> {
										cancelChecker.checkCanceled();
										candidates.add(dependency);
										candidateProfile.put(dependency, idString.get());
									});
						});
					}
				});
			});
		}
		
		// collect the managed dep ids
		cancelChecker.checkCanceled();
		Map<String, Dependency> managed = new HashMap<>();
		DependencyManagement dm = mavenProject.getDependencyManagement();
		if (dm != null) {
			List<Dependency> deps = dm.getDependencies();
			if (deps != null) {
				// #335366
				// 355882 use dep.getManagementKey() to prevent false positives
				// when type or classifier doesn't match
				cancelChecker.checkCanceled();
				deps.stream().filter(dep -> dep.getVersion() != null)
						.forEach(dep -> managed.put(dep.getManagementKey(), dep));
			}
		}

		// now we have all the candidates, match them against the effective managed set
		cancelChecker.checkCanceled();
		candidates.stream().forEach(dep -> {
			cancelChecker.checkCanceled();
			Optional<DOMElement> version = DOMUtils.findChildElement(dep, VERSION_ELT);
			version.ifPresent(v -> {
				String grpString = DOMUtils.findChildElementText(dep, GROUP_ID_ELT).orElse(null);
				String artString = DOMUtils.findChildElementText(dep, ARTIFACT_ID_ELT).orElse(null);
				String versionString = DOMUtils.findElementText(v).orElse(null);
				if (grpString != null && artString != null && versionString != null 
						&& !lookForIgnoreMarker(v, MARKER_IGNORE_MANAGED)) {
					String typeString = DOMUtils.findChildElementText(dep, TYPE_ELT).orElse(null);
					String classifier = DOMUtils.findChildElementText(dep, CLASSIFIER_ELT).orElse(null);
					String id = getDependencyKey(grpString, artString, typeString, classifier);
					if (managed.containsKey(id)) {
						cancelChecker.checkCanceled();
						Dependency managedDep = managed.get(id);
						String managedVersion = managedDep == null ? null : managedDep.getVersion();
						String msg = versionString.equals(managedVersion) ? "Duplicating managed version %s for %s"
								: "Overriding managed version %s for %s";

						DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
						Diagnostic diagnostic = new Diagnostic(
								XMLPositionUtility.createRange(v.getStartTagCloseOffset() + 1, v.getEndTagOpenOffset(),
										xmlDocument),
								String.format(msg, managedVersion, artString), DiagnosticSeverity.Warning,
								xmlDocument.getDocumentURI(),
								MavenSyntaxErrorCode.OverridingOfManagedDependency.getCode());

						addDiagnocticData(diagnostic, GROUP_ID_ELT, grpString);
						addDiagnocticData(diagnostic, ARTIFACT_ID_ELT, artString);
						setManagedVersionAttributes(diagnostic, mavenProject, managedDep);
						String profile = candidateProfile.get(dep);
						if (profile != null) {
							addDiagnocticData(diagnostic, PROFILE_ELT, profile);
						}
						cancelChecker.checkCanceled();
						diagnostics.add(diagnostic);
					}
				}
			});
		});

		return Optional.of(diagnostics);
	}

	private boolean validateDependency(DOMElement dependencyNode, DiagnosticRequest diagnosticRequest,
			List<Diagnostic> diagnostics) {
		if (dependencyResolutionResult != null && dependencyResolutionResult.getUnresolvedDependencies() != null
				&& !dependencyResolutionResult.getUnresolvedDependencies().isEmpty()) {
			Dependency dependency = MavenParseUtils.parseArtifact(dependencyNode);
			for (org.eclipse.aether.graph.Dependency unresolved : dependencyResolutionResult
					.getUnresolvedDependencies()) {
				if (Objects.equals(dependency.getGroupId(), unresolved.getArtifact().getGroupId())
						&& Objects.equals(dependency.getArtifactId(), unresolved.getArtifact().getArtifactId())
						&& ((dependency.getVersion() != null && dependency.getVersion().startsWith("${"))
								|| Objects.equals(dependency.getVersion(), unresolved.getArtifact().getVersion()))) {
					List<Exception> errors = dependencyResolutionResult.getResolutionErrors(unresolved);
					for (Exception error : errors) {
						Diagnostic diagnostic = diagnosticRequest.createDiagnostic(error.getMessage(),
								DiagnosticSeverity.Error);
						Range range = XMLPositionUtility.createRange(dependencyNode);
						diagnostic.setRange(range);
						diagnostics.add(diagnostic);
					}
				}
			}
		}
		return true;
	}

	/**
	 * Validates if a dependency version duplicates or overrides a managed plugin version
	 * 
	 * @param diagnosticRequest
	 * @param mavenProject
	 * @return
	 */
	private Optional<List<Diagnostic>> validateManagedPlugins(DiagnosticRequest diagnosticRequest,
			MavenProject mavenProject) {
		cancelChecker.checkCanceled();
		DOMNode node = diagnosticRequest.getNode();
		if (node == null) {
			return Optional.empty();
		}

		List<DOMElement> candidates = new ArrayList<>();
		Optional<DOMElement> build = DOMUtils.findChildElement(node, BUILD_ELT);
		build.ifPresent(buildElement -> {
			cancelChecker.checkCanceled();
			Optional<DOMElement> plugins = DOMUtils.findChildElement(buildElement, PLUGINS_ELT);
			plugins.ifPresent(pluginsElement -> {
				DOMUtils.findChildElements(pluginsElement, PLUGIN_ELT).stream()
						.filter(plugin -> DOMUtils.findChildElement(plugin, VERSION_ELT).isPresent())
						.forEach(candidates::add);
			});
		});

		// we should also consider <plugins> section in the profiles, but profile are
		// optional and so is their
		// pluginManagement section.. that makes handling our markers more complex.
		// see MavenProject.getInjectedProfileIds() for a list of currently active
		// profiles in effective pom
		cancelChecker.checkCanceled();
		String currentProjectKey = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" //$NON-NLS-1$//$NON-NLS-2$
				+ mavenProject.getVersion();
		List<String> activeprofiles = mavenProject.getInjectedProfileIds().get(currentProjectKey);
		// remember what profile we found the dependency in.
		Map<DOMElement, String> candidateProfile = new HashMap<>();
		Optional<DOMElement> profiles = DOMUtils.findChildElement(node, PROFILES_ELT);
		profiles.ifPresent(profilesElement -> {
			cancelChecker.checkCanceled();
			DOMUtils.findChildElements(profilesElement, PROFILE_ELT).stream().forEach(profile -> {
				cancelChecker.checkCanceled();
				Optional<String> idString = DOMUtils.findChildElementText(profile, ID_ELT);
				if (idString.isPresent() && activeprofiles.contains(idString.get())) {
					Optional<DOMElement> profileBuild = DOMUtils.findChildElement(profile, BUILD_ELT);
					profileBuild.ifPresent(buildElement -> {
						Optional<DOMElement> plugins = DOMUtils.findChildElement(buildElement, PLUGINS_ELT);
						plugins.ifPresent(pluginsElement -> {
							DOMUtils.findChildElements(pluginsElement, PLUGIN_ELT).stream()
									.filter(plugin -> DOMUtils.findChildElement(plugin, VERSION_ELT).isPresent())
									.forEach(plugin -> {
										cancelChecker.checkCanceled();
										candidates.add(plugin);
										candidateProfile.put(plugin, idString.get());
									});
						});
					});
				}
			});
		});

		// collect the managed plugin ids
		cancelChecker.checkCanceled();
		Map<String, Plugin> managed = new HashMap<>();
		PluginManagement pm = mavenProject.getPluginManagement();
		if (pm != null) {
			List<Plugin> plgs = pm.getPlugins();
			if (plgs != null) {
				cancelChecker.checkCanceled();
				plgs.stream().filter(plg -> {
					InputLocation loc = plg.getLocation("version");
					if (loc == null || loc.getSource() == null) {
						return false;
					}
					// #350203 skip plugins defined in the superpom
					String modelID = loc.getSource().getModelId();
					return (modelID == null || !(modelID.startsWith("org.apache.maven:maven-model-builder:")
							&& modelID.endsWith(":super-pom")));
				}).forEach(plg -> managed.put(plg.getKey(), plg));
			}
		}

		// now we have all the candidates, match them against the effective managed set
		cancelChecker.checkCanceled();
		List<Diagnostic> diagnostics = new ArrayList<>();
		candidates.stream().forEach(dep -> {
			cancelChecker.checkCanceled();
			String grpString = DOMUtils.findChildElementText(dep, GROUP_ID_ELT).orElse(null);
			if (grpString == null) {
				grpString = "org.apache.maven.plugins"; //$NON-NLS-1$
			}
			String artString = DOMUtils.findChildElementText(dep, ARTIFACT_ID_ELT).orElse(null);
			DOMElement version = DOMUtils.findChildElement(dep, VERSION_ELT).orElse(null);
			String versionString = DOMUtils.findChildElementText(dep, VERSION_ELT).orElse(null);
			if (artString != null && versionString != null && !lookForIgnoreMarker(version, MARKER_IGNORE_MANAGED)) {
				cancelChecker.checkCanceled();
				String id = Plugin.constructKey(grpString, artString);
				if (managed.containsKey(id)) {
					cancelChecker.checkCanceled();
					Plugin managedPlugin = managed.get(id);
					String managedVersion = managedPlugin == null ? null : managedPlugin.getVersion();
					String msg = versionString.equals(managedVersion) ? "Duplicating managed version %s for %s"
							: "Overriding managed version %s for %s";

					DOMDocument xmlDocument = diagnosticRequest.getXMLDocument();
					Diagnostic diagnostic = new Diagnostic(
							XMLPositionUtility.createRange(version.getStartTagCloseOffset() + 1,
									version.getEndTagOpenOffset(), xmlDocument),
							String.format(msg, managedVersion, artString), DiagnosticSeverity.Warning,
							xmlDocument.getDocumentURI(), MavenSyntaxErrorCode.OverridingOfManagedPlugin.getCode());

					addDiagnocticData(diagnostic, GROUP_ID_ELT, grpString);
					addDiagnocticData(diagnostic, ARTIFACT_ID_ELT, artString);
					setManagedVersionAttributes(diagnostic, mavenProject, managedPlugin);
					String profile = candidateProfile.get(dep);
					if (profile != null) {
						addDiagnocticData(diagnostic, PROFILE_ELT, profile);
					}
					cancelChecker.checkCanceled();
					diagnostics.add(diagnostic);
				}
			}
		});

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

	@SuppressWarnings("unchecked")
	private static void addDiagnocticData(Diagnostic diagnostic, @Nonnull String key, @Nonnull String value) {
		Map<String, String> dataMap = null;

		// Any Diagnostic data that is not a Map<String, Sting< will be lost
		if (diagnostic.getData() instanceof Map<?, ?> rawMap) {
			try {
				dataMap = (Map<String, String>) rawMap;
			} catch (ClassCastException e) {
			}
		}

		if (dataMap == null) {
			dataMap = new HashMap<String, String>();
			diagnostic.setData(dataMap);
		}
		dataMap.put(key, value);
	}

	public static String getDiagnocticData(Diagnostic diagnostic, @Nonnull String key) {
		if (diagnostic.getData() instanceof Map<?, ?> rawMap) {
			return rawMap.get(key).toString();
		}
		return null;
	}

	private void setManagedVersionAttributes(Diagnostic diagnostic, MavenProject mavenproject,
			InputLocationTracker dependencyOrPlugin) {
		cancelChecker.checkCanceled();
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
						addDiagnocticData(diagnostic, ATTR_MANAGED_VERSION_LOCATION, file.toURI().toString());
						if (lineNumber > -1) {
							addDiagnocticData(diagnostic, ATTR_MANAGED_VERSION_LINE, String.valueOf(lineNumber));
							if (columnNumber > -1) {
								addDiagnocticData(diagnostic, ATTR_MANAGED_VERSION_COLUMN,
										String.valueOf(columnNumber));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Searches for an Ignore-comment node right after the given version element.
	 * 
	 * Node: Originally such ignore comment was supposed to be placed at the same line as the given version element,
	 * but different formatting options can move the comment to the next line, so we'll be searching regardless of in the 
	 * line ending until any element or EOF found
	 * 
	 * @param version
	 * @param ignoreString
	 * @return
	 */
	private boolean lookForIgnoreMarker(@Nonnull DOMElement version, String ignoreString) {
		cancelChecker.checkCanceled();
		DOMNode reg = version;
		while ((reg = reg.getNextSibling()) != null && !(reg instanceof DOMElement)) {
			cancelChecker.checkCanceled();
			if (reg instanceof DOMComment comm) {
				String data = comm.getData();
				if (data != null && data.contains(ignoreString)) {
					return true;
				}
			}
		}
		return false;
	}
}
