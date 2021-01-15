/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.definition;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.Maven;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
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
		
		File currentFolder = new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile();
		
		LocationLink propertyLocation = findMavenPropertyLocation(request);
		if (propertyLocation != null) {
			locations.add(propertyLocation);
			return;
		}
		
		DOMElement element = findInterestingElement(request.getNode());
		if (element != null && element.getLocalName().equals("module")) {
			File subModuleFile = new File(currentFolder, element.getFirstChild().getTextContent() + File.separator + Maven.POMv4);
			if (subModuleFile.isFile()) {
				locations.add(toLocationNoRange(subModuleFile, element));
			}
			return;
		}
		Dependency dependency = MavenParseUtils.parseArtifact(request.getParentElement());
		DOMNode parentNode = DOMUtils.findClosestParentNode(request, "parent");
		if (parentNode != null && parentNode.isElement()) {
			Optional<String> relativePath = DOMUtils.findChildElementText(element, "relativePath");
			if (relativePath.isPresent()) {
				File relativeFile = new File(currentFolder, relativePath.get());
				if (relativeFile.isDirectory()) {
					relativeFile = new File(relativeFile, Maven.POMv4);
				}
				if (relativeFile.isFile()) {
					locations.add(toLocationNoRange(relativeFile, parentNode));
				}
				return;
			} else {
				File relativeFile = new File(currentFolder.getParentFile(), Maven.POMv4);
				if (match(relativeFile, dependency)) {
					locations.add(toLocationNoRange(relativeFile, parentNode));
				}
				return;
			}
		}
		if (dependency != null && element != null) {
			File artifactLocation = getArtifactLocation(dependency, element.getOwnerDocument());
			LocationLink location = toLocationNoRange(artifactLocation, element);
			if (location != null) {
				locations.add(location);
			}
		}
	}

	private LocationLink findMavenPropertyLocation(IDefinitionRequest request) {
		Pair<Range, String> mavenProperty = MavenHoverParticipant.getMavenPropertyInRequest(request);
		if (mavenProperty == null) {
			return null; 
		}
		DOMDocument xmlDocument = request.getXMLDocument();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(xmlDocument);
		if (project == null) {
			return null;
		}
		MavenProject childProj = project;
		while (project != null && project.getProperties().containsKey(mavenProperty.getRight())) {
			childProj = project;
			project = project.getParent();
		}
		
		DOMNode propertyDeclaration = null;
		Predicate<DOMNode> isMavenProperty = (node) -> node.getParentNode().getLocalName().equals("properties");
		
		if (childProj.getFile().toURI().toString().equals(xmlDocument.getDocumentURI())) {
			// Property is defined in the same file as the request
			propertyDeclaration = DOMUtils.findNodesByLocalName(xmlDocument, mavenProperty.getRight()).stream().filter(isMavenProperty).collect(Collectors.toList()).get(0);
		} else {
			DOMDocument propertyDeclaringDocument = org.eclipse.lemminx.utils.DOMUtils.loadDocument(childProj.getFile().toURI().toString(),
					request.getNode().getOwnerDocument().getResolverExtensionManager());
			propertyDeclaration = DOMUtils.findNodesByLocalName(propertyDeclaringDocument, mavenProperty.getRight()).stream().filter(isMavenProperty).collect(Collectors.toList()).get(0);
		}
		
		if (propertyDeclaration == null) {
			return null;
		}
		
		return toLocation(childProj.getFile(), propertyDeclaration, mavenProperty.getLeft());	
	}

	private boolean match(File relativeFile, Dependency dependency) {
		return plugin.getProjectCache().getSnapshotProject(relativeFile)
				.filter(p -> 
					p.getGroupId().equals(dependency.getGroupId()) && //
					p.getArtifactId().equals(dependency.getArtifactId()) && //
					p.getVersion().equals(dependency.getVersion()))
				.isPresent();
	}

	private File getArtifactLocation(Dependency dependency, DOMDocument doc) {
		if (dependency.getGroupId() == null || dependency.getVersion() == null) {
			MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
			if (p != null) {
				final Dependency originalDependency = dependency;
				dependency = p.getDependencies().stream().filter(dep -> 
						(originalDependency.getGroupId() == null || originalDependency.getGroupId().equals(dep.getGroupId()) &&
						(originalDependency.getArtifactId() == null || originalDependency.getArtifactId().equals(dep.getArtifactId())) &&
						(originalDependency.getVersion() == null || originalDependency.getVersion().equals(dep.getVersion())))).findFirst()
					.orElse(dependency);
			}
		}
		File localArtifact = plugin.getLocalRepositorySearcher().findLocalFile(dependency);
		if (localArtifact != null) {
			return localArtifact;
		}
		return null;
	}

	private LocationLink toLocationNoRange(File target, DOMNode originNode) {
		if (target == null) {
			return null;
		}
		Range dumbRange = new Range(new Position(0, 0), new Position(0, 0));
		return new LocationLink(target.toURI().toString(), dumbRange, dumbRange, XMLPositionUtility.createRange(originNode));
	}
	
	private LocationLink toLocation(File target, DOMNode targetNode, Range originRange) {
		Range targetRange = XMLPositionUtility.createRange(targetNode);
		return new LocationLink(target.toURI().toString(), targetRange, targetRange, originRange);
	}

	private DOMElement findInterestingElement(DOMNode node) {
		if (node == null) {
			return null;
		}
		if (!node.isElement()) {
			return findInterestingElement(node.getParentElement());
		}
		DOMElement element = (DOMElement)node;
		switch (node.getLocalName()) {
		case "module":
			return element;
		case "artifactId":
		case "groupId":
		case "version":
		case "relativePath":
			return node.getParentElement();
		default:
			// continue
		}
		if (DOMUtils.findChildElementText(element, "artifactId").isPresent()) {
			return element;
		}
		return null;
	}

}
