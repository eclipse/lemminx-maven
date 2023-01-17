/*******************************************************************************
 * Copyright (c) 2020-2022 Red Hat Inc. and others.
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
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROJECT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGINS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.RELATIVE_PATH_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.File;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.Maven;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MojoParameter;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MarkdownUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.extensions.maven.utils.PlexusConfigHelper;
import org.eclipse.lemminx.services.extensions.HoverParticipantAdapter;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;

public class MavenHoverParticipant extends HoverParticipantAdapter {

	private static final Logger LOGGER = Logger.getLogger(MavenHoverParticipant.class.getName());
	private final MavenLemminxExtension plugin;

	public MavenHoverParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
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
		return CONFIGURATION_ELT.equals(parent.getLocalName()) ? collectPluginConfiguration(request) : null;
	}

	@Override
	public Hover onText(IHoverRequest request) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}

		DOMNode tag = request.getNode();
		DOMElement parent = tag.getParentElement();

		boolean isParentDeclaration = ParticipantUtils.isParentDeclaration(parent);

		Map.Entry<Range, String> mavenProperty = ParticipantUtils.getMavenPropertyInRequest(request);
		if (mavenProperty != null) {
			return collectProperty(request, mavenProperty);
		}
		
		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		Dependency artifactToSearch = ParticipantUtils.getArtifactToSearch(p, request);

		return switch (parent.getLocalName()) {
		case GROUP_ID_ELT, ARTIFACT_ID_ELT, VERSION_ELT -> {
			Hover hover = isParentDeclaration && p != null && p.getParent() != null ? hoverForProject(request,
					p.getParent(), ParticipantUtils.isWellDefinedDependency(artifactToSearch)) : null;
			if (hover == null) {
				Artifact artifact = ParticipantUtils.findWorkspaceArtifact(plugin, request, artifactToSearch);
				if (artifact != null && artifact.getFile() != null) {
					yield hoverForProject(request,
							plugin.getProjectCache().getSnapshotProject(artifact.getFile()).orElse(null),
							ParticipantUtils.isWellDefinedDependency(artifactToSearch));
				}
			}

			if (hover == null) {
				hover = collectArtifactDescription(request);
			}
			yield hover;
		}
		case GOAL_ELT -> collectGoal(request);
		// TODO consider incomplete GAV (eg plugins), by querying the "key" against project
		default -> null;
		};
	}

	private static final String PomTextHover_managed_version = "The managed version is {0}.";
	private static final String PomTextHover_managed_version_missing = "The managed version could not be determined.";
	private static final String PomTextHover_managed_location = "The artifact is managed in {0}";
	private static final String PomTextHover_managed_location_missing = "The managed definition location could not be determined, probably defined by \"import\" scoped dependencies.";

	private static String getActualVersionText(boolean supportsMarkdown, MavenProject project) {
		if (project == null) {
			return null;
		}

		String sourceModelId = project.getGroupId() + ':' + project.getArtifactId() + ':' + project.getVersion();
		File locacion = project.getFile();
		return createVersionMessage(supportsMarkdown, project.getVersion(), (locacion != null && locacion.exists() ? sourceModelId : null), locacion.toURI().toString());
	}
	
	private static String getActualVersionText(boolean supportsMarkdown, Model model) {
		if (model == null) {
			return null;
		}

		Parent parent = model.getParent();
		String version = model.getVersion();
		if (version == null && parent != null) {
			version = parent.getVersion();
		}

		InputLocation location =  model.getLocation(ARTIFACT_ID_ELT);
		if (location == null && parent != null) {
			location = parent.getLocation(ARTIFACT_ID_ELT);
		}

		return createVersionMessage(supportsMarkdown, version, location);
	}
	
	private String getManagedVersionText(IHoverRequest request) {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}

		// make sure model is resolved and up-to-date
		File currentFolder = new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile();
		DOMElement element = ParticipantUtils.findInterestingElement(request.getNode());
		if (element == null || (!DEPENDENCY_ELT.equals(element.getLocalName()) && !PLUGIN_ELT.equals(element.getLocalName()))) {
			return null;
		}
		
		boolean isPlugin = PLUGIN_ELT.equals(element.getLocalName());
		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(element.getOwnerDocument());
		Dependency dependency = ParticipantUtils.getArtifactToSearch(p, request);

		// Search for DEPENDENCY/PLUGIN through the parents pom's
		File parentPomFile = getParentPomFile(p, currentFolder, request.getXMLDocument());

		while (parentPomFile != null && parentPomFile.exists()) {
			DOMDocument parentXmlDocument = org.eclipse.lemminx.utils.DOMUtils.loadDocument(
					parentPomFile.toURI().toString(),
					request.getNode().getOwnerDocument().getResolverExtensionManager());
			if (parentXmlDocument == null) {
				return null; // An error occurred while loading the document
			}

			MavenProject parentMavenProject = plugin.getProjectCache().getLastSuccessfulMavenProject(parentXmlDocument);
			
			List<DOMElement> elements = findDependencyOrPluginElement(parentXmlDocument, isPlugin, dependency.getGroupId(), dependency.getArtifactId());
			for (DOMElement e : elements) {
				Optional<String> version = DOMUtils.findChildElementText(e, VERSION_ELT);
				if (version.isPresent()) {
					String sourceModelId = parentMavenProject.getGroupId() + ':' + parentMavenProject.getArtifactId() + ':' + parentMavenProject.getVersion();
					return createVersionMessage(request.canSupportMarkupKind(MarkupKind.MARKDOWN), version.get(), sourceModelId, parentPomFile.toURI().toString());
				}
			}
				
			// Else proceed with the next parent
			parentPomFile = getParentPomFile(parentMavenProject, currentFolder, parentXmlDocument);
		}
		return null;
	}

	List<DOMElement> findDependencyOrPluginElement(DOMDocument document, boolean isPlugin, String groupId, String artifactId) {
		return DOMUtils.findNodesByLocalName(document, isPlugin ? PLUGIN_ELT : DEPENDENCY_ELT)
		.stream().filter(d -> {
			if (!DOMUtils.isADescendantOf(d, isPlugin ? PLUGINS_ELT : DEPENDENCIES_ELT)) {
				return false;
			}
			Optional<String> g = DOMUtils.findChildElementText(d, GROUP_ID_ELT);
			Optional<String> a = DOMUtils.findChildElementText(d, ARTIFACT_ID_ELT);
			return (g.isPresent() && g.get().equals(groupId) && a.isPresent() && a.get().equals(artifactId));
		}).filter(DOMElement.class::isInstance).map(DOMElement.class::cast).collect(Collectors.toList());
	}

	private File getParentPomFile (MavenProject project, File currentFolder, DOMDocument document) {
		Optional<DOMElement> projectElement = DOMUtils.findChildElement(document, PROJECT_ELT);
		Optional<DOMElement> parentElement = projectElement.isPresent() ? DOMUtils.findChildElement(projectElement.get(), PARENT_ELT) : Optional.empty();
		Optional<String> relativePath = parentElement.isPresent() ? DOMUtils.findChildElementText(parentElement.get(), RELATIVE_PATH_ELT) : Optional.empty();
		if (relativePath.isPresent()) {
			File relativeFile = new File(currentFolder, relativePath.get());
			if (relativeFile.isDirectory()) {
				relativeFile = new File(relativeFile, Maven.POMv4);
			}
			if (relativeFile.isFile()) {
				return relativeFile;
			}
		} else {
			File relativeFile = new File(currentFolder.getParentFile(), Maven.POMv4);
			if (relativeFile.isFile()) {
				return relativeFile;
			} else {
				// those next lines may actually be more generic and suit parent definition in any case
				if (project != null && project.getParentFile() != null) {
					return project.getParentFile();
				}
			}
		}
		return null;
	}
	
	
	private static String createVersionMessage(boolean supportsMarkdown, String version, InputLocation location) {
		String sourceModelId = null;
		String uri = null;
		if (location != null) {
			InputSource source = location.getSource();
			if (source != null) {
				sourceModelId = source.getModelId();
				uri = source.getLocation();
			}
		}
		
		return createVersionMessage(supportsMarkdown, version, sourceModelId, uri);
	}
	
	private static String createVersionMessage(boolean supportsMarkdown, String version, String sourceModelId, String uri) {
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();

		String message = null;
		if (version != null) {
			message = toBold.apply(MessageFormat.format(PomTextHover_managed_version, version));
		} else {
			message = toBold.apply(PomTextHover_managed_version_missing);
		}

		if (sourceModelId != null) {
			message += ' ' + toBold.apply(MessageFormat.format(PomTextHover_managed_location, 
					supportsMarkdown ? MarkdownUtils.toLink(uri, sourceModelId, null) : sourceModelId));
		} else {
			message += ' ' + toBold.apply(PomTextHover_managed_location_missing);
		}

		return message;
	}

	private Hover hoverForProject(IHoverRequest request, MavenProject p, boolean isWellDefined) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold
				: UnaryOperator.identity();
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		String message = "";

		if (p.getName() != null) {
			message += toBold.apply(p.getName());
		}

		if (p.getDescription() != null) {
			message += lineBreak + p.getDescription();
		}

		if (!isWellDefined) {
			String managedVersion = getManagedVersionText(request);
			if (managedVersion == null) {
				managedVersion = getActualVersionText(supportsMarkdown, p);
			}
			if (managedVersion != null) {
				message += lineBreak + managedVersion;
			}
		}

		if (message.isBlank()) {
			return null;
		}
		return new Hover(new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT,
				message));
	}
	
	private Hover collectArtifactDescription(IHoverRequest request) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);

		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		Dependency artifactToSearch = ParticipantUtils.getArtifactToSearch(p, request);
		boolean wellDefined = ParticipantUtils.isWellDefinedDependency(artifactToSearch);
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
					.sorted(Comparator.comparing((Artifact artifact) -> new DefaultArtifactVersion(artifact.getVersion())).reversed())
					.findFirst().map(plugin.getLocalRepositorySearcher()::findLocalFile).map(file -> 
							builder.buildRawModel(file, ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL, true).get())
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

						if (!wellDefined) {
							String managedVersion = getManagedVersionText(request);
							if (managedVersion == null) {
								managedVersion = getActualVersionText(supportsMarkdown, model);
							}
							if (managedVersion != null) {
								message += lineBreak + managedVersion;
							}
						}
						
						return message;
					}).map(message -> (message.length() > 2 ? message : null));
			if (localDescription.isPresent()) {
				return new Hover(new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT,
						localDescription.get()));
			}
		} catch (Exception e1) {
			LOGGER.log(Level.SEVERE, e1.toString(), e1);
		}
		
		// we don't have description or other valuable information for non-local artifacts.
		return null;
	}

	private Hover collectGoal(IPositionRequest request) {
		DOMNode node = request.getNode();
		try {
			PluginDescriptor pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, plugin);
			if (pluginDescriptor == null) { // probable incorrect pom file at this moment
				return null;
			}
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
		if (CONFIGURATION_ELT.equals(request.getParentElement().getLocalName())) {
			// The configuration element being hovered is at the top level
			for (MojoParameter parameter : parameters) {
				if (request.getNode().getLocalName().equals(parameter.name)) {
					return new Hover(MavenPluginUtils.getMarkupDescription(parameter, null, supportsMarkdown));
				}
			}
			// not found by name, search by alias
			for (MojoParameter parameter : parameters) {
				if (request.getNode().getLocalName().equals(parameter.alias)) {
					return new Hover(MavenPluginUtils.getMarkupDescription(parameter, null, supportsMarkdown));
				}
			}
		}

		// Nested case: node is a grand child of configuration
		// Get the node's ancestor which is a child of configuration
		DOMNode parentParameterNode = DOMUtils.findAncestorThatIsAChildOf(request, CONFIGURATION_ELT);
		if (parentParameterNode != null) {
			List<MojoParameter> parentParameters = parameters.stream()
					.filter(mojoParameter -> mojoParameter.name.equals(parentParameterNode.getLocalName()))
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
					if (request.getNode().getLocalName().equals(parameter.name)) {
						return new Hover(
								MavenPluginUtils.getMarkupDescription(parameter, parentParameter, supportsMarkdown));
					}
				}
			}
		}
		return null;
	}

	private Hover collectProperty(IHoverRequest request, Map.Entry<Range, String> property) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		DOMDocument doc = request.getXMLDocument();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
		if (project != null) {
			Map<String, String> allProps = ParticipantUtils.getMavenProjectProperties(project);
			UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();

			for (Entry<String, String> prop : allProps.entrySet()) {
				String mavenProperty = prop.getKey();
				if (property.getValue().equals(mavenProperty)) {
					String message = toBold.apply("Property: ") + mavenProperty + lineBreak + toBold.apply("Value: ")
							+ prop.getValue() + lineBreak;

					Hover hover = new Hover(
							new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, message));
					hover.setRange(property.getKey());
					return hover;
				}
			}
		}
		return null;
	}
}
