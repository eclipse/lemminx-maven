/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.EXECUTION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOALS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MojoParameter;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

public class MavenPluginUtils {

	private static final Logger LOGGER = Logger.getLogger(MavenPluginUtils.class.getName());

	private MavenPluginUtils() {
		// Utility class, not meant to be instantiated
	}

	public static MarkupContent getMarkupDescription(MojoParameter parameter, MojoParameter parentParameter,
			boolean supportsMarkdown) {
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);

		final String fromParent = toBold.apply("From parent configuration element:") + lineBreak;
		String type = parameter.type != null ? parameter.type : "";
		String expression = parameter.getExpression() != null ? parameter.getExpression() : "(none)";
		String defaultValue = parameter.getDefaultValue() != null ? parameter.getDefaultValue() : "(unset)";
		String description = parameter.getDescription() != null ? parameter.getDescription() : "";

		if (defaultValue.isEmpty() && parentParameter != null && parentParameter.getDefaultValue() != null) {
			defaultValue = fromParent + parentParameter.getDefaultValue();
		}
		if (description.isEmpty() && parentParameter != null) {
			description = fromParent + parentParameter.getDescription();
		}

		description = MarkdownUtils.htmlXMLToMarkdown(description);

		String markdownDescription = description + lineBreak +  toBold.apply("Required: ") + parameter.isRequired() + 
				lineBreak + toBold.apply("Type: ") + type + lineBreak + toBold.apply("Expression: ") + expression + 
				lineBreak + toBold.apply("Default Value: ") + defaultValue;

