/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

public class MavenPluginUtils {
	
	private MavenPluginUtils() {
		// Utility class, not meant to be instantiated
	}

	public static MarkupContent getMarkupDescription(Parameter parameter, boolean supportsMarkdown) {
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		
		String description = parameter.getDescription();
		description = MarkdownUtils.htmlXMLToMarkdown(description);
		
		String expression = parameter.getExpression() != null ? parameter.getExpression() : "(none)";
		String defaultValue = parameter.getDefaultValue() != null ? parameter.getDefaultValue() : "(unset)";
	
		String markdownDescription = 
				toBold.apply("Required: ") + parameter.isRequired() + lineBreak + 
				toBold.apply("Type: ") + parameter.getType() + lineBreak + 
				toBold.apply("Expression: ") + expression + lineBreak + 
				toBold.apply("Default Value: ") + defaultValue + lineBreak + 
				description;
	
		return new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, markdownDescription);
	}
	
	public static MarkupContent getMarkupDescription(MojoParameter parameter, MojoParameter parentParameter, boolean supportsMarkdown) {
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		
		final String fromParent = toBold.apply("From parent configuration element:") + lineBreak;
		String type = parameter.getType() != null ? parameter.getType() : "";
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
		
		String markdownDescription = 
				toBold.apply("Required: ") + parameter.isRequired() + lineBreak + 
				toBold.apply("Type: ") + type + lineBreak + 
				toBold.apply("Expression: ") + expression + lineBreak + 
				toBold.apply("Default Value: ") + defaultValue + lineBreak +
				description;
		
		return new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, markdownDescription);
	}

	public static Set<Parameter> collectPluginConfigurationParameters(IPositionRequest request,
			MavenLemminxExtension lemminxMavenPlugin) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, lemminxMavenPlugin);
		if (pluginDescriptor == null) {
			return Collections.emptySet();
		}
		List<MojoDescriptor> mojosToConsiderList = pluginDescriptor.getMojos();
		DOMNode executionElementDomNode = DOMUtils.findClosestParentNode(request, "execution");
		if (executionElementDomNode != null) {
			Set<String> interestingMojos = executionElementDomNode.getChildren().stream()
					.filter(node -> "goals".equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(node -> "goal".equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(DOMNode::isText).map(DOMNode::getTextContent).collect(Collectors.toSet());
			mojosToConsiderList = mojosToConsiderList.stream().filter(mojo -> interestingMojos.contains(mojo.getGoal()))
					.collect(Collectors.toList());
		}
		Set<Parameter> parameters = mojosToConsiderList.stream().flatMap(mojo -> mojo.getParameters().stream())
				.collect(Collectors.toSet());
		return parameters;
	}
	
	
	public static Set<MojoParameter> collectPluginConfigurationMojoParameters(IPositionRequest request, MavenLemminxExtension plugin) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
		if (pluginDescriptor == null) {
			return Collections.emptySet();
		}
		List<MojoDescriptor> mojosToConsiderList = pluginDescriptor.getMojos();
		DOMNode executionElementDomNode = DOMUtils.findClosestParentNode(request, "execution");
		if (executionElementDomNode != null) {
			Set<String> interestingMojos = executionElementDomNode.getChildren().stream()
					.filter(node -> "goals".equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(node -> "goal".equals(node.getLocalName())).flatMap(node -> node.getChildren().stream())
					.filter(DOMNode::isText).map(DOMNode::getTextContent).collect(Collectors.toSet());
			mojosToConsiderList = mojosToConsiderList.stream().filter(mojo -> interestingMojos.contains(mojo.getGoal()))
					.collect(Collectors.toList());
		}
		MavenProject project =  plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return Collections.emptySet();
		}
		//System.out.println("plugin: " + pluginDescriptor.getPluginLookupKey());
		//pluginDescriptor.getDependencies().forEach(dep -> System.out.println("    plugin dependency: " + dep.getArtifactId() + ":" + dep.getVersion()));
		plugin.getMavenSession().setProjects(Collections.singletonList(project));
		Set<MojoParameter> mojoParams = mojosToConsiderList.stream().flatMap(mojo -> PlexusConfigHelper.loadMojoParameters(pluginDescriptor, mojo, plugin.getMavenSession(), plugin.getBuildPluginManager()).stream()
		).collect(Collectors.toSet());
		
		return mojoParams;
	}


	public static RemoteRepository toRemoteRepo(Repository modelRepo) {
		Builder builder = new RemoteRepository.Builder(modelRepo.getId(), modelRepo.getLayout(), modelRepo.getUrl());
		return builder.build();
	}

	public static PluginDescriptor getContainingPluginDescriptor(IPositionRequest request, MavenLemminxExtension lemminxMavenPlugin) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		MavenProject project = lemminxMavenPlugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return null;
		}
		DOMNode pluginNode = DOMUtils.findClosestParentNode(request, "plugin");
		if (pluginNode == null) {
			return null;
		}
		Optional<String> groupId = DOMUtils.findChildElementText(pluginNode, "groupId");
		Optional<String> artifactId = DOMUtils.findChildElementText(pluginNode, "artifactId");
		String pluginKey = "";
		if (groupId.isPresent()) {
			pluginKey += groupId.get();
			pluginKey += ':';
		}
		if (artifactId.isPresent()) {
			pluginKey += artifactId.get();
		}
		Plugin plugin = project.getPlugin(pluginKey);
		if (plugin == null && project.getPluginManagement() != null) {
			plugin = project.getPluginManagement().getPluginsAsMap().get(pluginKey);
			
			if (plugin == null && artifactId.isPresent()) {
				//pluginArtifactMap will be empty if PluginManagement is null
				for (Entry <String, Artifact> entry : project.getPluginArtifactMap().entrySet() ) {
					if (entry.getValue().getArtifactId().equals(artifactId.get())) {
						plugin = project.getPlugin(entry.getKey());
					}
				}
			}
		}
		
		if (plugin == null) {
			throw new InvalidPluginDescriptorException("Unable to resolve " + pluginKey,  Collections.emptyList());
		}

		return lemminxMavenPlugin.getMavenPluginManager().getPluginDescriptor(plugin, project.getPluginRepositories().stream()
				.map(MavenPluginUtils::toRemoteRepo).collect(Collectors.toList()), lemminxMavenPlugin.getMavenSession().getRepositorySession());
	}

}
