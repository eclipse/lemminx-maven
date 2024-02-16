/*******************************************************************************
 * Copyright (c) 2020, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.hover;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CONFIGURATION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGINS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROJECT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROPERTIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.File;
import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.Maven;
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
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.MavenInitializationException;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MavenModelOutOfDatedException;
import org.eclipse.lemminx.extensions.maven.MojoParameter;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MarkdownUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenParseUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.extensions.maven.utils.PlexusConfigHelper;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lemminx.services.extensions.hover.HoverParticipantAdapter;
import org.eclipse.lemminx.services.extensions.hover.IHoverRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenHoverParticipant extends HoverParticipantAdapter {

	private static final Logger LOGGER = Logger.getLogger(MavenHoverParticipant.class.getName());
	private final MavenLemminxExtension plugin;

	public MavenHoverParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public Hover onTag(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}
		try {
			if (DOMUtils.isADescendantOf(request.getNode(), CONFIGURATION_ELT)) {
				return collectPluginConfiguration(request, cancelChecker);
			}
		} catch (MavenInitializationException | MavenModelOutOfDatedException e) {
			// - Maven is initializing
			// - or parse of maven model with DOM document is out of dated
			// -> catch the error to avoid breaking XML hover from LemMinX
		}
		return null;
	}

	@Override
	public Hover onText(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}
		try {
	
			Map.Entry<Range, String> mavenProperty = ParticipantUtils.getMavenPropertyInRequest(request);
			if (mavenProperty != null) {
				return collectProperty(request, mavenProperty, cancelChecker);
			}
			
			DOMNode tag = request.getNode();
			DOMElement parent = tag.getParentElement();
			if (ParticipantUtils.isProject(parent)) {
				// Do not show hover for the project itself
				return null;
			}
		
			cancelChecker.checkCanceled();
			boolean isParentDeclaration = ParticipantUtils.isParentDeclaration(parent);
			MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
			Dependency artifactToSearch = ParticipantUtils.getArtifactToSearch(p, tag);
	
			return switch (parent.getLocalName()) {
				case GROUP_ID_ELT, ARTIFACT_ID_ELT, VERSION_ELT -> {
					Hover hover = null;
					
					if (isParentDeclaration) {
						if (p != null) {
							cancelChecker.checkCanceled();
							File parentPomFile = getParentPomFile(p, request.getXMLDocument());
							if (parentPomFile != null) {
								hover = hoverForProject(request, p.getParent(), ParticipantUtils.isWellDefinedDependency(artifactToSearch), cancelChecker);
							}
						}
					}
		
					if (hover == null) {
						cancelChecker.checkCanceled();
						Artifact artifact = ParticipantUtils.findWorkspaceArtifact(plugin, request, artifactToSearch);
						if (artifact != null && artifact.getFile() != null) {
							yield hoverForProject(request,
									plugin.getProjectCache().getSnapshotProject(artifact.getFile()).orElse(null),
									ParticipantUtils.isWellDefinedDependency(artifactToSearch),
									cancelChecker);
						}
					}
		
					if (hover == null) {
						hover = collectArtifactDescription(request, cancelChecker);
					}
					yield hover;
				}
				case GOAL_ELT -> collectGoal(request, cancelChecker);
				// TODO consider incomplete GAV (eg plugins), by querying the "key" against project
				default -> null;
				};
		} catch (MavenInitializationException | MavenModelOutOfDatedException e) {
			// - Maven is initializing
			// - or parse of maven model with DOM document is out of dated
			// -> catch the error to avoid breaking XML hover from LemMinX
		}
		return null;
	}

	private static final String PomTextHover_managed_version = "The managed version is {0}.";
	private static final String PomTextHover_managed_version_missing = "The managed version could not be determined.";
	private static final String PomTextHover_managed_location = "The artifact is managed in {0}";
	private static final String PomTextHover_managed_location_missing = "The managed definition location could not be determined, probably defined by \"import\" scoped dependencies.";
	private static final String PomTextHover_property_location = "The property is defined in {0}";
	private static final String PomTextHover_managed_scope = "The managed scope is: \"{0}\"";

	private static String getActualVersionText(boolean supportsMarkdown, MavenProject project, CancelChecker cancelChecker) throws CancellationException {
		if (project == null) {
			return null;
		}

		String sourceModelId = project.getGroupId() + ':' + project.getArtifactId() + ':' + project.getVersion();
		File locacion = project.getFile();
		return createVersionMessage(supportsMarkdown, project.getVersion(), 
				(locacion != null && locacion.exists() ? sourceModelId : null), locacion.toURI().toString());
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
	
	private String getManagedVersionText(IHoverRequest request, CancelChecker cancelChecker) throws CancellationException {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return null;
		}

		// make sure model is resolved and up-to-date
		DOMElement element = ParticipantUtils.findInterestingElement(request.getNode());
		if (element == null || (!DEPENDENCY_ELT.equals(element.getLocalName()) && !PLUGIN_ELT.equals(element.getLocalName()))) {
			return null;
		}
		
		cancelChecker.checkCanceled();
		boolean isPlugin = PLUGIN_ELT.equals(element.getLocalName());
		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(element.getOwnerDocument());
		Dependency dependency = ParticipantUtils.getArtifactToSearch(p, request.getNode());

		cancelChecker.checkCanceled();
		// Search for DEPENDENCY/PLUGIN through the parents pom's
		File parentPomFile = getParentPomFile(p, request.getXMLDocument());
		while (parentPomFile != null && parentPomFile.exists()) {
			cancelChecker.checkCanceled();
			DOMDocument parentXmlDocument = org.eclipse.lemminx.utils.DOMUtils.loadDocument(
					parentPomFile.toURI().toString(),
					request.getNode().getOwnerDocument().getResolverExtensionManager());
			if (parentXmlDocument == null) {
				return null; // An error occurred while loading the document
			}

			cancelChecker.checkCanceled();
			MavenProject parentMavenProject = plugin.getProjectCache().getLastSuccessfulMavenProject(parentXmlDocument);

			List<DOMElement> elements = findDependencyOrPluginElement(parentXmlDocument, isPlugin, dependency.getGroupId(), dependency.getArtifactId());
			for (DOMElement e : elements) {
				cancelChecker.checkCanceled();
				Optional<String> version = DOMUtils.findChildElementText(e, VERSION_ELT);
				if (version.isPresent()) {
					Dependency d = ParticipantUtils.getArtifactToSearch(parentMavenProject, e);
					String sourceModelId = parentMavenProject.getGroupId() + ':' + parentMavenProject.getArtifactId() + ':' + parentMavenProject.getVersion();
					return createVersionMessage(request.canSupportMarkupKind(MarkupKind.MARKDOWN), d.getVersion(), sourceModelId, parentPomFile.toURI().toString());
				}
			}
				
			// Else proceed with the next parent
			parentPomFile = getParentPomFile(parentMavenProject, parentXmlDocument);
		}
		return null;
	}

	private static List<DOMElement> findDependencyOrPluginElement(DOMDocument document, boolean isPlugin, String groupId, String artifactId) {
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

	@SuppressWarnings("deprecation")
	private static File getParentPomFile (MavenProject project,  DOMDocument document) {
		Optional<File> parentPomFile = Optional.ofNullable(project)
		        .map(MavenProject::getParentFile);
		if (parentPomFile.isPresent()) {
			return parentPomFile.get();
		}
		
		// Try searching from the document's parent section
		File currentFolder = new File(URI.create(document.getTextDocument().getUri())).getParentFile();
		Optional<DOMElement> projectElement = DOMUtils.findChildElement(document, PROJECT_ELT);
		if(projectElement.isPresent()) {
			Optional<DOMElement> parentElement = DOMUtils.findChildElement(projectElement.get(), PARENT_ELT);
			if (parentElement.isPresent()) {
				// @TODO: check group/artifactId/version if present
				Parent parent = MavenParseUtils.parseParent(parentElement.get());
				String relativePath = parent.getRelativePath();
				if (relativePath != null && !relativePath.isEmpty()) {
					File relativeFile = new File(currentFolder, relativePath);
					if (relativeFile.canRead() && relativeFile.isDirectory()) {
						relativeFile = new File(relativeFile, Maven.POMv4);
					}
					if (relativeFile.canRead() && relativeFile.isFile()) {
						return relativeFile.getAbsoluteFile();
					}
				} else {
					File relativeFile = new File(currentFolder.getParentFile(), Maven.POMv4);
					if (relativeFile.canRead() && relativeFile.isFile()) {
						return relativeFile;
					}
				}
			}
		}
		
		return null;
	}
	
	private static String createVersionMessage(boolean supportsMarkdown, String version, InputLocation location) {
		String sourceModelId = null;
		String uri = null;
		Range range = null;
		if (location != null) {
			InputSource source = location.getSource();
			if (source != null) {
				sourceModelId = source.getModelId();
				uri = source.getLocation();
			}
		
			int lineNumber = location.getLineNumber();
			if (lineNumber != -1) {
				int columnNumber = location.getColumnNumber();
				Position position = new Position(lineNumber, columnNumber != -1 ? columnNumber : 0);
				range = new Range(position, position);
			}
		}
		
		return createVersionMessage(supportsMarkdown, version, sourceModelId, uri, range);
	}
	
	private static String createVersionMessage(boolean supportsMarkdown, String version, String sourceModelId, String uri) {
		return createVersionMessage(supportsMarkdown, version, sourceModelId, uri, null);
	}	

	private static String createVersionMessage(boolean supportsMarkdown, String version, String sourceModelId, String uri, Range range) {
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();

		String message = (version != null) 
				? toBold.apply(MessageFormat.format(PomTextHover_managed_version, version)) 
				: toBold.apply(PomTextHover_managed_version_missing);

		if (sourceModelId != null) {
			message += ' ' + toBold.apply(MessageFormat.format(PomTextHover_managed_location, 
					supportsMarkdown ? MarkdownUtils.toLink(uri, range, sourceModelId, sourceModelId) : sourceModelId));
		} else {
			message += ' ' + toBold.apply(PomTextHover_managed_location_missing);
		}

		return message;
	}

	private Hover hoverForProject(IHoverRequest request, MavenProject p, boolean isWellDefined, CancelChecker cancelChecker) throws CancellationException {
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
			String managedVersion = getManagedVersionText(request, cancelChecker);
			if (managedVersion == null) {
				managedVersion = getActualVersionText(supportsMarkdown, p, cancelChecker);
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
	
	private Hover collectArtifactDescription(IHoverRequest request, CancelChecker cancelChecker) throws CancellationException {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);

		cancelChecker.checkCanceled();
		MavenProject p = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		Dependency dependency = ParticipantUtils.getArtifactToSearch(p, request.getNode());
		boolean wellDefined = ParticipantUtils.isWellDefinedDependency(dependency);
		DOMElement element = ParticipantUtils.findInterestingElement(request.getNode());
		dependency = ParticipantUtils.resolveDependency(p, dependency, element, plugin);
		Optional<Dependency> managed = ParticipantUtils.isManagedDependency(element) ?
				Optional.empty() : ParticipantUtils.findManagedDependency(p, dependency);
		
		Dependency originalDependency = dependency; // To get the scope
		cancelChecker.checkCanceled();
		try {
			// Find in local repository
			File localArtifactLocation = null;
			if (managed.isPresent()) {
				// Use managed dependency
				dependency = managed.get();
				InputLocation inputLocation = dependency.getLocation(ARTIFACT_ID_ELT);
				if (inputLocation != null && inputLocation.getSource() != null) {
					String url = inputLocation.getSource().getLocation();
					localArtifactLocation = new File(url);
				}
			} else {
				localArtifactLocation = plugin.getLocalRepositorySearcher().findLocalFile(dependency);
			}

			if (localArtifactLocation != null && localArtifactLocation.isFile()) {
				cancelChecker.checkCanceled();
				ModelBuilder builder = plugin.getProjectCache().getPlexusContainer().lookup(ModelBuilder.class);
				Model model = builder.buildRawModel(localArtifactLocation, ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL, true).get();
				if (model != null) {
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
						String managedVersion = managed.isPresent() ? 
								createVersionMessage(request.canSupportMarkupKind(MarkupKind.MARKDOWN), dependency.getVersion(), dependency.getLocation(ARTIFACT_ID_ELT))
								: getManagedVersionText(request, cancelChecker);
						if (managedVersion == null) {
							managedVersion = getActualVersionText(supportsMarkdown, model);
						}
						if (managedVersion != null) {
							message += lineBreak + managedVersion;
						}
					}
					
					// Dependency scope info from original dependency has higher priority
					String scope = dependency.getScope();
					if (originalDependency.getScope() != null) {
						scope = originalDependency.getScope();
					}
					if (scope != null) {
						message += lineBreak + toBold.apply(MessageFormat.format(PomTextHover_managed_scope, scope));
					}
					
					if (message.length() > 2) {
						return new Hover(new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, message));
					}
				}
			}
		} catch (CancellationException e) {
			// Log at FINER level and return null
			throw e;
		} catch (Exception e1) {
			LOGGER.log(Level.SEVERE, e1.toString(), e1);
		}
		
		// we don't have description or other valuable information for non-local artifacts
		// or the operation is cancelled
		return null;
	}

	private Hover collectGoal(IPositionRequest request, CancelChecker cancelChecker) throws CancellationException {
		DOMNode node = request.getNode();
		cancelChecker.checkCanceled();
		PluginDescriptor pluginDescriptor = null;
		try {
			pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(node, plugin);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		if (pluginDescriptor == null) { // probable incorrect pom file at this moment
			return null;
		}

		for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
			cancelChecker.checkCanceled();
			if (!node.getNodeValue().trim().isEmpty() && node.getNodeValue().equals(mojo.getGoal())) {
				return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, mojo.getDescription()));
			}
		}
		return null;
	}

	private Hover collectPluginConfiguration(IHoverRequest request, CancelChecker cancelChecker) throws CancellationException {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		Set<MojoParameter> parameters;
		cancelChecker.checkCanceled();
		try {
			parameters = MavenPluginUtils.collectPluginConfigurationMojoParameters(request, plugin, cancelChecker);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return null;
		}
		if (CONFIGURATION_ELT.equals(request.getParentElement().getLocalName())) {
			// The configuration element being hovered is at the top level
			for (MojoParameter parameter : parameters) {
				cancelChecker.checkCanceled();
				if (request.getNode().getLocalName().equals(parameter.name)) {
					return new Hover(MavenPluginUtils.getMarkupDescription(parameter, null, supportsMarkdown));
				}
			}
			// not found by name, search by alias
			for (MojoParameter parameter : parameters) {
				cancelChecker.checkCanceled();
				if (request.getNode().getLocalName().equals(parameter.alias)) {
					return new Hover(MavenPluginUtils.getMarkupDescription(parameter, null, supportsMarkdown));
				}
			}
		}

		// Nested case: node is a grand child of configuration
		// Get the node's ancestor which is a child of configuration
		DOMNode parentParameterNode = DOMUtils.findAncestorThatIsAChildOf(request, CONFIGURATION_ELT);
		if (parentParameterNode != null) {
			cancelChecker.checkCanceled();
			List<MojoParameter> parentParameters = parameters.stream()
					.filter(mojoParameter -> mojoParameter.name.equals(parentParameterNode.getLocalName()))
					.collect(Collectors.toList());
			if (!parentParameters.isEmpty()) {
				MojoParameter parentParameter = parentParameters.get(0);
				if (parentParameter.getNestedParameters().size() == 1) {
					cancelChecker.checkCanceled();
					// The parent parameter must be a collection of a type
					MojoParameter nestedParameter = parentParameter.getNestedParameters().get(0);
					Class<?> potentialInlineType = PlexusConfigHelper.getRawType(nestedParameter.getParamType());
					if (potentialInlineType != null && PlexusConfigHelper.isInline(potentialInlineType)) {
						return new Hover(MavenPluginUtils.getMarkupDescription(nestedParameter, parentParameter,
								supportsMarkdown));
					}
				}

				cancelChecker.checkCanceled();
				// Get all deeply nested parameters
				List<MojoParameter> nestedParameters = parentParameter.getFlattenedNestedParameters();
				nestedParameters.add(parentParameter);
				for (MojoParameter parameter : nestedParameters) {
					cancelChecker.checkCanceled();
					if (request.getNode().getLocalName().equals(parameter.name)) {
						return new Hover(
								MavenPluginUtils.getMarkupDescription(parameter, parentParameter, supportsMarkdown));
					}
				}
			}
		}
		return null;
	}

	private Hover collectProperty(IHoverRequest request, Map.Entry<Range, String> property, CancelChecker cancelChecker) throws CancellationException {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		UnaryOperator<String> toBold = supportsMarkdown ? MarkdownUtils::toBold : UnaryOperator.identity();
		String lineBreak = MarkdownUtils.getLineBreak(supportsMarkdown);
		DOMDocument doc = request.getXMLDocument();
		
		cancelChecker.checkCanceled();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(doc);
		if (project != null) {
			cancelChecker.checkCanceled();
			Map<String, String> allProps = ParticipantUtils.getMavenProjectProperties(project);
			return allProps.entrySet().stream()
				.filter(prop -> property.getValue().equals(prop.getKey()))
				.map(prop -> {
					StringBuilder message = new StringBuilder();
					message.append(toBold.apply("Property: ")).append(prop.getKey()).append(lineBreak)
						.append(toBold.apply("Value: ")).append(prop.getValue()).append(lineBreak);
	
					// Find location
					MavenProject parentProject = project, childProj = project;
					String propertyName = property.getValue();
					while (parentProject != null && parentProject.getProperties().containsKey(propertyName)) {
						cancelChecker.checkCanceled();
						childProj = parentProject;
						parentProject = parentProject.getParent();
					}

					DOMNode propertyDeclaration = null;
					Predicate<DOMNode> isMavenProperty = (node) -> PROPERTIES_ELT.equals(node.getParentNode().getLocalName());

					URI childProjectUri = ParticipantUtils.normalizedUri(childProj.getFile().toURI().toString());
					URI thisProjectUri = ParticipantUtils.normalizedUri(doc.getDocumentURI());
					cancelChecker.checkCanceled();
					if (childProjectUri.equals(thisProjectUri)) {
						// Property is defined in the same file as the request
						propertyDeclaration = DOMUtils.findNodesByLocalName(doc, propertyName).stream()
								.filter(isMavenProperty).findFirst().orElse(null);
					} else {
						DOMDocument propertyDeclaringDocument = org.eclipse.lemminx.utils.DOMUtils.loadDocument(
								childProj.getFile().toURI().toString(),
								request.getNode().getOwnerDocument().getResolverExtensionManager());
						cancelChecker.checkCanceled();
						propertyDeclaration = DOMUtils.findNodesByLocalName(propertyDeclaringDocument, propertyName)
								.stream().filter(isMavenProperty).findFirst().orElse(null);
					}

					if (propertyDeclaration != null) {
						cancelChecker.checkCanceled();
						String uri = childProj.getFile().getAbsolutePath();
						Range targetRange = XMLPositionUtility.createRange(propertyDeclaration);
							String sourceModelId = childProj.getGroupId() + ':' + childProj.getArtifactId() + ':'
									+ childProj.getVersion();
							message.append(toBold.apply(MessageFormat.format(PomTextHover_property_location,
									supportsMarkdown ? MarkdownUtils.toLink(uri, targetRange, sourceModelId, null)
											: sourceModelId)));
					} else {
							ProjectBuildingRequest projectRequest = project.getProjectBuildingRequest();
							if (projectRequest != null) {
								if (projectRequest.getUserProperties().getProperty(propertyName) != null) {
									message.append(toBold.apply(MessageFormat.format(PomTextHover_property_location,
											"the user properties")));
								}
							}
					}

					Hover hover = new Hover(
							new MarkupContent(supportsMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, message.toString()));
					hover.setRange(property.getKey());
					return hover;
				}).findAny().orElse(null);
		}
		return null;
	}
}