		return new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, markdownDescription);
	}

	public static Set<Parameter> collectPluginConfigurationParameters(IPositionRequest request,
			MavenLemminxExtension plugin)
			throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
		if (pluginDescriptor == null) {
			return Collections.emptySet();
		}
		List<MojoDescriptor> mojosToConsiderList = pluginDescriptor.getMojos();
		DOMNode executionElementDomNode = DOMUtils.findClosestParentNode(request, "execution");
		if (executionElementDomNode != null) {
			Set<String> interestingMojos = executionElementDomNode.getChildren().stream()
					.filter(node -> GOALS_ELT.equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(node -> GOAL_ELT.equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(DOMNode::isText).map(DOMNode::getTextContent).collect(Collectors.toSet());
			mojosToConsiderList = mojosToConsiderList.stream().filter(mojo -> interestingMojos.contains(mojo.getGoal()))
					.collect(Collectors.toList());
		}
		Set<Parameter> parameters = mojosToConsiderList.stream().flatMap(mojo -> mojo.getParameters().stream())
				.collect(Collectors.toSet());
		return parameters;
	}

	public static Set<MojoParameter> collectPluginConfigurationMojoParameters(IPositionRequest request,
			MavenLemminxExtension plugin)
			throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
		if (pluginDescriptor == null) {
			return Collections.emptySet();
		}
		List<MojoDescriptor> mojosToConsiderList = pluginDescriptor.getMojos();
		DOMNode executionElementDomNode = DOMUtils.findClosestParentNode(request, EXECUTION_ELT);
		if (executionElementDomNode != null) {
			Set<String> interestingMojos = executionElementDomNode.getChildren().stream()
					.filter(node -> GOALS_ELT.equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(node -> GOAL_ELT.equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(DOMNode::isText).map(DOMNode::getTextContent).collect(Collectors.toSet());
			mojosToConsiderList = mojosToConsiderList.stream().filter(mojo -> interestingMojos.contains(mojo.getGoal()))
					.collect(Collectors.toList());
		}
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return Collections.emptySet();
		}
		plugin.getMavenSession().setProjects(Collections.singletonList(project));
		Set<MojoParameter> mojoParams = mojosToConsiderList.stream().flatMap(mojo -> PlexusConfigHelper
				.loadMojoParameters(pluginDescriptor, mojo, plugin.getMavenSession(), plugin.getBuildPluginManager())
				.stream()).collect(Collectors.toSet());
		return mojoParams;
	}

	public static RemoteRepository toRemoteRepo(Repository modelRepo) {
		Builder builder = new RemoteRepository.Builder(modelRepo.getId(), modelRepo.getLayout(), modelRepo.getUrl());
		return builder.build();
	}

	public static PluginDescriptor getContainingPluginDescriptor(IPositionRequest request,
			MavenLemminxExtension lemminxMavenPlugin)
			throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		MavenProject project = lemminxMavenPlugin.getProjectCache()
				.getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return null;
		}
		DOMNode pluginNode = DOMUtils.findClosestParentNode(request, PLUGIN_ELT);
		if (pluginNode == null) {
			return null;
		}
		Optional<String> groupId = DOMUtils.findChildElementText(pluginNode, GROUP_ID_ELT);
		Optional<String> artifactId = DOMUtils.findChildElementText(pluginNode, ARTIFACT_ID_ELT);
		String pluginKey = "";
		if (groupId.isPresent()) {
			pluginKey += groupId.get();
			pluginKey += ':';
		}
		if (artifactId.isPresent()) {
			pluginKey += artifactId.get();
		}
		Plugin plugin = findPluginInProject(project, pluginKey, artifactId);

		if (plugin == null) {
			DOMNode profileNode = DOMUtils.findClosestParentNode(request, "profile");
			if (profileNode != null) {
				project = profileNode.getChildren().stream() //
						.filter(DOMElement.class::isInstance) //
						.map(DOMElement.class::cast) //
						.filter(node -> "id".equals(node.getLocalName())) //
						.map(node -> node.getChild(0).getTextContent())
						.filter(Objects::nonNull)
						.map(profileId -> lemminxMavenPlugin.getProjectCache().getSnapshotProject(request.getXMLDocument(), profileId)) //
						.filter(Objects::nonNull)
						.findFirst().orElse(null);
				if (project != null) {
					plugin = findPluginInProject(project, pluginKey, artifactId);
				}
			}
		}
		if (plugin == null) {
			throw new InvalidPluginDescriptorException("Unable to resolve " + pluginKey, Collections.emptyList());
		}

		PluginDescriptor pluginDescriptor = null;
		try {
			pluginDescriptor = lemminxMavenPlugin.getMavenPluginManager().getPluginDescriptor(plugin,
				project.getRemotePluginRepositories().stream().collect(Collectors.toList()),
				lemminxMavenPlugin.getMavenSession().getRepositorySession());
		} catch (PluginResolutionException ex) {
			LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
		}
		if (pluginDescriptor == null && "0.0.1-SNAPSHOT".equals(plugin.getVersion())) { // probably missing or not parsed version
			Optional<DefaultArtifactVersion> version;
			final Plugin thePlugin = plugin;
			try {
				version = lemminxMavenPlugin.getLocalRepositorySearcher().getLocalArtifactsLastVersion().stream()
					.filter(gav -> thePlugin.getArtifactId().equals(gav.getArtifactId()))
					.filter(gav -> thePlugin.getGroupId().equals(gav.getGroupId()))
					.map(Artifact::getVersion) //
					.map(DefaultArtifactVersion::new) //
					.collect(Collectors.maxBy(Comparator.naturalOrder()));
				if (version.isPresent()) {
					plugin.setVersion(version.get().toString());
					pluginDescriptor = lemminxMavenPlugin.getMavenPluginManager().getPluginDescriptor(plugin,
							project.getRemotePluginRepositories().stream().collect(Collectors.toList()),
							lemminxMavenPlugin.getMavenSession().getRepositorySession());
				}
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return pluginDescriptor;
	}

	private static Plugin findPluginInProject(MavenProject project, String pluginKey, Optional<String> artifactId) {
		Optional<Plugin> plugin = Optional.ofNullable(project.getPlugin(pluginKey))
				.or(() -> Optional.ofNullable(project.getPluginManagement().getPluginsAsMap().get(pluginKey)));
		if (artifactId.isPresent()) {
			plugin = plugin.or(() ->
				Stream.concat(project.getBuildPlugins().stream(), project.getPluginManagement().getPlugins().stream())
						.filter(p -> artifactId.get().equals(p.getArtifactId()))
						.findFirst()
			);
		}
		return plugin.orElse(null);
	}

}
