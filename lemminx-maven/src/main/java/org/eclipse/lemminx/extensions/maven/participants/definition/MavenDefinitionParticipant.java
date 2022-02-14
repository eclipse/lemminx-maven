/*******************************************************************************
 * Copyright (c) 2020-2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.definition;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.MODULE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROPERTIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.RELATIVE_PATH_ELT;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.Maven;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenDefinitionParticipant implements IDefinitionParticipant {

	private final MavenLemminxExtension plugin;

	public MavenDefinitionParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public void findDefinition(IDefinitionRequest request, List<LocationLink> locations, CancelChecker cancelChecker) {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return;
		}
		// make sure model is resolved and up-to-date
		File currentFolder = new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile();

		LocationLink propertyLocation = findMavenPropertyLocation(request);
		if (propertyLocation != null) {
			locations.add(propertyLocation);
			return;
		}

		DOMElement element = ParticipantUtils.findInterestingElement(request.getNode());
		if (element != null && MODULE_ELT.equals(element.getLocalName())) {
			File subModuleFile = new File(currentFolder,
					element.getFirstChild().getTextContent() + File.separator + Maven.POMv4);
			if (subModuleFile.isFile()) {
				locations.add(toLocationNoRange(subModuleFile, element));
			}
			return;
		}
		
		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(element.getOwnerDocument());
		Dependency dependency = ParticipantUtils.getArtifactToSearch(p, request);

		DOMNode parentNode = DOMUtils.findClosestParentNode(request, PARENT_ELT);
		if (parentNode != null && parentNode.isElement()) {
			// Find in workspace
			if (ParticipantUtils.isWellDefinedDependency(dependency)) {
				Artifact artifact = ParticipantUtils.findWorkspaceArtifact(plugin, request, dependency);
				if (artifact != null && artifact.getFile() != null) {
					LocationLink location = toLocationNoRange(artifact.getFile(), element);
					if (location != null) {
						locations.add(location);
						return;
					}
				}
			}
			
			Optional<String> relativePath = DOMUtils.findChildElementText(element, RELATIVE_PATH_ELT);
			if (relativePath.isPresent()) {
				File relativeFile = new File(currentFolder, relativePath.get());
				if (relativeFile.isDirectory()) {
					relativeFile = new File(relativeFile, Maven.POMv4);
				}
				if (relativeFile.isFile()) {
					locations.add(toLocationNoRange(relativeFile, parentNode));
					return;
				}
			} else {
				File relativeFile = new File(currentFolder.getParentFile(), Maven.POMv4);
				if (match(relativeFile, dependency)) {
					locations.add(toLocationNoRange(relativeFile, parentNode));
					return;
				} else {
					// those next lines may actually be more generic and suit parent definition in any case
					MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
					if (project != null && project.getParentFile() != null) {
						locations.add(toLocationNoRange(project.getParentFile(), parentNode));
						return;
					}
				}
			}
		}
		if (dependency != null && element != null) {
			if (!ParticipantUtils.isWellDefinedDependency(dependency)) {
				if (ParticipantUtils.isPlugin(element)) {
					try {
						PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
						if (pluginDescriptor != null) {
							dependency.setGroupId(pluginDescriptor.getGroupId());
							dependency.setArtifactId(pluginDescriptor.getArtifactId());
							dependency.setVersion(pluginDescriptor.getVersion());
						}					
					} catch (PluginResolutionException | PluginDescriptorParsingException
							| InvalidPluginDescriptorException e) {
						// Ignore
					}
				} else if (ParticipantUtils.isDependency(element)) {
					if (dependency.getGroupId() == null || dependency.getVersion() == null) {
						if (p != null) {
							final Dependency originalDependency = dependency;
							dependency = p.getDependencies().stream()
									.filter(dep -> (originalDependency.getGroupId() == null
											|| originalDependency.getGroupId().equals(dep.getGroupId())
													&& (originalDependency.getArtifactId() == null
															|| originalDependency.getArtifactId().equals(dep.getArtifactId()))
													&& (originalDependency.getVersion() == null
															|| originalDependency.getVersion().equals(dep.getVersion()))))
									.findFirst().orElse(dependency);
						}
					}
				}
			}

			// Find in workspace
			if (ParticipantUtils.isWellDefinedDependency(dependency)) {
				Artifact artifact = ParticipantUtils.findWorkspaceArtifact(plugin, request, dependency);
				if (artifact != null && artifact.getFile() != null) {
					locations.add(toLocationNoRange(artifact.getFile(), element));
					return;
				}
			}
			
			File localArtifactLocation = plugin.getLocalRepositorySearcher().findLocalFile(dependency);
			if (localArtifactLocation != null && localArtifactLocation.isFile()) {
				locations.add(toLocationNoRange(localArtifactLocation, element));
				return;
			}
		}
	}
	
	private LocationLink findMavenPropertyLocation(IDefinitionRequest request) {
		Map.Entry<Range, String> mavenProperty = ParticipantUtils.getMavenPropertyInRequest(request);
		if (mavenProperty == null) {
			return null;
		}
		DOMDocument xmlDocument = request.getXMLDocument();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(xmlDocument);
		if (project == null) {
			return null;
		}
		MavenProject childProj = project;
		while (project != null && project.getProperties().containsKey(mavenProperty.getValue())) {
			childProj = project;
			project = project.getParent();
		}

		DOMNode propertyDeclaration = null;
		Predicate<DOMNode> isMavenProperty = (node) -> PROPERTIES_ELT.equals(node.getParentNode().getLocalName());

		if (childProj.getFile().toURI().toString().equals(xmlDocument.getDocumentURI())) {
			// Property is defined in the same file as the request
			propertyDeclaration = DOMUtils.findNodesByLocalName(xmlDocument, mavenProperty.getValue()).stream()
					.filter(isMavenProperty).collect(Collectors.toList()).get(0);
		} else {
			DOMDocument propertyDeclaringDocument = org.eclipse.lemminx.utils.DOMUtils.loadDocument(
					childProj.getFile().toURI().toString(),
					request.getNode().getOwnerDocument().getResolverExtensionManager());
			propertyDeclaration = DOMUtils.findNodesByLocalName(propertyDeclaringDocument, mavenProperty.getValue())
					.stream().filter(isMavenProperty).collect(Collectors.toList()).get(0);
		}

		if (propertyDeclaration == null) {
			return null;
		}

		return toLocation(childProj.getFile(), propertyDeclaration, mavenProperty.getKey());
	}

	private boolean match(File relativeFile, Dependency dependency) {
		return plugin.getProjectCache().getSnapshotProject(relativeFile)
				.filter(p -> p.getGroupId().equals(dependency.getGroupId()) && //
						p.getArtifactId().equals(dependency.getArtifactId()) && //
						p.getVersion().equals(dependency.getVersion()))
				.isPresent();
	}

	private static LocationLink toLocationNoRange(File target, DOMNode originNode) {
		if (target == null) {
			return null;
		}
		Range dumbRange = new Range(new Position(0, 0), new Position(0, 0));
		return new LocationLink(target.toURI().toString(), dumbRange, dumbRange,
				XMLPositionUtility.createRange(originNode));
	}

	private static LocationLink toLocation(File target, DOMNode targetNode, Range originRange) {
		Range targetRange = XMLPositionUtility.createRange(targetNode);
		return new LocationLink(target.toURI().toString(), targetRange, targetRange, originRange);
	}
}
