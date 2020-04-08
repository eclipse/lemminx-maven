/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;

public class MavenHoverParticipant implements IHoverParticipant {
	private final MavenProjectCache cache;
	private final RemoteRepositoryIndexSearcher indexSearcher;
	private final MavenPluginManager pluginManager;

	public MavenHoverParticipant(MavenProjectCache cache,  RemoteRepositoryIndexSearcher indexSearcher,  MavenPluginManager pluginManager) {
		this.cache = cache;
		this.indexSearcher = indexSearcher;
		this.pluginManager = pluginManager;
	}

	@Override
	public String onAttributeName(IHoverRequest request) throws Exception {
		return null;
	}

	@Override
	public String onAttributeValue(IHoverRequest request) throws Exception {
		return null;
	}

	@Override
	public String onTag(IHoverRequest request) throws Exception {
		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();
		DOMElement grandParent = parent.getParentElement();

		if (tag.getLocalName() == null) {
			return null;
		}

		boolean isPlugin = "plugin".equals(parent.getLocalName())
				|| (grandParent != null && "plugin".equals(grandParent.getLocalName()));
		boolean isParentDeclaration = "parent".equals(parent.getLocalName())
				|| (grandParent != null && "parent".equals(grandParent.getLocalName()));

		switch (parent.getLocalName()) {
		case "configuration":
			return collectPuginConfiguration(request);
		case "goals":
			return collectGoals(request);
		default:
			break;
		}

		switch (tag.getLocalName()) {
		case "artifactId":
			if (isParentDeclaration) {
				return null;
			} else {
				return collectArtifactDescription(request, isPlugin);
			}
		default:
			break;
		}

		return null;
	}

	private String collectArtifactDescription(IHoverRequest request, boolean isPlugin) {
		Collection<String> possibleHovers = Collections.synchronizedSet(new LinkedHashSet<>());
		DOMNode node = request.getNode();
		DOMDocument doc = request.getXMLDocument();

		List<String> remoteArtifactRepositories = Collections
				.singletonList(RemoteRepositoryIndexSearcher.CENTRAL_REPO.getUrl());
		Dependency artifactToSearch = MavenParseUtils.parseArtifact(node);
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		if (project != null) {
			remoteArtifactRepositories = project.getRemoteArtifactRepositories().stream()
					.map(ArtifactRepository::getUrl).collect(Collectors.toList());
		}

		try {
			CompletableFuture.allOf(remoteArtifactRepositories.stream().map(repository -> {
				final String updatingItem = "Updating index for " + repository;
				possibleHovers.add(updatingItem);

				return indexSearcher.getIndexingContext(URI.create(repository)).thenAccept(index -> {
					if (isPlugin) {
						// TODO: make a new function that gets only the exact artifact ID match, or just
						// take the first thing given
						indexSearcher.getPluginArtifactIds(artifactToSearch, index).stream()
								.map(ArtifactInfo::getDescription)
								.filter(Objects::nonNull)
								.forEach(possibleHovers::add);
					} else {
						indexSearcher.getArtifactIds(artifactToSearch, index).stream()
								.map(ArtifactInfo::getDescription)
								.filter(Objects::nonNull)
								.forEach(possibleHovers::add);
					}
				}).whenComplete((ok, error) -> possibleHovers.remove(updatingItem));

			}).toArray(CompletableFuture<?>[]::new)).get(2, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException exception) {
			exception.printStackTrace();
		} catch (TimeoutException e) {
			// nothing to log, some work still pending
		}
		if (possibleHovers.isEmpty()) {
			return null;
		}
		return possibleHovers.iterator().next();
	}

	private String collectGoals(IPositionRequest request) {
		DOMNode node = request.getNode();
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, cache, pluginManager);
		if (pluginDescriptor != null ) {
			for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
				if (!node.getChild(0).getNodeValue().trim().isEmpty() && node.hasChildNodes()
						&& node.getChild(0).getNodeValue().equals(mojo.getGoal())) {
					return mojo.getDescription();
				}
			}			
		}
		return null;
	}

	private String collectPuginConfiguration(IPositionRequest request) {
		List<Parameter> parameters = MavenPluginUtils.collectPluginConfigurationParameters(request, cache, pluginManager);
		DOMNode node = request.getNode();
		
		for (Parameter parameter : parameters) {
			if (node.getLocalName().equals(parameter.getName())) {
				return MavenPluginUtils.getMarkupDescription(parameter).getValue();
			}
		}
		return null;
	}
	
	private Hover toHover(String description) {
		Hover hover = new Hover();
		hover.setContents(new MarkupContent("plaintext", description));
		return hover;
	}

	@Override
	public String onText(IHoverRequest request) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	
}
