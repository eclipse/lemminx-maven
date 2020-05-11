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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lemminx.services.extensions.IPositionRequest;

public class MavenHoverParticipant implements IHoverParticipant {
	private final MavenProjectCache cache;
	private final RemoteRepositoryIndexSearcher indexSearcher;
	private final MavenPluginManager pluginManager;
	private final LocalRepositorySearcher localRepoSearcher;
	private final RepositorySystemSession repoSession;

	public MavenHoverParticipant(MavenProjectCache cache, LocalRepositorySearcher localRepoSearcher, RemoteRepositoryIndexSearcher indexSearcher, RepositorySystemSession repoSession, MavenPluginManager pluginManager) {
		this.cache = cache;
		this.localRepoSearcher = localRepoSearcher;
		this.indexSearcher = indexSearcher;
		this.repoSession = repoSession;
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
		if (!MavenPlugin.match(request.getXMLDocument())) {
			  return null;
		}
		
		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();
		DOMElement grandParent = parent.getParentElement();

		if (tag.getLocalName() == null) {
			return null;
		}

		switch (parent.getLocalName()) {
		case "configuration":
			return collectPuginConfiguration(request);
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
			ModelBuilder builder = cache.getPlexusContainer().lookup(ModelBuilder.class);
			Optional<String> localDescription = localRepoSearcher.getLocalArtifactsLastVersion().stream()
				.filter(gav ->
					(artifactToSearch.getGroupId() == null || artifactToSearch.getGroupId().equals(gav.getGroupId())) &&
					(artifactToSearch.getArtifactId() == null || artifactToSearch.getArtifactId().equals(gav.getArtifactId())) &&
					(artifactToSearch.getVersion() == null || artifactToSearch.getVersion().equals(gav.getVersion())))
				.sorted(Comparator.comparing((Gav gav) -> new DefaultArtifactVersion(gav.getVersion())).reversed())
				.findFirst()
				.map(localRepoSearcher::findLocalFile)
				.map(file -> builder.buildRawModel(file, ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL, false).get())
				.map(model -> model.getName() + "\n\n" + model.getDescription())
				.map(message -> (message.length() > 2 ? message : null));
			if (localDescription.isPresent()) {
				return localDescription.get();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		try {
			CompletableFuture.allOf(remoteArtifactRepositories.stream().map(repository -> {
				final String updatingItem = "Updating index for " + repository;
				possibleHovers.add(updatingItem);

				return indexSearcher.getIndexingContext(URI.create(repository)).thenAccept(index -> {
					if (isPlugin) {
						// TODO: make a new function that gets only the exact artifact ID match, or just
						// take the first thing given
						indexSearcher.getPluginArtifacts(artifactToSearch, index).stream()
								.map(ArtifactInfo::getDescription)
								.filter(Objects::nonNull)
								.forEach(possibleHovers::add);
					} else {
						indexSearcher.getArtifacts(artifactToSearch, index).stream()
								.map(ArtifactInfo::getDescription)
								.filter(Objects::nonNull)
								.forEach(possibleHovers::add);
					}
				}).whenComplete((ok, error) -> possibleHovers.remove(updatingItem));

			}).toArray(CompletableFuture<?>[]::new)).get(10, TimeUnit.SECONDS);
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

	private String collectGoal(IPositionRequest request) {
		DOMNode node = request.getNode();
		PluginDescriptor pluginDescriptor;
		try {
			pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, cache, repoSession, pluginManager);
			for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
				if (!node.getNodeValue().trim().isEmpty() && node.getNodeValue().equals(mojo.getGoal())) {
					return mojo.getDescription();
				}
			}
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String collectPuginConfiguration(IPositionRequest request) {
		List<Parameter> parameters;
		try {
			parameters = MavenPluginUtils.collectPluginConfigurationParameters(request, cache, repoSession, pluginManager);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			e.printStackTrace();
			return null;
		}
		DOMNode node = request.getNode();
		
		for (Parameter parameter : parameters) {
			if (node.getLocalName().equals(parameter.getName())) {
				return MavenPluginUtils.getMarkupDescription(parameter).getValue();
			}
		}
		return null;
	}

	@Override
	public String onText(IHoverRequest request) throws Exception {
		if (!MavenPlugin.match(request.getXMLDocument())) {
			  return null;
		}
		
		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();
		DOMElement grandParent = parent.getParentElement();
		
		boolean isPlugin = "plugin".equals(parent.getLocalName())
				|| (grandParent != null && "plugin".equals(grandParent.getLocalName()));
		boolean isParentDeclaration = "parent".equals(parent.getLocalName())
				|| (grandParent != null && "parent".equals(grandParent.getLocalName()));
		
		
		String mavenProperty = getMavenPropertyInHover(request);
		if (mavenProperty != null) {
			return collectProperty(request, mavenProperty);
		}
		
		switch (parent.getLocalName()) {
		case "artifactId":
			if (isParentDeclaration) {
				return null;
			} else {
				return collectArtifactDescription(request, isPlugin);
			}
		case "goal":
			return collectGoal(request);
		default:
			break;
		}
		
		return null;
	}


	public String getMavenPropertyInHover(IPositionRequest request) {
		DOMNode tag = request.getNode();
		String tagText = tag.getNodeValue();

		int hoverLocation = request.getOffset();
		int propertyOffset = request.getNode().getStart();
		int beforeHover = hoverLocation - propertyOffset;

		String beforeHoverText = tagText.substring(0, beforeHover);
		String afterHoverText = tagText.substring(beforeHover);

		int indexOpen = beforeHoverText.lastIndexOf("${");
		int indexCloseBefore = beforeHoverText.lastIndexOf('}');
		int indexCloseAfter = afterHoverText.indexOf('}');
		if (indexOpen > indexCloseBefore) {
			return tagText.substring(indexOpen + 2, indexCloseAfter + beforeHover);
		}
		return null;
	}

	private String collectProperty(IPositionRequest request, String property) {
		DOMDocument doc = request.getXMLDocument();
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		if (project != null) {
			Map<String, String> allProps = getMavenProjectProperties(project);

			for (Entry<String, String> prop : allProps.entrySet()) {
				String mavenProperty = prop.getKey();
				if (property.equals(mavenProperty)) {
					return "Property: " + mavenProperty + MavenPluginUtils.LINE_BREAK + "Value: " + prop.getValue()
							+ MavenPluginUtils.LINE_BREAK;
				}
			}
		}
		return null;
	}

	// TODO: Move this function to a utility class
	public static Map<String, String> getMavenProjectProperties(MavenProject project) {
		Map<String, String> allProps = new HashMap<>();
		if (project.getProperties() != null) {
			for (Entry<Object, Object> prop : project.getProperties().entrySet()) {
				allProps.put((String) prop.getKey(), (String) prop.getValue());
			}
		}
		allProps.put("basedir", project == null ? "unknown" : project.getBasedir().toString());
		allProps.put("project.basedir", project == null ? "unknown" : project.getBasedir().toString());
		allProps.put("project.version", project == null ? "unknown" : project.getVersion());
		allProps.put("project.groupId", project == null ? "unknown" : project.getGroupId());
		allProps.put("project.artifactId", project == null ? "unknown" : project.getArtifactId());
		allProps.put("project.name", project == null ? "unknown" : project.getName());
		allProps.put("project.build.directory", project.getBuild() == null ? "unknown" : project.getBuild().getDirectory());
		allProps.put("project.build.outputDirectory",
				project.getBuild() == null ? "unknown" : project.getBuild().getOutputDirectory());
		return allProps;
	}
	
}
