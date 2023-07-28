/*******************************************************************************
 * Copyright (c) 2019-2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.completion;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CONFIGURATION_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCIES_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DEPENDENCY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.EXISTS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.FILE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.FILTERS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.FILTER_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GOAL_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.MISSING_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.MODULE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.OUTPUT_DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_EAR;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_EJB;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_JAR;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_MAVEN_PLUGIN;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_POM;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PACKAGING_TYPE_WAR;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PARENT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PHASE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGINS_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PLUGIN_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.RELATIVE_PATH_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.SCOPE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.SCRIPT_SOURCE_DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.SOURCE_DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TARGET_PATH_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TEST_OUTPUT_DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TEST_SOURCE_DIRECTORY_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.Maven;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.DOMConstants;
import org.eclipse.lemminx.extensions.maven.DependencyScope;
import org.eclipse.lemminx.extensions.maven.MavenInitializationException;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.MojoParameter;
import org.eclipse.lemminx.extensions.maven.Phase;
import org.eclipse.lemminx.extensions.maven.participants.ArtifactWithDescription;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenParseUtils;
import org.eclipse.lemminx.extensions.maven.utils.MavenPluginUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.extensions.maven.utils.PlexusConfigHelper;
import org.eclipse.lemminx.services.extensions.completion.CompletionParticipantAdapter;
import org.eclipse.lemminx.services.extensions.completion.ICompletionRequest;
import org.eclipse.lemminx.services.extensions.completion.ICompletionResponse;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MavenCompletionParticipant extends CompletionParticipantAdapter {

	public static final String WAITING_LABEL = "⏳  Waiting for Maven Central Response...";
	
	private static final Logger LOGGER = Logger.getLogger(MavenCompletionParticipant.class.getName());
	private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("[-.a-zA-Z0-9]+");

	private static final String FILE_TYPE = "File";
	private static final String STRING_TYPE = "File";
	private static final String DIRECTORY_STRING_LC = "directory";

	// Extension packaging types: components.xml path and element names
	private static final String COMPONENTS_PATH = "META-INF/plexus/components.xml";
	private static final String JAR_EXT = ".jar";
	private static final String COMPONENTS_COMPONENT_ELT = "component";
	private static final String COMPONENTS_ROLE_ELT = "role";
	private static final String COMPONENTS_CONFIGURATION_ELT = "configuration";
	private static final String COMPONENTS_TYPE_ELT = "type";

	static interface GAVInsertionStrategy {
		/**
		 * set current element value and add siblings as addition textEdits
		 */
		public static final GAVInsertionStrategy ELEMENT_VALUE_AND_SIBLING = new GAVInsertionStrategy() {
		};

		/**
		 * insert elements as children of the parent element
		 */
		public static final GAVInsertionStrategy CHILDREN_ELEMENTS = new GAVInsertionStrategy() {
		};

		public static final class NodeWithChildrenInsertionStrategy implements GAVInsertionStrategy {
			public final String elementName;

			public NodeWithChildrenInsertionStrategy(String elementName) {
				this.elementName = elementName;
			}
		}
	}

	private final MavenLemminxExtension plugin;

	public MavenCompletionParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onTagOpen(ICompletionRequest request, ICompletionResponse response, CancelChecker cancelChecker) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return;
		}

		try {
			DOMNode tag = request.getNode();
			if (DOMUtils.isADescendantOf(tag, CONFIGURATION_ELT)) {
				collectPluginConfiguration(request).forEach(response::addCompletionItem);
			}
		} catch(MavenInitializationException e) {
			// Maven is initializing, catch the error to avoid breaking XML completion from LemMinX
		}
	}

	// TODO: Factor this with MavenHoverParticipant's equivalent method
	private List<CompletionItem> collectPluginConfiguration(ICompletionRequest request) {
		boolean supportsMarkdown = request.canSupportMarkupKind(MarkupKind.MARKDOWN);
		try {
			Set<MojoParameter> parameters = MavenPluginUtils.collectPluginConfigurationMojoParameters(request, plugin);

			if (CONFIGURATION_ELT.equals(request.getParentElement().getLocalName())) {
				// The configuration element being completed is at the top level
				return parameters.stream()
						.map(param -> toTag(param.name,
								MavenPluginUtils.getMarkupDescription(param, null, supportsMarkdown), request))
	 					.collect(Collectors.toList());
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
							return Collections.singletonList(toTag(nestedParameter.name, MavenPluginUtils
									.getMarkupDescription(nestedParameter, parentParameter, supportsMarkdown), request));
						}
					}
	
					// Get all deeply nested parameters
					List<MojoParameter> nestedParameters = parentParameter.getFlattenedNestedParameters();
					nestedParameters.add(parentParameter);
					return nestedParameters.stream()
							.map(param -> toTag(param.name,
									MavenPluginUtils.getMarkupDescription(param, parentParameter, supportsMarkdown),
									request))
							.collect(Collectors.toList());
				}
			}
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}

		return Collections.emptyList();
	}

	@Override
	public void onXMLContent(ICompletionRequest request, ICompletionResponse response, CancelChecker cancelChecker) throws Exception {
		if (!MavenLemminxExtension.match(request.getXMLDocument())) {
			return;
		}

		try {
			if (request.getXMLDocument().getText().length() < 2) {
				response.addCompletionItem(createMinimalPOMCompletionSnippet(request));
			}
			DOMElement parent = request.getParentElement();
			if (parent == null || parent.getLocalName() == null) {
				return;
			}
			DOMElement grandParent = parent.getParentElement();
			boolean isPlugin = ParticipantUtils.isPlugin(parent);
			boolean isParentDeclaration = ParticipantUtils.isParentDeclaration(parent);
			Optional<String> groupId = grandParent == null ? Optional.empty()
					: grandParent.getChildren().stream().filter(DOMNode::isElement)
							.filter(node -> GROUP_ID_ELT.equals(node.getLocalName()))
							.flatMap(node -> node.getChildren().stream()).map(DOMNode::getTextContent)
							.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).findFirst();
			Optional<String> artifactId = grandParent == null ? Optional.empty()
					: grandParent.getChildren().stream().filter(DOMNode::isElement)
							.filter(node -> ARTIFACT_ID_ELT.equals(node.getLocalName()))
							.flatMap(node -> node.getChildren().stream()).map(DOMNode::getTextContent)
							.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).findFirst();
			GAVInsertionStrategy gavInsertionStrategy = computeGAVInsertionStrategy(request);
			List<ArtifactWithDescription> allArtifactInfos = Collections.synchronizedList(new ArrayList<>());
			LinkedHashMap<String, CompletionItem> nonArtifactCollector = new LinkedHashMap<>();
			switch (parent.getLocalName()) {
			case SCOPE_ELT:
				collectSimpleCompletionItems(Arrays.asList(DependencyScope.values()), DependencyScope::getName,
						DependencyScope::getDescription, request).forEach(response::addCompletionAttribute);
				break;
			case PHASE_ELT:
				collectSimpleCompletionItems(Arrays.asList(Phase.ALL_STANDARD_PHASES), phase -> phase.id,
						phase -> phase.description, request).forEach(response::addCompletionAttribute);
				break;
			case GROUP_ID_ELT:
				if (isParentDeclaration) {
					Optional<MavenProject> filesystem = computeFilesystemParent(request);
					if (filesystem.isPresent()) {
						filesystem.map(MavenProject::getGroupId)
						.map(g -> toCompletionItem(g.toString(), null, request.getReplaceRange()))
						.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
						.ifPresent(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
					}
					// TODO localRepo
					// TODO remoteRepos
				} else {
					// TODO if artifactId is set and match existing content, suggest only matching
					// groupId
					collectSimpleCompletionItems(
							isPlugin ? plugin.getLocalRepositorySearcher().searchPluginGroupIds()
									: plugin.getLocalRepositorySearcher().searchGroupIds(),
							Function.identity(), Function.identity(), request).stream()
								.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
								.forEach(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
					internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, nonArtifactCollector, cancelChecker);
				}
				internalCollectWorkspaceArtifacts(request, allArtifactInfos, nonArtifactCollector, groupId, artifactId);
	
				// Sort and move nonArtifactCollector items to the response and clear nonArtifactCollector
				nonArtifactCollector.entrySet().stream()
				.map(entry -> entry.getValue()).forEach(response::addCompletionItem);
				nonArtifactCollector.clear();
				break;
			case ARTIFACT_ID_ELT:
				if (isParentDeclaration) {
					Optional<MavenProject> filesystem = computeFilesystemParent(request);
					if (filesystem.isPresent()) {
						filesystem.map(ArtifactWithDescription::new).ifPresent(allArtifactInfos::add);
					}
					// TODO localRepo
					// TODO remoteRepos
				} else {
					allArtifactInfos.addAll((isPlugin ? plugin.getLocalRepositorySearcher().getLocalPluginArtifacts()
							: plugin.getLocalRepositorySearcher().getLocalArtifactsLastVersion()).stream()
									.filter(gav -> !groupId.isPresent() || gav.getGroupId().equals(groupId.get()))
									// TODO pass description as documentation
									.map(ArtifactWithDescription::new).collect(Collectors.toList()));
					internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, nonArtifactCollector, cancelChecker);
				}
				internalCollectWorkspaceArtifacts(request, allArtifactInfos, nonArtifactCollector, groupId, artifactId);
				break;
			case VERSION_ELT:
				if (isParentDeclaration) {
					Optional<MavenProject> filesystem = computeFilesystemParent(request);
					if (filesystem.isPresent()) {
						filesystem.map(MavenProject::getVersion).map(DefaultArtifactVersion::new)
						.map(version -> toCompletionItem(version.toString(), null, request.getReplaceRange()))
						.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
						.ifPresent(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
					}
					// TODO localRepo
					// TODO remoteRepos
				} else {
					if (artifactId.isPresent()) {
						plugin.getLocalRepositorySearcher().getLocalArtifactsLastVersion().stream()
								.filter(gav -> gav.getArtifactId().equals(artifactId.get()))
								.filter(gav -> !groupId.isPresent() || gav.getGroupId().equals(groupId.get())).findAny()
								.map(Artifact::getVersion).map(DefaultArtifactVersion::new)
								.map(version -> toCompletionItem(version.toString(), null, request.getReplaceRange()))
								.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
								.ifPresent(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
						internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, nonArtifactCollector, cancelChecker);
					}
				}
				internalCollectWorkspaceArtifacts(request, allArtifactInfos, nonArtifactCollector, groupId, artifactId);
	
				if (nonArtifactCollector.isEmpty()) {
					response.addCompletionItem(toTextCompletionItem(request, "-SNAPSHOT"));
				} else {
					// Sort and move nonArtifactCollector items to the response and clear nonArtifactCollector
					final AtomicInteger sortIndex = new AtomicInteger(0);
					nonArtifactCollector.entrySet().stream()
						.map(entry -> entry.getValue())
						.filter(Objects::nonNull)
						.filter(item -> item.getSortText() != null || item.getLabel() != null)
						.sorted(new Comparator<CompletionItem>() {
							// Sort in reverse order to correctly fill 'sortText' 
							@Override
							public int compare(CompletionItem o1, CompletionItem o2) {
								String sortText1 = o1.getSortText() != null ? o1.getSortText() : o1.getLabel();
								String sortText2 = o2.getSortText() != null ? o2.getSortText() : o2.getLabel();
								return new DefaultArtifactVersion(sortText2)
										.compareTo(new DefaultArtifactVersion(sortText1));
							}
						})
						.peek(item -> item.setSortText(String.format("%06d", (sortIndex.getAndIncrement())) + '.' + item.getLabel()))
						.forEach(response::addCompletionItem);
					nonArtifactCollector.clear();
				}
				break;
			case MODULE_ELT:
				collectSubModuleCompletion(request).forEach(response::addCompletionItem);
				break;
			case TARGET_PATH_ELT:
			case DIRECTORY_ELT:
			case SOURCE_DIRECTORY_ELT:
			case SCRIPT_SOURCE_DIRECTORY_ELT:
			case TEST_SOURCE_DIRECTORY_ELT:
			case OUTPUT_DIRECTORY_ELT:
			case TEST_OUTPUT_DIRECTORY_ELT:
				collectRelativeDirectoryPathCompletion(request).forEach(response::addCompletionItem);
				break;
			case FILTER_ELT:
				if (FILTERS_ELT.equals(grandParent.getLocalName())) {
					collectRelativeFilterPathCompletion(request).forEach(response::addCompletionItem);
				}
				break;
			case EXISTS_ELT:
			case MISSING_ELT:
				if (FILE_ELT.equals(grandParent.getLocalName())) {
					collectRelativeAnyPathCompletion(request).forEach(response::addCompletionItem);
				}
				break;
			case RELATIVE_PATH_ELT:
				collectRelativePathCompletion(request).forEach(response::addCompletionItem);
				break;
			case DEPENDENCIES_ELT:
			case DEPENDENCY_ELT:
				// TODO completion/resolve to get description for local artifacts
				allArtifactInfos.addAll(plugin.getLocalRepositorySearcher().getLocalArtifactsLastVersion().stream()
						.map(ArtifactWithDescription::new).collect(Collectors.toList()));
				internalCollectRemoteGAVCompletion(request, false, allArtifactInfos, nonArtifactCollector, cancelChecker);
				break;
			case PLUGINS_ELT:
			case PLUGIN_ELT:
				// TODO completion/resolve to get description for local artifacts
				allArtifactInfos.addAll(plugin.getLocalRepositorySearcher().getLocalPluginArtifacts().stream()
						.map(ArtifactWithDescription::new).collect(Collectors.toList()));
				internalCollectRemoteGAVCompletion(request, true, allArtifactInfos, nonArtifactCollector, cancelChecker);
				break;
			case PARENT_ELT:
				Optional<MavenProject> filesystem = computeFilesystemParent(request);
				if (filesystem.isPresent()) {
					filesystem.map(ArtifactWithDescription::new).ifPresent(allArtifactInfos::add);
				} else {
					// TODO localRepo
					// TODO remoteRepos
				}
				break;
			case GOAL_ELT:
				collectGoals(request).forEach(response::addCompletionItem);
				break;
			case PACKAGING_ELT:
				collectPackaging(request).forEach(response::addCompletionItem);
				break;
			default:
				Set<MojoParameter> parameters = MavenPluginUtils.collectPluginConfigurationMojoParameters(request, plugin)
						.stream().filter(p -> p.name.equals(parent.getLocalName()))
						.filter(p -> (p.type.startsWith(FILE_TYPE)) || 
								(p.type.startsWith(STRING_TYPE) && p.name.toLowerCase().endsWith(DIRECTORY_STRING_LC)))
						.collect(Collectors.toSet());
				if (parameters != null && parameters.size() > 0) {
					collectMojoParametersDefaultCompletion(request, parameters)
					.forEach(response::addCompletionItem);
					if (parameters.stream()
							.anyMatch(p -> !p.name.toLowerCase().endsWith(DIRECTORY_STRING_LC))) {
						// Show all files
						collectRelativeAnyPathCompletion(request).forEach(response::addCompletionItem);
					} else {
						// Show only directories
						collectRelativeDirectoryPathCompletion(request).forEach(response::addCompletionItem);
					}
				}
			}				
			if (!allArtifactInfos.isEmpty()) {
				Comparator<ArtifactWithDescription> artifactInfoComparator = Comparator
						.comparing(artifact -> new DefaultArtifactVersion(artifact.artifact.getVersion()));
				final Comparator<ArtifactWithDescription> highestVersionWithDescriptionComparator = artifactInfoComparator
						.thenComparing(
								artifactInfo -> artifactInfo.description != null ? artifactInfo.description : "");
				allArtifactInfos.stream()
						.collect(Collectors.groupingBy(artifact -> artifact.artifact.getGroupId() + ":" + artifact.artifact.getArtifactId()))
						.values().stream()
						.map(artifacts -> Collections.max(artifacts, highestVersionWithDescriptionComparator))
						.map(artifactInfo -> toGAVCompletionItem(artifactInfo, request, gavInsertionStrategy))
						.filter(completionItem -> !response.hasAttribute(completionItem.getLabel()))
						.forEach(response::addCompletionItem);
			}
			if (request.getNode().isText()) {
				completeProperties(request).forEach(response::addCompletionAttribute);
			}
		} catch(MavenInitializationException e) {
			// Maven is initializing, catch the error to avoid breaking XML completion from LemMinX
		}
	}
	
	private CompletionItem toTextCompletionItem(ICompletionRequest request, String text) throws BadLocationException {
		CompletionItem res = new CompletionItem(text);
		res.setFilterText(text);
		TextEdit edit = new TextEdit();
		edit.setNewText(text);
		res.setTextEdit(Either.forLeft(edit));
		Position endOffset = request.getXMLDocument().positionAt(request.getOffset());
		for (int startOffset = Math.max(0, request.getOffset() - text.length()); startOffset <= request
				.getOffset(); startOffset++) {
			String prefix = request.getXMLDocument().getText().substring(startOffset, request.getOffset());
			if (text.startsWith(prefix)) {
				edit.setRange(new Range(request.getXMLDocument().positionAt(startOffset), endOffset));
				return res;
			}
		}
		edit.setRange(new Range(endOffset, endOffset));
		return res;
	}

	private GAVInsertionStrategy computeGAVInsertionStrategy(ICompletionRequest request) {
		if (request.getParentElement() == null) {
			return null;
		}
		return switch (request.getParentElement().getLocalName()) {
			case DEPENDENCIES_ELT -> new GAVInsertionStrategy.NodeWithChildrenInsertionStrategy(DEPENDENCY_ELT);
			case DEPENDENCY_ELT -> GAVInsertionStrategy.CHILDREN_ELEMENTS;
			case PLUGINS_ELT -> new GAVInsertionStrategy.NodeWithChildrenInsertionStrategy(PLUGIN_ELT);
			case PLUGIN_ELT ->  GAVInsertionStrategy.CHILDREN_ELEMENTS;
			case ARTIFACT_ID_ELT -> GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING;
			case PARENT_ELT -> GAVInsertionStrategy.CHILDREN_ELEMENTS;
			default -> GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING;
		};
	}

	@SuppressWarnings("deprecation")
	private Optional<MavenProject> computeFilesystemParent(ICompletionRequest request) {
		Optional<String> relativePath = null;
		if (request.getParentElement().getLocalName().equals(PARENT_ELT)) {
			relativePath = DOMUtils.findChildElementText(request.getNode(), RELATIVE_PATH_ELT);
		} else {
			relativePath = DOMUtils.findChildElementText(request.getParentElement().getParentElement(),
					RELATIVE_PATH_ELT);
		}
		if (!relativePath.isPresent()) {
			relativePath = Optional.of("..");
		}
		File referencedTargetPomFile = new File(
				new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile(),
				relativePath.orElse(""));
		if (referencedTargetPomFile.isDirectory()) {
			referencedTargetPomFile = new File(referencedTargetPomFile, Maven.POMv4);
		}
		if (referencedTargetPomFile.isFile()) {
			return plugin.getProjectCache().getSnapshotProject(referencedTargetPomFile);
		}
		return Optional.empty();
	}

	private CompletionItem createMinimalPOMCompletionSnippet(ICompletionRequest request)
			throws IOException, BadLocationException {
		CompletionItem item = new CompletionItem("minimal pom content");
		item.setKind(CompletionItemKind.Snippet);
		item.setInsertTextFormat(InsertTextFormat.Snippet);
		Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setArtifactId(
				new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile().getName());
		MavenXpp3Writer writer = new MavenXpp3Writer();
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			writer.write(stream, model);
			TextEdit textEdit = new TextEdit(
					new Range(new Position(0, 0),
							request.getXMLDocument().positionAt(request.getXMLDocument().getText().length())),
					new String(stream.toByteArray()));
			item.setTextEdit(Either.forLeft(textEdit));
		}
		return item;
	}

	private Collection<CompletionItem> collectGoals(ICompletionRequest request) {
		PluginDescriptor pluginDescriptor;
		try {
			pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request.getNode(), plugin);
			return collectSimpleCompletionItems(pluginDescriptor.getMojos(), MojoDescriptor::getGoal,
					MojoDescriptor::getDescription, request);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return Collections.emptySet();
	}

	private Collection<CompletionItem> collectPackaging(ICompletionRequest request) {
		Set<String> packagingTypes = new LinkedHashSet<>();
		packagingTypes.add(PACKAGING_TYPE_JAR);
		packagingTypes.add(PACKAGING_TYPE_WAR);
		packagingTypes.add(PACKAGING_TYPE_EAR);
		packagingTypes.add(PACKAGING_TYPE_EJB);
		packagingTypes.add(PACKAGING_TYPE_POM);
		packagingTypes.add(PACKAGING_TYPE_MAVEN_PLUGIN);
		
		// dynamically load available packaging types from build plugins
		updateAvailablePackagingTypes(packagingTypes, request); 

		return packagingTypes.stream().map(type -> {
			try {
				CompletionItem item = toTextCompletionItem(request, type);
				item.setDocumentation("Packagng Type: " + (type != null ? type : "unknown"));
				item.setKind(CompletionItemKind.Value);
				item.setSortText(type != null ? type : "zzz");
				return item;
			} catch (BadLocationException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				return toErrorCompletionItem(e);
			}
		}).collect(Collectors.toList());
	}

	private void updateAvailablePackagingTypes(Set<String> packagingTypes, ICompletionRequest request) {
		MavenProject project = plugin.getProjectCache().getSnapshotProject(request.getXMLDocument(), null, false);
		if (project == null) {
			return;
		}

		for (Plugin plugin : project.getBuildPlugins()) {
			if (plugin.isExtensions()) {
				Artifact artifact = new DefaultArtifact(
						plugin.getGroupId(), plugin.getArtifactId(),
						null, plugin.getVersion());
				addPluginPackagingTypes(packagingTypes, artifact);
			}
		}
	}	
	
	/**
	 * Parses the plugin's META-INF/plexus/components.xml file for available
	 * packaging types
	 * 
	 * @param packagingTypes Set of packaging types that this method will add to
	 * @param artifact       The artifact of the build plugin
	 * @apiNote If any exceptions occur during this method, such as an XML parsing
	 *          exception or file not found, this method will immediately stop. It
	 *          is assumed that there is something wrong with the user's project or
	 *          repository setup which prevents this method from completing.
	 */
	private void addPluginPackagingTypes(Set<String> packagingTypes, Artifact artifact) {
		File artifactPomFile = plugin.getLocalRepositorySearcher().findLocalFile(artifact);
		if (artifactPomFile == null) {
			return;
		}

		File artifactJarFile = new File(artifactPomFile.getParentFile().getAbsoluteFile(),
				artifact.getArtifactId() + '-' + artifact.getVersion() + JAR_EXT);
		try (JarFile jarFile = new JarFile(artifactJarFile.getAbsoluteFile())) {
			DocumentBuilder db = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
			JarEntry componentsxml = jarFile.getJarEntry(COMPONENTS_PATH);
			if (componentsxml != null) {
				Document doc = db.parse(jarFile.getInputStream(componentsxml));
				if (doc.getDocumentElement() != null) {
					doc.getDocumentElement().normalize();
					NodeList components = doc.getElementsByTagName(COMPONENTS_COMPONENT_ELT);
					for (int i = 0; i < components.getLength(); i++) {
						Node component = components.item(i);
						if (component.getNodeType() == Node.ELEMENT_NODE) {
							Element element = (Element) component;
							String role = element.getElementsByTagName(COMPONENTS_ROLE_ELT).item(0).getTextContent();
							if (ArtifactHandler.ROLE.equals(role)) {
								Node config = element.getElementsByTagName(COMPONENTS_CONFIGURATION_ELT).item(0);
								if (config.getNodeType() == Node.ELEMENT_NODE) {
									Element configEl = (Element) config;
									String name = configEl.getElementsByTagName(COMPONENTS_TYPE_ELT).item(0).getTextContent();
									packagingTypes.add(name);
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			// Broken XML, file not found, etc. Can't add packaging types.
		}
	}

	private CompletionItem toGAVCompletionItem(ArtifactWithDescription artifactInfo, ICompletionRequest request,
			GAVInsertionStrategy strategy) {
		boolean hasGroupIdSet = DOMUtils.findChildElementText(request.getParentElement().getParentElement(), GROUP_ID_ELT).isPresent() 
						|| DOMUtils.findChildElementText(request.getParentElement(), GROUP_ID_ELT).isPresent();
		boolean insertArtifactIsEnd = !request.getParentElement().hasEndTag();
		boolean insertGroupId = strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy || !hasGroupIdSet;
		boolean isExclusion = DOMUtils.findClosestParentNode(request.getNode(), DOMConstants.EXCLUSIONS_ELT) != null;
		boolean insertVersion = !isExclusion && (strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy || !DOMUtils
				.findChildElementText(request.getParentElement().getParentElement(), VERSION_ELT).isPresent());
		CompletionItem item = new CompletionItem();
		if (artifactInfo.description != null) {
			item.setDocumentation(artifactInfo.description);
		}
		DOMDocument doc = request.getXMLDocument();
		Range replaceRange = doc.getTextDocument().getWordRangeAt(request.getOffset(), ARTIFACT_ID_PATTERN);

		TextEdit textEdit = new TextEdit();
		item.setTextEdit(Either.forLeft(textEdit));
		textEdit.setRange(replaceRange);
		if (strategy == GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING) {
			item.setKind(CompletionItemKind.Value);
			textEdit.setRange(replaceRange);
			switch (request.getParentElement().getLocalName()) {
			case ARTIFACT_ID_ELT:
				item.setLabel(artifactInfo.artifact.getArtifactId()
						+ (insertGroupId || insertVersion
								? " - " + artifactInfo.artifact.getGroupId() + ":" + artifactInfo.artifact.getArtifactId() + ":"
										+ artifactInfo.artifact.getVersion()
								: ""));
				textEdit.setNewText(artifactInfo.artifact.getArtifactId()
						+ (insertArtifactIsEnd ? "</artifactId>" : ""));
				item.setFilterText(artifactInfo.artifact.getArtifactId());
				List<TextEdit> additionalEdits = new ArrayList<>(2);
				if (insertGroupId) {
					Position insertionPosition;
					try {
						insertionPosition = request.getXMLDocument()
								.positionAt(request.getParentElement().getParentElement().getStartTagCloseOffset() + 1);
						additionalEdits.add(new TextEdit(new Range(insertionPosition, insertionPosition),
								request.getLineIndentInfo().getLineDelimiter()
										+ request.getLineIndentInfo().getWhitespacesIndent() + "<groupId>"
										+ artifactInfo.artifact.getGroupId() + "</groupId>"));
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						LOGGER.log(Level.SEVERE, e.getMessage(), e);
					}
				}
				if (insertVersion) {
					Position insertionPosition;
					try {
						insertionPosition = insertArtifactIsEnd ? replaceRange.getEnd() :
								request.getXMLDocument().positionAt(request.getParentElement().getEndTagCloseOffset() + 1);
						
						additionalEdits.add(new TextEdit(new Range(insertionPosition, insertionPosition),
								request.getLineIndentInfo().getLineDelimiter()
										+ request.getLineIndentInfo().getWhitespacesIndent() + "<version>"
										+ artifactInfo.artifact.getVersion()
										+ "</version>"));
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						LOGGER.log(Level.SEVERE, e.getMessage(), e);
					}
				}
				if (!additionalEdits.isEmpty()) {
					item.setAdditionalTextEdits(additionalEdits);
				}
				return item;
			case GROUP_ID_ELT:
				item.setLabel(artifactInfo.artifact.getGroupId());
				textEdit.setNewText(artifactInfo.artifact.getGroupId());
				return item;
			case VERSION_ELT:
				item.setLabel(artifactInfo.artifact.getVersion());
				textEdit.setNewText(artifactInfo.artifact.getVersion());
				return item;
			}
		} else {
			item.setLabel(artifactInfo.artifact.getArtifactId() + " - " + artifactInfo.artifact.getGroupId() + ":"
					+ artifactInfo.artifact.getArtifactId() + ":" + artifactInfo.artifact.getVersion());
			item.setKind(CompletionItemKind.Struct);
			item.setFilterText(artifactInfo.artifact.getArtifactId());
			try {
				textEdit.setRange(replaceRange);
				String newText = "";
				String suffix = "";
				String gavElementsIndent = request.getLineIndentInfo().getWhitespacesIndent();
				String oneLevelIndent = DOMUtils.getOneLevelIndent(request);
				String lineDelimiter = request.getLineIndentInfo().getLineDelimiter();
				if (strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy nodeWithChildren) {
					String elementName = nodeWithChildren.elementName;
					newText += "<" + elementName + ">" + lineDelimiter + gavElementsIndent + oneLevelIndent; 
					suffix = lineDelimiter + gavElementsIndent + "</" + elementName + ">";
					gavElementsIndent += oneLevelIndent;
				}
				if (insertGroupId) {
					newText += "<groupId>" + artifactInfo.artifact.getGroupId() + "</groupId>"
							+ lineDelimiter + gavElementsIndent;
				}
				newText += "<artifactId>" + artifactInfo.artifact.getArtifactId() + "</artifactId>";
				if (insertVersion) {
					newText += lineDelimiter + gavElementsIndent;
					newText += "<version>" + artifactInfo.artifact.getVersion() + "</version>";
				}
				newText += suffix;
				textEdit.setNewText(newText);
			} catch (BadLocationException ex) {
				LOGGER.log(Level.SEVERE, ex.getCause().toString(), ex);
				return null;
			}
		}
		return item;
	}

	private static CompletionItem toTag(String name, MarkupContent description, ICompletionRequest request) {
		CompletionItem res = new CompletionItem(name);
		res.setDocumentation(Either.forRight(description));
		res.setInsertTextFormat(InsertTextFormat.Snippet);
		TextEdit edit = new TextEdit();
		edit.setNewText('<' + name + ">$0</" + name + '>');
		edit.setRange(request.getReplaceRange());
		res.setTextEdit(Either.forLeft(edit));
		res.setKind(CompletionItemKind.Field);
		res.setFilterText(edit.getNewText());
		res.setSortText(name);
		return res;
	}

	private Collection<CompletionItem> completeProperties(ICompletionRequest request) {
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return Collections.emptySet();
		}
		Map<String, String> allProps = ParticipantUtils.getMavenProjectProperties(project);

		return allProps.entrySet().stream().map(property -> {
			try {
				CompletionItem item = toTextCompletionItem(request, "${" + property.getKey() + '}');
				item.setDocumentation(
						"Default Value: " + (property.getValue() != null ? property.getValue() : "unknown"));
				item.setKind(CompletionItemKind.Property);
				// '$' sorts before alphabet characters, so we add z to make it appear later in
				// proposals
				item.setSortText("z${" + property.getKey() + "}");
				if (property.getKey().contains("env.")) {
					// We don't want environment variables at the top of the completion proposals
					item.setSortText("z${zzz" + property.getKey() + "}");
				}
				return item;
			} catch (BadLocationException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				return toErrorCompletionItem(e);
			}
		}).collect(Collectors.toList());
	}

	private CompletionItem toErrorCompletionItem(Throwable e) {
		CompletionItem res = new CompletionItem("Error: " + e.getMessage());
		res.setDocumentation(ExceptionUtils.getStackTrace(e));
		res.setInsertText("");
		return res;
	}

	private void internalCollectRemoteGAVCompletion(ICompletionRequest request, boolean onlyPlugins,
			Collection<ArtifactWithDescription> artifactInfosCollector, LinkedHashMap<String, CompletionItem> nonArtifactCollector, CancelChecker cancelChecker) {
		DOMElement node = request.getParentElement();
		Dependency artifactToSearch = MavenParseUtils.parseArtifact(node);
		if (artifactToSearch == null) {
			return;
		}
		DOMDocument doc = request.getXMLDocument();
		int startTagCloseOffset = node.getStartTagCloseOffset() >= 0 ? node.getStartTagCloseOffset() : 0;
		int endTagOpenOffset = node.getEndTagOpenOffset() >= 0 ? node.getEndTagOpenOffset() : request.getOffset();
		Range range = XMLPositionUtility.createRange(startTagCloseOffset + 1, endTagOpenOffset, doc);
		Set<CompletionItem> updateItems = Collections.synchronizedSet(new HashSet<>(1));
		final CompletionItem updatingItem = new CompletionItem(WAITING_LABEL);
		updatingItem.setPreselect(false);
		updatingItem.setInsertText("");
		updatingItem.setKind(CompletionItemKind.Event);
		updateItems.add(updatingItem);

		plugin.getCentralSearcher().ifPresent(centralSearcher -> {
			try {
				CompletableFuture.runAsync(() -> {
						cancelChecker.checkCanceled();
						switch (node.getLocalName()) {
						case GROUP_ID_ELT:
							// TODO: just pass only plugins boolean, and make getGroupId's accept a boolean
							// parameter
							(onlyPlugins ?
									centralSearcher.getPluginGroupIds(artifactToSearch) :
									centralSearcher.getGroupIds(artifactToSearch))
								.stream() //
								.map(groupId -> toCompletionItem(groupId, null, range)) //
								.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
								.forEach(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
							cancelChecker.checkCanceled();
							return;
						case ARTIFACT_ID_ELT:
							(onlyPlugins ?
									centralSearcher.getPluginArtifacts(artifactToSearch) :
									centralSearcher.getArtifacts(artifactToSearch))
								.stream() //
								.map(ArtifactWithDescription::new) //
								.forEach(artifactInfosCollector::add);
							cancelChecker.checkCanceled();
							return;
						case VERSION_ELT:
							(onlyPlugins ?
									centralSearcher.getPluginArtifactVersions(artifactToSearch) :
									centralSearcher.getArtifactVersions(artifactToSearch))
								.stream() //
								.map(version -> toCompletionItem(version.toString(), null, range)) //
								.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
								.forEach(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
							cancelChecker.checkCanceled();
							return;
						case DEPENDENCIES_ELT:
						case DEPENDENCY_ELT:
							centralSearcher.getArtifacts(artifactToSearch).stream().map(ArtifactWithDescription::new).forEach(artifactInfosCollector::add);
							cancelChecker.checkCanceled();
							return;
						case PLUGINS_ELT:
						case PLUGIN_ELT:
							centralSearcher.getPluginArtifacts(artifactToSearch).stream().map(ArtifactWithDescription::new).forEach(artifactInfosCollector::add);
							cancelChecker.checkCanceled();
							return;
						}
					}).whenComplete((ok, error) -> updateItems.remove(updatingItem)).get(2, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException exception) {
				LOGGER.log(Level.SEVERE, exception.getCause() != null ? exception.getCause().toString() : exception.getMessage(), exception);
			} catch (TimeoutException e) {
				// nothing to log, some work still pending
				updateItems.forEach(updateItem -> nonArtifactCollector.put(updateItem.getLabel(), updatingItem));
			}
		});
	}

	@SuppressWarnings("deprecation")
	private Collection<CompletionItem> collectSubModuleCompletion(ICompletionRequest request) {
		DOMDocument doc = request.getXMLDocument();
		File docFolder = new File(URI.create(doc.getTextDocument().getUri())).getParentFile();
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (!prefix.isEmpty() && !prefix.endsWith("/")) {
			files.addAll(Arrays.asList(
					prefixFile.getParentFile().listFiles((dir, name) -> name.startsWith(prefixFile.getName()))));
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles(File::isDirectory)));
		}
		// make folder that have a pom show higher
		files.sort(Comparator.comparing((File file) -> new File(file, Maven.POMv4).exists()).reversed()
				.thenComparing(Function.identity()));
		if (prefix.isEmpty()) {
			files.add(docFolder.getParentFile());
		}
		return files.stream().map(file -> toFileCompletionItem(file, docFolder, request)).collect(Collectors.toList());
	}

	private Collection<CompletionItem> collectMojoParametersDefaultCompletion(ICompletionRequest request, Set<MojoParameter> parameters) {
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		List<String> defaultValues = parameters.stream().filter(p -> (p.getDefaultValue() != null))
				.map(p -> p.getDefaultValue())
				.collect(Collectors.toList());

		if (!prefix.isEmpty()) {
			defaultValues = defaultValues.stream().filter(v -> v.startsWith(prefix))
					.collect(Collectors.toList());
		}					
		return	defaultValues.stream()
			 	.sorted(String.CASE_INSENSITIVE_ORDER)
				.map(defaultValue -> toCompletionItem(defaultValue.toString(), null, request.getReplaceRange()))
				.collect(Collectors.toList());
	}
	
	@SuppressWarnings("deprecation")
	private Collection<CompletionItem> collectRelativePathCompletion(ICompletionRequest request) {
		DOMDocument doc = request.getXMLDocument();
		File docFile = new File(URI.create(doc.getTextDocument().getUri()));
		File docFolder = docFile.getParentFile();
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (prefix.isEmpty()) {
			Arrays.stream(docFolder.getParentFile().listFiles()).filter(file -> file.getName().contains(PARENT_ELT))
					.map(file -> new File(file, Maven.POMv4)).filter(File::isFile).forEach(files::add);
			files.add(docFolder.getParentFile());
		} else {
			try {
				prefixFile = prefixFile.getCanonicalFile();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			if (!prefix.endsWith("/")) {
				final File thePrefixFile = prefixFile;
				files.addAll(Arrays.asList(prefixFile.getParentFile()
						.listFiles(file -> file.getName().startsWith(thePrefixFile.getName()))));
			}
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles()));
		}
		return files.stream().filter(file -> file.getName().equals(Maven.POMv4) || file.isDirectory())
				.filter(file -> !(file.equals(docFolder) || file.equals(docFile))).flatMap(file -> {
					if (docFile.toPath().startsWith(file.toPath()) || file.getName().contains(PARENT_ELT)) {
						File pomFile = new File(file, Maven.POMv4);
						if (pomFile.exists()) {
							return Stream.of(pomFile, file);
						}
					}
					return Stream.of(file);
				}).sorted(Comparator.comparing(File::isFile) // pom files before folders
						.thenComparing(
								file -> (file.isFile() && docFile.toPath().startsWith(file.getParentFile().toPath()))
										|| (file.isDirectory() && file.equals(docFolder.getParentFile()))) // `../pom.xml`
																											// before
																											// ...
						.thenComparing(file -> file.getParentFile().getName().contains(PARENT_ELT)) // folders
																									// containing
																									// "parent"
																									// before...
						.thenComparing(file -> file.getParentFile().getParentFile().equals(docFolder.getParentFile())) // siblings
																														// before...
						.reversed().thenComparing(Function.identity())// other folders and files
				).map(file -> toFileCompletionItem(file, docFolder, request)).collect(Collectors.toList());
	}

	private Collection<CompletionItem> collectRelativeAnyPathCompletion(ICompletionRequest request) {
		DOMDocument doc = request.getXMLDocument();
		File docFile = new File(URI.create(doc.getTextDocument().getUri()));
		File docFolder = docFile.getParentFile();
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (prefix.isEmpty()) {
			files.add(docFolder.getParentFile());
		} else {
			try {
				prefixFile = prefixFile.getCanonicalFile();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			if (!prefix.endsWith("/")) {
				final File thePrefixFile = prefixFile;
				files.addAll(Arrays.asList(prefixFile.getParentFile()
						.listFiles(file -> file.getName().startsWith(thePrefixFile.getName()))));
			}
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles()));
		}
		return files.stream().filter(file -> file.isFile() || file.isDirectory())
					.sorted(Comparator.comparing(File::isFile) // files before folders
						.thenComparing(
								file -> (file.isFile() && docFile.toPath().startsWith(file.getParentFile().toPath()))
										|| (file.isDirectory() && file.equals(docFolder.getParentFile()))) // `files before
						.thenComparing(file -> file.getParentFile().getName().contains(PARENT_ELT)) // folders
																									// containing
																									// "parent"
																									// before...
						.thenComparing(file -> file.getParentFile().getParentFile().equals(docFolder.getParentFile())) // siblings
																														// before...
						.reversed().thenComparing(Function.identity())// other folders and files
				).map(file -> toFileCompletionItem(file, docFolder, request)).collect(Collectors.toList());
	}
	
	private Collection<CompletionItem> collectRelativeDirectoryPathCompletion(ICompletionRequest request) {
		DOMDocument doc = request.getXMLDocument();
		File docFile = new File(URI.create(doc.getTextDocument().getUri()));
		File docFolder = docFile.getParentFile();
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (prefix.isEmpty()) {
			files.add(docFolder.getParentFile());
		} else {
			try {
				prefixFile = prefixFile.getCanonicalFile();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			if (!prefix.endsWith("/")) {
				final File thePrefixFile = prefixFile;
				files.addAll(Arrays.asList(prefixFile.getParentFile()
						.listFiles(file -> file.getName().startsWith(thePrefixFile.getName()))));
			}
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles()));
		}
		return files.stream().filter(file -> file.isDirectory())
				.filter( file -> !file.equals(docFolder))
				.sorted(Comparator.comparing(File::isDirectory) // only folders
						.thenComparing(file -> (file.isDirectory() && file.equals(docFolder.getParentFile())))
						.thenComparing(file -> file.getParentFile().getName().contains(PARENT_ELT)) // folders containing
																									// "parent" before...
						.thenComparing(file -> file.getParentFile().getParentFile().equals(docFolder.getParentFile())) // siblings before...
						.reversed().thenComparing(Function.identity())// other folders and files
				).map(file -> toFileCompletionItem(file, docFolder, request)).collect(Collectors.toList());
	}

	private List<File> collectRelativePropertiesFiles(File parent) {
		List<File> result = new ArrayList<>();
		List<File> parentFiles = Arrays.asList(parent.listFiles());
		
		parentFiles.stream().filter(file -> (file.isFile() && file.getName().endsWith(".properties")))
			.forEach(file -> result.add(file));
		parentFiles.stream().filter(file -> (file.isDirectory()))
			.forEach(file -> result.addAll(collectRelativePropertiesFiles(file)));
		return result;
	}
	
	private Collection<CompletionItem> collectRelativeFilterPathCompletion(ICompletionRequest request) {
		DOMDocument doc = request.getXMLDocument();
		File docFile = new File(URI.create(doc.getTextDocument().getUri()));
		File docFolder = docFile.getParentFile();
		String prefix = request.getNode().getNodeValue() != null ? request.getNode().getNodeValue() : "";
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (!prefix.isEmpty()) {
			try {
				prefixFile = prefixFile.getCanonicalFile();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			if (!prefix.endsWith("/")) {
				final File thePrefixFile = prefixFile;
				files.addAll(Arrays.asList(prefixFile.getParentFile()
						.listFiles(file -> (file.getName().startsWith(thePrefixFile.getName())
								&& file.getName().endsWith(".properties")))));
			}
		}
		if (prefixFile.isDirectory()) {
			files.addAll(collectRelativePropertiesFiles(prefixFile));
		}
		return files.stream()
				.sorted(Comparator.comparing(File::isFile) // pom files before folders
						.thenComparing(
								file -> (file.isFile() && docFile.toPath().startsWith(file.getParentFile().toPath())))
						.reversed().thenComparing(Function.identity())// other folders and files
				).map(file -> toFileCompletionItem(file, docFolder, request)).collect(Collectors.toList());
	}

	private CompletionItem toFileCompletionItem(File file, File referenceFolder, ICompletionRequest request) {
		CompletionItem res = new CompletionItem();
		Path path = referenceFolder.toPath().relativize(file.toPath());
		StringBuilder builder = new StringBuilder(path.toString().length());
		Path current = path;
		while (current != null) {
			if (!current.equals(path)) {
				// Only append "/" for parent directories
				builder.insert(0, '/');
			}
			builder.insert(0, current.getFileName());
			current = current.getParent();
		}

		Range replaceRange = request.getReplaceRange();

		/*
		 * Temporary workaround for https://github.com/eclipse/lemminx-maven/pull/127
		 * Workaround involves overriding the replace range of the entire node text
		 * rather than some portion after the file separator. TODO: Remove try catch
		 * block after upstream fix has been merged. Upstream fix:
		 * https://github.com/eclipse/lemminx/issues/723
		 */
		try {
			DOMElement parentElement = request.getParentElement();
			int startOffset = parentElement.getStartTagCloseOffset() + 1;
			Position start = parentElement.getOwnerDocument().positionAt(startOffset);
			Position end = request.getPosition();
			int endOffset = parentElement.getEndTagOpenOffset();
			if (endOffset > 0) {
				end = request.getXMLDocument().positionAt(endOffset);
			}
			replaceRange = new Range(start, end);
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}

		String pathString = builder.toString();
		res.setLabel(pathString);
		res.setFilterText(pathString);
		res.setKind(file.isDirectory() ? CompletionItemKind.Folder : CompletionItemKind.File);
		res.setTextEdit(Either.forLeft(new TextEdit(replaceRange, pathString)));

		return res;
	}

	private <T> Collection<CompletionItem> collectSimpleCompletionItems(Collection<T> items,
			Function<T, String> insertionTextExtractor, Function<T, String> documentationExtractor,
			ICompletionRequest request) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		boolean needClosingTag = node.getEndTagOpenOffset() == DOMNode.NULL_VALUE;
		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1,
				needClosingTag ? node.getStartTagOpenOffset() + 1 : node.getEndTagOpenOffset(), doc);
		final Set<String> collectedItemLabels = Collections.synchronizedSet(new HashSet<>());

		return items.stream().map(o -> {
			String label = insertionTextExtractor.apply(o);
			CompletionItem item = new CompletionItem();
			item.setLabel(label);
			String insertText = label + (needClosingTag ? "</" + node.getTagName() + ">" : "");
			item.setKind(CompletionItemKind.Property);
			item.setDocumentation(Either.forLeft(documentationExtractor.apply(o)));
			item.setFilterText(insertText);
			item.setTextEdit(Either.forLeft(new TextEdit(range, insertText)));
			item.setInsertTextFormat(InsertTextFormat.PlainText);
			return item;
		})
		.filter(completionItem -> {
			if (!collectedItemLabels.contains(completionItem.getLabel())) {
				collectedItemLabels.add(completionItem.getLabel());
				return true;
			}
			return false;
		})
		.collect(Collectors.toList());
	}

	/**
	 * Utility function, takes a label string, description and range and returns a
	 * CompletionItem
	 *
	 * @param description Completion description
	 * @param label       Completion label
	 * @return CompletionItem resulting from the label, description and range given
	 * @param range Range where the completion will be inserted
	 */
	private static CompletionItem toCompletionItem(String label, String description, Range range) {
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		item.setSortText(label);
		item.setKind(CompletionItemKind.Property);
		String insertText = label;
		if (description != null) {
			item.setDocumentation(Either.forLeft(description));
		}
		item.setFilterText(insertText);
		item.setInsertTextFormat(InsertTextFormat.PlainText);
		item.setTextEdit(Either.forLeft(new TextEdit(range, insertText)));
		return item;
	}

	/*
	 * Utility function which can be passed as an argument to filter() to filter out
	 * duplicate elements by a property.
	 */
	public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
		Map<Object, Boolean> map = new ConcurrentHashMap<>();
		return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}
	
	private void internalCollectWorkspaceArtifacts(ICompletionRequest request, Collection<ArtifactWithDescription> artifactInfosCollector,
			LinkedHashMap<String, CompletionItem> nonArtifactCollector, Optional<String> groupId, Optional<String> artifactId) {
		DOMElement parent = request.getParentElement();
		if (parent == null || parent.getLocalName() == null) {
			return;
		}
		DOMElement grandParent = parent.getParentElement();
		if (grandParent == null || 
				(!PARENT_ELT.equals(grandParent.getLocalName()) &&
						!DEPENDENCY_ELT.equals(grandParent.getLocalName()) &&
						!PLUGIN_ELT.equals(grandParent.getLocalName()))) {
			return;
		}
		
		String groupIdFilter = groupId.orElse(null);
		String artifactIdFilter = artifactId.orElse(null);
		DOMDocument doc = request.getXMLDocument();
		int startTagCloseOffset = parent.getStartTagCloseOffset() >= 0 ? parent.getStartTagCloseOffset() : 0;
		int endTagOpenOffset = parent.getEndTagOpenOffset() >= 0 ? parent.getEndTagOpenOffset() : request.getOffset();
		Range range = XMLPositionUtility.createRange(startTagCloseOffset + 1, endTagOpenOffset, doc);

		switch (parent.getLocalName()) {
		case ARTIFACT_ID_ELT:
			plugin.getCurrentWorkspaceProjects().stream() //
				.filter(a -> groupIdFilter == null || groupIdFilter.equals(a.getGroupId()))
				.map(ArtifactWithDescription::new) //
				.forEach(artifactInfosCollector::add);
			break;
		case GROUP_ID_ELT:
			plugin.getCurrentWorkspaceProjects().stream() //
				.filter(p -> artifactIdFilter == null || artifactIdFilter.equals(p.getArtifactId())) //
				.map(p -> toCompletionItem(p.getGroupId(), null, range)) //
				.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
				.forEach(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
			;
			break;
		case VERSION_ELT:
			plugin.getCurrentWorkspaceProjects().stream() //
				.filter(p -> artifactIdFilter == null || artifactIdFilter.equals(p.getArtifactId())) //
				.map(p -> toCompletionItem(p.getVersion(), null, range)) //
				.filter(completionItem -> !nonArtifactCollector.containsKey(completionItem.getLabel()))
				.forEach(completionItem -> nonArtifactCollector.put(completionItem.getLabel(), completionItem));
			;
			break;
		}
	}
}
