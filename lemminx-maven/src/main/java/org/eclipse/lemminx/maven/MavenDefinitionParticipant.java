/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.apache.maven.Maven;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenDefinitionParticipant implements IDefinitionParticipant {

	private LocalRepositorySearcher localRepositorySearcher;
	private MavenProjectCache cache;

	public MavenDefinitionParticipant(MavenProjectCache cache, LocalRepositorySearcher localRepositorySearcher) {
		this.cache = cache;
		this.localRepositorySearcher = localRepositorySearcher;
	}
	
	@Override
	public void findDefinition(IDefinitionRequest request, List<LocationLink> locations, CancelChecker cancelChecker) {
		File currentFolder = new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile();
		DOMElement element = findInterestingElement(request.getNode());
		if (element.getLocalName().equals("module")) {
			File subModuleFile = new File(currentFolder, element.getFirstChild().getTextContent() + "/" + Maven.POMv4);
			if (subModuleFile.isFile()) {
				locations.add(toLocation(subModuleFile, element));
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
					locations.add(toLocation(relativeFile, (DOMElement)parentNode));
				}
				return;
			} else {
				File relativeFile = new File(currentFolder.getParentFile(), Maven.POMv4);
				if (match(relativeFile, dependency)) {
					locations.add(toLocation(relativeFile, (DOMElement)parentNode));
				}
				return;
			}
		}
		if (dependency != null) {
			File artifactLocation = getArtifactLocation(dependency);
			LocationLink location = toLocation(artifactLocation, element);
			if (location != null) {
				locations.add(location);
			}
			return;
		}
	}

	private boolean match(File relativeFile, Dependency dependency) {
		MavenProject p = cache.getSnapshotProject(relativeFile).get();
		return p != null &&
				p.getGroupId().equals(dependency.getGroupId()) &&
				p.getArtifactId().equals(dependency.getArtifactId()) &&
				p.getVersion().equals(dependency.getVersion());
	}

	private File getArtifactLocation(Dependency dependency) {
		File localArtifact = localRepositorySearcher.findLocalFile(dependency);
		if (localArtifact != null) {
			return localArtifact;
		}
		return null;
	}

	private LocationLink toLocation(File target, DOMElement element) {
		Range dumbRange = new Range(new Position(0, 0), new Position(0, 0));
		LocationLink link = new LocationLink(target.toURI().toString(), dumbRange, dumbRange, XMLPositionUtility.createRange(element));
		return link;
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
		}
		if (DOMUtils.findChildElementText(element, "artifactId").isPresent()) {
			return element;
		}
		return null;
	}

}
