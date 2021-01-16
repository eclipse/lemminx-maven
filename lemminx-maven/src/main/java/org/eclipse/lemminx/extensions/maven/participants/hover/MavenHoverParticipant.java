/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.hover;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CONFIGURATION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;

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
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.properties.internal.EnvironmentUtils;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MojoParameter;
import org.eclipse.lemminx.extensions.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MarkdownUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenParseUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lemminx.extensions.maven.utils.PlexusConfigHelper;
import org.eclipse.lemminx.services.extensions.HoverParticipantAdapter;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;

public class MavenHoverParticipant extends HoverParticipantAdapter {

	private static final Logger LOGGER = Logger.getLogger(MavenHoverParticipant.class.getName());
	private static Properties environmentProperties;
	private final MavenLemminxExtension plugin;

	public MavenHoverParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
		environmentProperties = new Properties();
		EnvironmentUtils.addEnvVars(environmentProperties);
	}

	@Override
	public Hover onTag(IHoverRequest request) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}

		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();

		if (tag.getLocalName() == null) {
			return null;
		}

		if (DOMUtils.isADescendantOf(tag, CONFIGURATION_ELT)) {
			return collectPluginConfiguration(request);
		}

		// TODO: Get rid of this?
		switch (parent.getLocalName()) {
		case CONFIGURATION_ELT:
			return collectPluginConfiguration(request);
		default:
			break;
		}

		return null;
	}

	private Hover collectArtifactDescription(IHoverRequest request, boolean isPlugin) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		Collection<String> possibleHovers = Collections.synchronizedSet(new LinkedHashSet<>());
		DOMNode node = request.getNode();
		DOMDocument doc = request.getXMLDocument();

		Dependency artifactToSearch = MavenParseUtils.parseArtifact(node);
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
		List<String> remoteArtifactRepositories = project == null ? //
				Collections.singletonList(RemoteRepositoryIndexSearcher.CENTRAL_REPO.getUrl()) : //
				project.getRemoteArtifactRepositories().stream().map(ArtifactRepository::getUrl)
						.collect(Collectors.toList());

		try {
			ModelBuilder builder = plugin.getProjectCache().getPlexusContainer().lookup(ModelBuilder.class);
			Optional<String> localDescription = plugin.getLocalRepositorySearcher()
					.getLocalArtifactsLastVersion().stream()
					.filter(gav -> (artifactToSearch.getGroupId() == null
							|| artifactToSearch.getGroupId().equals(gav.getGroupId()))
							&& (artifactToSearch.getArtifactId() == null
									|| artifactToSearch.getArtifactId().equals(gav.getArtifactId()))
							&& (artifactToSearch.getVersion() == null
									|| artifactToSearch.getVersion().equals(gav.getVersion())))
					.sorted(Comparator.comparing((Gav gav) -> new DefaultArtifactVersion(gav.getVersion())).reversed())
					.findFirst().map(plugin.getLocalRepositorySearcher()::findLocalFile).map(file -> builder
							.buildRawModel(file, ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL, false).get())
					.map(model -> {
						UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold
								: UnaryOperator.identity();
						String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
						String message = "";

						if (model.getName() != null) {
							message += toBold.apply(model.getName());
						}

						if (model.getDescription() != null) {
							message += lineBreak + model.getDescription();
						}

						return message;
					}).map(message -> (message.length() > 2 ? message : null));
			if (localDescription.isPresent()) {
				return new Hover(new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT,
						localDescription.get()));
			}
		} catch (Exception e1) {
			LOGGER.log(Level.SEVERE, e1.getCause().toString(), e1);
		}
		plugin.getIndexSearcher().ifPresent(indexSearcher -> {
			try {
				CompletableFuture.allOf(remoteArtifactRepositories.stream().map(repository -> {
					final String updatingItem = "Updating index for " + repository;
					possibleHovers.add(updatingItem);

					return indexSearcher.getIndexingContext(URI.create(repository)).thenAccept(index -> {
						if (isPlugin) {
							// TODO: make a new function that gets only the exact artifact ID match, or just
							// take the first thing given
							indexSearcher.getPluginArtifacts(artifactToSearch, index).stream()
									.map(ArtifactInfo::getDescription).filter(Objects::nonNull)
									.forEach(possibleHovers::add);
						} else {
							indexSearcher.getArtifacts(artifactToSearch, index).stream()
									.map(ArtifactInfo::getDescription).filter(Objects::nonNull)
									.forEach(possibleHovers::add);
						}
					}).whenComplete((ok, error) -> possibleHovers.remove(updatingItem));

				}).toArray(CompletableFuture<?>[]::new)).get(10, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException exception) {
				LOGGER.log(Level.SEVERE, exception.getCause().toString(), exception);
			} catch (TimeoutException e) {
				// nothing to log, some work still pending
			}
		});
		if (possibleHovers.isEmpty()) {
			return null;
		}

		return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, possibleHovers.iterator().next()));
	}

	private Hover collectGoal(IPositionRequest request) {
		DOMNode node = request.getNode();
		PluginDescriptor pluginDescriptor;
		try {
			pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
			for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
				if (!node.getNodeValue().trim().isEmpty() && node.getNodeValue().equals(mojo.getGoal())) {
					return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, mojo.getDescription()));
				}
			}
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private Hover collectPluginConfiguration(IHoverRequest request) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		Set<MojoParameter> parameters;
		try {
			parameters = MavenPluginUtils.collectPluginConfigurationMojoParameters(request, plugin);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return null;
		}
		DOMNode node = request.getNode();
		String parentName = node.getParentNode().getLocalName();
		if (parentName != null && parentName.equals(CONFIGURATION_ELT)) {
			// The configuration element being hovered is at the top level
			for (MojoParameter parameter : parameters) {
				if (node.getLocalName().equals(parameter.getName())) {
					return new Hover(MavenPluginUtils.getMarkupDescription(parameter, null, supportsMarkdown));
				}
			}
		}

		// Nested case: node is a grand child of configuration

		// Get the node's ancestor which is a child of configuration
		DOMNode parentParameterNode = DOMUtils.findAncestorThatIsAChildOf(request, CONFIGURATION_ELT);
		if (parentParameterNode != null) {
			List<MojoParameter> parentParameters = parameters.stream()
					.filter(mojoParameter -> mojoParameter.getName().equals(parentParameterNode.getLocalName()))
					.collect(Collectors.toList());
			if (!parentParameters.isEmpty()) {
				MojoParameter parentParameter = parentParameters.get(0);

				if (parentParameter.getNestedParameters().size() == 1) {
					// The parent parameter must be a collection of a type
					MojoParameter nestedParameter = parentParameter.getNestedParameters().get(0);
					Class<?> potentialInlineType = PlexusConfigHelper.getRawType(nestedParameter.getParamType());
					if (potentialInlineType != null && PlexusConfigHelper.isInline(potentialInlineType)) {
						return new Hover(MavenPluginUtils.getMarkupDescription(nestedParameter, parentParameter,
								supportsMarkdown));
					}
				}

				// Get all deeply nested parameters
				List<MojoParameter> nestedParameters = parentParameter.getFlattenedNestedParameters();
				nestedParameters.add(parentParameter);
				for (MojoParameter parameter : nestedParameters) {
					if (node.getLocalName().equals(parameter.getName())) {
						return new Hover(
								MavenPluginUtils.getMarkupDescription(parameter, parentParameter, supportsMarkdown));
					}
				}
			}
		}
		return null;
	}

	@Override
	public Hover onText(IHoverRequest request) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}

		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();
		DOMElement grandParent = parent.getParentElement();

		boolean isPlugin = PLUGIN_ELT.equals(parent.getLocalName())
				|| (grandParent != null && PLUGIN_ELT.equals(grandParent.getLocalName()));
		boolean isParentDeclaration = PARENT_ELT.equals(parent.getLocalName())
				|| (grandParent != null && PARENT_ELT.equals(grandParent.getLocalName()));

		Pair<Range, String> mavenProperty = getMavenPropertyInRequest(request);
		if (mavenProperty != null) {
			return collectProperty(request, mavenProperty);
		}

		switch (parent.getLocalName()) {
		case ARTIFACT_ID_ELT:
			if (isParentDeclaration) {
				return null;
			} else {
				return collectArtifactDescription(request, isPlugin);
			}
		case GOAL_ELT:
			return collectGoal(request);
		default:
			break;
		}

		return null;
	}

	public static Pair<Range, String> getMavenPropertyInRequest(IPositionRequest request) {
		DOMNode tag = request.getNode();
		String tagText = tag.getNodeValue();
		if (tagText == null) {
			return null;
		}

		int hoverLocation = request.getOffset();
		int propertyOffset = request.getNode().getStart();
		int beforeHover = hoverLocation - propertyOffset;

		String beforeHoverText = tagText.substring(0, beforeHover);
		String afterHoverText = tagText.substring(beforeHover);

		int indexOpen = beforeHoverText.lastIndexOf("${");
		int indexCloseBefore = beforeHoverText.lastIndexOf('}');
		int indexCloseAfter = afterHoverText.indexOf('}');
		if (indexOpen > indexCloseBefore) {

			String propertyText = tagText.substring(indexOpen + 2, indexCloseAfter + beforeHover);
			int textStart = request.getNode().getStart();
			Range propertyRange = XMLPositionUtility.createRange(textStart + indexOpen + 2,
					textStart + indexCloseAfter - 1, request.getXMLDocument());
			return new ImmutablePair<Range, String>(propertyRange, propertyText);
		}
		return null;
	}

	private Hover collectProperty(IHoverRequest request, Pair<Range, String> property) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		DOMDocument doc = request.getXMLDocument();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
		if (project != null) {
			Map<String, String> allProps = getMavenProjectProperties(project);
			UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();

			for (Entry<String, String> prop : allProps.entrySet()) {
				String mavenProperty = prop.getKey();
				if (property.getRight().equals(mavenProperty)) {
					String message = toBold.apply("Property: ") + mavenProperty + lineBreak + toBold.apply("Value: ")
							+ prop.getValue() + lineBreak;

					Hover hover = new Hover(
							new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, message));
					hover.setRange(property.getLeft());
					return hover;
				}
			}
		}
		return null;
	}

	// TODO: Move this function to a utility class
	public static Map<String, String> getMavenProjectProperties(MavenProject project) {
		Map<String, String> allProps = new HashMap<>();
		Properties projectProperties = project.getProperties();
		projectProperties.putAll(environmentProperties);

		if (project.getProperties() != null) {
			for (Entry<Object, Object> prop : projectProperties.entrySet()) {
				allProps.put((String) prop.getKey(), (String) prop.getValue());
			}
		}
		allProps.put("basedir", project == null ? "unknown" : project.getBasedir().toString());
		allProps.put("project.basedir", project == null ? "unknown" : project.getBasedir().toString());
		allProps.put("project.version", project == null ? "unknown" : project.getVersion());
		allProps.put("project.groupId", project == null ? "unknown" : project.getGroupId());
		allProps.put("project.artifactId", project == null ? "unknown" : project.getArtifactId());
		allProps.put("project.name", project == null ? "unknown" : project.getName());
		allProps.put("project.build.directory",
				project.getBuild() == null ? "unknown" : project.getBuild().getDirectory());
		allProps.put("project.build.outputDirectory",
				project.getBuild() == null ? "unknown" : project.getBuild().getOutputDirectory());

		return allProps;
	}

}
