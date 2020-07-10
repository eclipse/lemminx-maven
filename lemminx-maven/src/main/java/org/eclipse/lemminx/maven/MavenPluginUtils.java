/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
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

public class MavenPluginUtils {
	
	static final String LINE_BREAK = "\n\n";

	private MavenPluginUtils() {
		// Utility class, not meant to be instantiated
	}

	public static MarkupContent getMarkupDescription(Parameter parameter) {
		String description = parameter.getDescription();
		description = htmlXMLToMarkdown(description);
		return new MarkupContent("markdown",
				"**required:** " + parameter.getRequirement() + LINE_BREAK + "**Type:** " + parameter.getType() + LINE_BREAK
						+ "Expression: " + parameter.getExpression() + LINE_BREAK + "Default Value: " + parameter.getDefaultValue()
						+ LINE_BREAK + description);
	}
	
	public static MarkupContent getMarkupDescription(MojoParameter parameter, MojoParameter parentParameter) {
		final String fromParent = "**From parent configuration element:**" + LINE_BREAK;
		String type = parameter.getType() != null ? parameter.getType() : "";
		String expression = parameter.getExpression() != null ? parameter.getExpression() : "";
		String defaultValue = parameter.getDefaultValue() != null ? parameter.getDefaultValue() : "";
		String description = parameter.getDescription() != null ? parameter.getDescription() : "";
		
		if (defaultValue.isEmpty() && parentParameter != null && parentParameter.getDefaultValue() != null) {
			defaultValue = fromParent + parentParameter.getDefaultValue();
		}

		if (description.isEmpty() && parentParameter != null) {
			description = fromParent + parentParameter.getDescription();
		}
		
		description = htmlXMLToMarkdown(description);

		// @formatter:off
		String markdownDescription = 
				"**required:** " + parameter.isRequired() + LINE_BREAK 
				+ "**Type:** "+ type + LINE_BREAK 
				+ "Expression: " + expression + LINE_BREAK 
				+ "Default Value: " + defaultValue + LINE_BREAK 
				+ description;
		// @formatter:on
		return new MarkupContent("markdown", markdownDescription);
	}

	private static String htmlXMLToMarkdown(String description) {
		if (description.contains("<pre>") && description.contains("&lt;")) {
			//Add markdown formatting to XML
			String xmlContent = description.substring(description.indexOf("<pre>") + 6, description.indexOf("</pre>") - 1);
			description = description.substring(0, description.indexOf("<pre>"));
			xmlContent = xmlContent.replaceAll("&lt;", "<");
			xmlContent = xmlContent.replaceAll("&gt;", ">");
			xmlContent = "```XML" + "\n" + xmlContent + "\n" + "```";
			description = description + LINE_BREAK + xmlContent;
		}
		return description;
	}

	public static List<Parameter> collectPluginConfigurationParameters(IPositionRequest request,
			MavenProjectCache cache, RepositorySystemSession repoSession, MavenPluginManager pluginManager, BuildPluginManager buildPluginManager, MavenSession mavenSession) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, cache, repoSession,
				pluginManager);
		if (pluginDescriptor == null) {
			return Collections.emptyList();
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
		List<Parameter> parameters = mojosToConsiderList.stream().flatMap(mojo -> mojo.getParameters().stream())
				.collect(Collectors.toList());
		return parameters;
	}
	
	
	public static Set<MojoParameter> collectPluginConfigurationMojoParameters(IPositionRequest request,
			MavenProjectCache cache, RepositorySystemSession repoSession, MavenPluginManager pluginManager, BuildPluginManager buildPluginManager, MavenSession mavenSession) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, cache, repoSession,
				pluginManager);
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
		MavenProject project =  cache.getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return Collections.emptySet();
		}
		//System.out.println("plugin: " + pluginDescriptor.getPluginLookupKey());
		//pluginDescriptor.getDependencies().forEach(dep -> System.out.println("    plugin dependency: " + dep.getArtifactId() + ":" + dep.getVersion()));
		mavenSession.setProjects(Collections.singletonList(project));
		Set<MojoParameter> mojoParams = mojosToConsiderList.stream().flatMap(mojo -> PlexusConfigHelper.loadMojoParameters(pluginDescriptor, mojo, mavenSession, buildPluginManager).stream()
		).collect(Collectors.toSet());
		
		return mojoParams;
	}


	public static RemoteRepository toRemoteRepo(Repository modelRepo) {
		Builder builder = new RemoteRepository.Builder(modelRepo.getId(), modelRepo.getLayout(), modelRepo.getUrl());
		return builder.build();
	}

	public static PluginDescriptor getContainingPluginDescriptor(IPositionRequest request, MavenProjectCache cache, RepositorySystemSession repositorySystemSession,
			MavenPluginManager pluginManager) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
		MavenProject project = cache.getLastSuccessfulMavenProject(request.getXMLDocument());
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

		return pluginManager.getPluginDescriptor(plugin, project.getPluginRepositories().stream()
				.map(MavenPluginUtils::toRemoteRepo).collect(Collectors.toList()), repositorySystemSession);
	}

}
