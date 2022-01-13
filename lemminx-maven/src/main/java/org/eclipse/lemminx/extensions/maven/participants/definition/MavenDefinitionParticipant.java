/*******************************************************************************
 * Copyright (c) 2020-2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.definition;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.MODULE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROPERTIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.RELATIVE_PATH_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.apache.maven.Maven;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.participants.hover.MavenHoverParticipant;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenParseUtils;
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

		DOMElement element = findInterestingElement(request.getNode());
		if (element != null && MODULE_ELT.equals(element.getLocalName())) {
			File subModuleFile = new File(currentFolder,
					element.getFirstChild().getTextContent() + File.separator + Maven.POMv4);
			if (subModuleFile.isFile()) {
				locations.add(toLocationNoRange(subModuleFile, element));
			}
			return;
		}
		Dependency dependency = MavenParseUtils.parseArtifact(request.getParentElement());
		DOMNode parentNode = DOMUtils.findClosestParentNode(request, PARENT_ELT);
		if (parentNode != null && parentNode.isElement()) {
			// Find in workspace
			if (isWellDefinedDependency(dependency)) {
				File workspaceArtifactLocation = findWorkspaceArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
				if (workspaceArtifactLocation != null) {
					LocationLink location = toLocationNoRange(workspaceArtifactLocation, element);
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
			// Find in workspace
			if (isWellDefinedDependency(dependency)) {
				File workspaceArtifactLocation = findWorkspaceArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
				if (workspaceArtifactLocation != null) {
					locations.add(toLocationNoRange(workspaceArtifactLocation, element));
					return;
				}
			}
			
			File artifactLocation = getArtifactLocation(dependency, element.getOwnerDocument());
			if (artifactLocation != null && artifactLocation.isFile()) {
				locations.add(toLocationNoRange(artifactLocation, element));
			}
		}
	}

	public File findWorkspaceArtifact(String groupId, String artifactId, String version) {
		try {
			ArtifactResult result = plugin.getPlexusContainer().lookup(ArtifactResolver.class).resolveArtifact(plugin.getMavenSession().getRepositorySession(), new ArtifactRequest(new DefaultArtifact(groupId, artifactId, "", version), null, null));
			if (result == null) {
				return null;
			}
			Artifact artifact = result.getArtifact();
			if (artifact == null) {
				return null;
			}
			return artifact.getFile();
		} catch (ArtifactResolutionException | ComponentLookupException e) {
			// can happen
		}
		return null;
	}
	
	private static boolean isWellDefinedDependency(Dependency dependency) {
		try {
			notNullNorBlank(dependency.getGroupId());
			notNullNorBlank(dependency.getArtifactId());
			notNullNorBlank(dependency.getVersion());
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	private static void notNullNorBlank(String str) {
        int c = str != null && str.length() > 0 ? str.charAt( 0 ) : 0;
        if ( ( c < '0' || c > '9' ) && ( c < 'a' || c > 'z' ) )
        {
            Validate.notBlank( str, "Argument can neither be null, empty nor blank" );
        }
    }
	
	private LocationLink findMavenPropertyLocation(IDefinitionRequest request) {
		Map.Entry<Range, String> mavenProperty = MavenHoverParticipant.getMavenPropertyInRequest(request);
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

	private File getArtifactLocation(Dependency dependency, DOMDocument doc) {
		if (dependency.getVersion() != null && dependency.getVersion().contains("$")) {
			dependency = dependency.clone();
			dependency.setVersion(null);
		}
		if (dependency.getGroupId() == null || dependency.getVersion() == null) {
			MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
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
		File localArtifact = plugin.getLocalRepositorySearcher().findLocalFile(dependency);
		if (localArtifact != null) {
			return localArtifact;
		}
		
		return null;
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

	private static DOMElement findInterestingElement(DOMNode node) {
		if (node == null) {
			return null;
		}
		if (!node.isElement()) {
			return findInterestingElement(node.getParentElement());
		}
		DOMElement element = (DOMElement) node;
		switch (node.getLocalName()) {
		case MODULE_ELT:
			return element;
		case ARTIFACT_ID_ELT:
		case GROUP_ID_ELT:
		case VERSION_ELT:
		case RELATIVE_PATH_ELT:
			return node.getParentElement();
		default:
			// continue
		}
		if (DOMUtils.findChildElementText(element, ARTIFACT_ID_ELT).isPresent()) {
			return element;
		}
		return null;
	}

}
