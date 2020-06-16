/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.maven.snippets.SnippetRegistry;
import org.eclipse.lemminx.services.extensions.CompletionParticipantAdapter;
import org.eclipse.lemminx.services.extensions.ICompletionRequest;
import org.eclipse.lemminx.services.extensions.ICompletionResponse;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class MavenCompletionParticipant extends CompletionParticipantAdapter {

	static interface GAVInsertionStrategy {
		/**
		 * set current element value and add siblings as addition textEdits
		 */
		public static final GAVInsertionStrategy ELEMENT_VALUE_AND_SIBLING = new GAVInsertionStrategy() {};

		/**
		 * insert elements as children of the parent element
		 */
		public static final GAVInsertionStrategy CHILDREN_ELEMENTS = new GAVInsertionStrategy() {};
		
		public static final class NodeWithChildrenInsertionStrategy implements GAVInsertionStrategy {
			public final String elementName;
			
			public NodeWithChildrenInsertionStrategy(String elementName) {
				this.elementName = elementName;
			}
		}
	}

	private boolean snippetsLoaded;
	private final LocalRepositorySearcher localRepositorySearcher;
	private final MavenProjectCache cache;
	private final RemoteRepositoryIndexSearcher indexSearcher;
	private final MavenPluginManager pluginManager;
	private final RepositorySystemSession repoSession;
	private final MavenSession mavenSession;
	private final BuildPluginManager buildPluginManager;

	public MavenCompletionParticipant(MavenProjectCache cache, LocalRepositorySearcher localRepositorySearcher, RemoteRepositoryIndexSearcher indexSearcher, MavenSession mavenSession, MavenPluginManager pluginManager, BuildPluginManager buildPluginManager) {
		this.cache = cache;
		this.mavenSession = mavenSession;
		this.localRepositorySearcher = localRepositorySearcher;
		this.indexSearcher = indexSearcher;
		this.repoSession = mavenSession.getRepositorySession();
		this.pluginManager = pluginManager;
		this.buildPluginManager = buildPluginManager;
	}
	
	@Override
	public void onTagOpen(ICompletionRequest request, ICompletionResponse response)
			throws Exception {
		if (!MavenPlugin.match(request.getXMLDocument())) {
			  return;
		}
		
		if ("configuration".equals(request.getParentElement().getLocalName())) {
			MavenPluginUtils.collectPluginConfigurationParameters(request, cache, repoSession, pluginManager, buildPluginManager, mavenSession).stream()
					.map(parameter -> toTag(parameter.getName(), MavenPluginUtils.getMarkupDescription(parameter), request))
					.forEach(response::addCompletionItem);
		}
	}

	@Override
	public void onXMLContent(ICompletionRequest request, ICompletionResponse response) throws Exception {
		if (!MavenPlugin.match(request.getXMLDocument())) {
			  return;
		}
		
		if (request.getXMLDocument().getText().length() < 2) {
			response.addCompletionItem(createMinimalPOMCompletionSnippet(request));
		}
		DOMElement parent = request.getParentElement();
		if (parent == null || parent.getLocalName() == null) {
			return;
		}
		DOMElement grandParent = parent.getParentElement();
		boolean isPlugin = "plugin".equals(parent.getLocalName()) || (grandParent != null && "plugin".equals(grandParent.getLocalName()));
		boolean isParentDeclaration = "parent".equals(parent.getLocalName()) || (grandParent != null && "parent".equals(grandParent.getLocalName()));
		Optional<String> groupId = grandParent == null ? Optional.empty() : grandParent.getChildren().stream()
				.filter(DOMNode::isElement)
				.filter(node -> "groupId".equals(node.getLocalName()))
				.flatMap(node -> node.getChildren().stream())
				.map(DOMNode::getTextContent)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.findFirst();
		Optional<String> artifactId = grandParent == null ? Optional.empty() : grandParent.getChildren().stream()
				.filter(DOMNode::isElement)
				.filter(node -> "artifactId".equals(node.getLocalName()))
				.flatMap(node -> node.getChildren().stream())
				.map(DOMNode::getTextContent)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.findFirst();
		GAVInsertionStrategy gavInsertionStrategy = computeGAVInsertionStrategy(request);
		List<ArtifactInfo> allArtifactInfos = Collections.synchronizedList(new ArrayList<>());
		switch (parent.getLocalName()) {
		case "scope":
			collectSimpleCompletionItems(Arrays.asList(DependencyScope.values()), DependencyScope::getName,
					DependencyScope::getDescription, request).forEach(response::addCompletionAttribute);
			break;
		case "phase":
			collectSimpleCompletionItems(Arrays.asList(Phase.ALL_STANDARD_PHASES), phase -> phase.id,
					phase -> phase.description, request).forEach(response::addCompletionAttribute);
			break;
		case "groupId":
			if (isParentDeclaration) {
				Optional<MavenProject> filesystem = computeFilesystemParent(request);
				if (filesystem.isPresent()) {
					filesystem.map(this::toArtifactInfo).ifPresent(allArtifactInfos::add);
				}
				// TODO localRepo
				// TODO remoteRepos
			} else {
				// TODO if artifactId is set and match existing content, suggest only matching groupId
				collectSimpleCompletionItems(isPlugin ? localRepositorySearcher.searchPluginGroupIds() : localRepositorySearcher.searchGroupIds(),
						Function.identity(), Function.identity(), request).forEach(response::addCompletionAttribute);
				internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, response);
			}
			break;
		case "artifactId":
			if (isParentDeclaration) {
				Optional<MavenProject> filesystem = computeFilesystemParent(request);
				if (filesystem.isPresent()) {
					filesystem.map(this::toArtifactInfo).ifPresent(allArtifactInfos::add);
				}
				// TODO localRepo
				// TODO remoteRepos
			} else {
				allArtifactInfos.addAll((isPlugin ? localRepositorySearcher.getLocalPluginArtifacts() : localRepositorySearcher.getLocalArtifactsLastVersion()).stream()
					.filter(gav -> !groupId.isPresent() || gav.getGroupId().equals(groupId.get()))
					// TODO pass description as documentation
					.map(this::toArtifactInfo)
					.collect(Collectors.toList()));
				internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, response);
			}
			break;
		case "version":
			if (!isParentDeclaration) {
				if (artifactId.isPresent()) {
					localRepositorySearcher.getLocalArtifactsLastVersion().stream()
						.filter(gav -> gav.getGroupId().equals(artifactId.get()))
						.filter(gav -> !groupId.isPresent() || gav.getGroupId().equals(groupId.get()))
						.findAny()
						.map(Gav::getVersion)
						.map(DefaultArtifactVersion::new)
						.map(version -> toCompletionItem(version.toString(), null, request.getReplaceRange()))
						.ifPresent(response::addCompletionItem);
					internalCollectRemoteGAVCompletion(request, isPlugin, allArtifactInfos, response);
				}
			} else {
				Optional<MavenProject> filesystem = computeFilesystemParent(request);
				if (filesystem.isPresent()) {
					filesystem.map(this::toArtifactInfo).ifPresent(allArtifactInfos::add);
				}
				// TODO localRepo
				// TODO remoteRepos
			}
			if (allArtifactInfos.isEmpty()) {
				response.addCompletionItem(toTextCompletionItem(request, "-SNAPSHOT"));
			}
			break;
		case "module":
			collectSubModuleCompletion(request).forEach(response::addCompletionItem);
			break;
		case "relativePath":
			collectRelativePathCompletion(request).forEach(response::addCompletionItem);
			break;
		case "dependencies":
		case "dependency":
			// TODO completion/resolve to get description for local artifacts
			allArtifactInfos.addAll(localRepositorySearcher.getLocalArtifactsLastVersion().stream()
				.map(this::toArtifactInfo)
				.collect(Collectors.toList()));
			internalCollectRemoteGAVCompletion(request, false, allArtifactInfos, response);
			break;
		case "plugins":
		case "plugin":
			// TODO completion/resolve to get description for local artifacts
			allArtifactInfos.addAll(localRepositorySearcher.getLocalPluginArtifacts().stream()
				.map(this::toArtifactInfo)
				.collect(Collectors.toList()));
			internalCollectRemoteGAVCompletion(request, true, allArtifactInfos, response);
			break;
		case "parent":
			Optional<MavenProject> filesystem = computeFilesystemParent(request);
			if (filesystem.isPresent()) {
				filesystem.map(this::toArtifactInfo).ifPresent(allArtifactInfos::add);
			} else {
				// TODO localRepo
				// TODO remoteRepos
			}
			break;
		case "goal":
			collectGoals(request).forEach(response::addCompletionItem);
			break;
		case "configuration":
			MavenPluginUtils.collectPluginConfigurationParameters(request, cache, repoSession, pluginManager, buildPluginManager, mavenSession).stream()
					.map(parameter -> toTag(parameter.getName(), MavenPluginUtils.getMarkupDescription(parameter), request))
					.filter(distinctByKey(
							completionItem -> ((CompletionItem) completionItem).getDocumentation().getLeft()))
					.forEach(response::addCompletionItem);
			break;
		default:
			initSnippets();
			TextDocument document = parent.getOwnerDocument().getTextDocument();
			int completionOffset = request.getOffset();
			boolean canSupportMarkdown = true; // request.canSupportMarkupKind(MarkupKind.MARKDOWN);
			SnippetRegistry.getInstance()
					.getCompletionItems(document, completionOffset, canSupportMarkdown, context -> {
						if (!Maven.POMv4.equals(context.getType())) {
							return false;
						}
						return parent.getLocalName().equals(context.getValue());
					}).forEach(response::addCompletionItem);
		}
		if (!allArtifactInfos.isEmpty()) {
			Comparator<ArtifactInfo> artifactInfoComparator = Comparator.comparing(artifact -> new DefaultArtifactVersion(artifact.getVersion()))/*.thenComparing(ArtifactInfo::getDescription)*/;
			final Comparator<ArtifactInfo> highestVersionWithDescriptionComparator = artifactInfoComparator.thenComparing(artifactInfo -> artifactInfo.getDescription() != null ? artifactInfo.getDescription() : "");
			allArtifactInfos.stream()
				.collect(Collectors.groupingBy(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId()))
				.values()
				.stream()
				.map(artifacts -> Collections.max(artifacts, highestVersionWithDescriptionComparator))
				.map(artifactInfo -> toGAVCompletionItem(artifactInfo, request, gavInsertionStrategy))
				.forEach(response::addCompletionItem);
		}
		if (request.getNode().isText() && (allArtifactInfos.isEmpty() || request.getNode().getTextContent().contains("$"))) {
			completeProperties(request).forEach(response::addCompletionAttribute);
		}
	}

	private CompletionItem toTextCompletionItem(ICompletionRequest request, String text) throws BadLocationException {
		CompletionItem res = new CompletionItem(text);
		res.setFilterText(text);
		TextEdit edit = new TextEdit();
		edit.setNewText(text);
		res.setTextEdit(edit);
		Position endOffset = request.getXMLDocument().positionAt(request.getOffset());
		for (int startOffset = Math.max(0, request.getOffset() - text.length()); startOffset <= request.getOffset(); startOffset++) {
			String prefix = request.getXMLDocument().getText().substring(startOffset, request.getOffset());
			if (text.startsWith(prefix)) {
				edit.setRange(new Range(request.getXMLDocument().positionAt(startOffset), endOffset));
				return res;
			}
		}
		edit.setRange(new Range(endOffset, endOffset));
		return res;
	}

	private ArtifactInfo toArtifactInfo(Gav gav) {
		return new ArtifactInfo(null, gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), null, null);
	}
	
	private ArtifactInfo toArtifactInfo(MavenProject project) {
		ArtifactInfo artifactInfo = new ArtifactInfo(null, project.getGroupId(), project.getArtifactId(), project.getVersion(), null, null);
		artifactInfo.setDescription(project.getDescription());
		return artifactInfo;
	}

	private GAVInsertionStrategy computeGAVInsertionStrategy(ICompletionRequest request) {
		if (request.getParentElement() == null) {
			return null;
		}
		switch (request.getParentElement().getLocalName()) {
		case "dependencies": return new GAVInsertionStrategy.NodeWithChildrenInsertionStrategy("dependency");
		case "dependency": return GAVInsertionStrategy.CHILDREN_ELEMENTS;
		case "plugins": return new GAVInsertionStrategy.NodeWithChildrenInsertionStrategy("plugin");
		case "plugin": return GAVInsertionStrategy.CHILDREN_ELEMENTS;
		case "artifactId": return GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING;
		case "parent": return GAVInsertionStrategy.CHILDREN_ELEMENTS;
		}
		return GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING;
	}

	private Optional<MavenProject> computeFilesystemParent(ICompletionRequest request) {
		Optional<String> relativePath = null;
		if (request.getParentElement().getLocalName().equals("parent")) {
			relativePath = DOMUtils.findChildElementText(request.getNode(), "relativePath");
		} else {
			relativePath = DOMUtils.findChildElementText(request.getParentElement().getParentElement(), "relativePath");
		}
		if (!relativePath.isPresent()) {
			relativePath = Optional.of("..");
		}
		File referencedTargetPomFile = new File(new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile(), relativePath.orElse(""));
		if (referencedTargetPomFile.isDirectory()) {
			referencedTargetPomFile = new File(referencedTargetPomFile, Maven.POMv4);
		}
		if (referencedTargetPomFile.isFile()) {
			return cache.getSnapshotProject(referencedTargetPomFile);
		}
		return Optional.empty();
	}

	private CompletionItem createMinimalPOMCompletionSnippet(ICompletionRequest request) throws IOException, BadLocationException {
		CompletionItem item = new CompletionItem("minimal pom content");
		item.setKind(CompletionItemKind.Snippet);
		item.setInsertTextFormat(InsertTextFormat.Snippet);
		Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setArtifactId(new File(URI.create(request.getXMLDocument().getTextDocument().getUri())).getParentFile().getName());
		MavenXpp3Writer writer = new MavenXpp3Writer();
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			writer.write(stream, model);
			TextEdit textEdit = new TextEdit(new Range(new Position(0, 0), request.getXMLDocument().positionAt(request.getXMLDocument().getText().length())), new String(stream.toByteArray()));
			item.setTextEdit(textEdit);
		}
		return item;
	}

	private Collection<CompletionItem> collectGoals(ICompletionRequest request) {
		PluginDescriptor pluginDescriptor;
		try {
			pluginDescriptor = MavenPluginUtils.getContainingPluginDescriptor(request, cache, repoSession, pluginManager);
			return collectSimpleCompletionItems(pluginDescriptor.getMojos(), MojoDescriptor::getGoal, MojoDescriptor::getDescription, request);
		} catch (PluginResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException e) {
			e.printStackTrace();
		}
		return Collections.emptySet();
	}

	private CompletionItem toGAVCompletionItem(ArtifactInfo artifactInfo, ICompletionRequest request, GAVInsertionStrategy strategy) {
		boolean insertGroupId = strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy || !DOMUtils.findChildElementText(request.getParentElement().getParentElement(), "groupId").isPresent();
		boolean insertVersion = strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy || !DOMUtils.findChildElementText(request.getParentElement().getParentElement(), "version").isPresent();
		CompletionItem item = new CompletionItem();
		if (artifactInfo.getDescription() != null) {
			item.setDocumentation(artifactInfo.getDescription());
		}
		TextEdit textEdit = new TextEdit();
		item.setTextEdit(textEdit);
		textEdit.setRange(request.getReplaceRange());
		if (strategy == GAVInsertionStrategy.ELEMENT_VALUE_AND_SIBLING) {
			item.setKind(CompletionItemKind.Value);
			textEdit.setRange(request.getReplaceRange());
			switch (request.getParentElement().getLocalName()) {
			case "artifactId":
				item.setLabel(artifactInfo.getArtifactId() + (insertGroupId || insertVersion ? " - " + artifactInfo.getGroupId() + ":" + artifactInfo.getArtifactId() + ":" + artifactInfo.getVersion() : ""));
				textEdit.setNewText(artifactInfo.getArtifactId());
				List<TextEdit> additionalEdits = new ArrayList<>(2);
				if (insertGroupId) {
					Position insertionPosition;
					try {
						insertionPosition = request.getXMLDocument().positionAt(request.getParentElement().getParentElement().getStartTagCloseOffset() + 1);
						additionalEdits.add(new TextEdit(new Range(insertionPosition, insertionPosition), request.getLineIndentInfo().getLineDelimiter() + request.getLineIndentInfo().getWhitespacesIndent() + "<groupId>" + artifactInfo.getGroupId() + "</groupId>"));
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				if (insertVersion) {
					Position insertionPosition;
					try {
						insertionPosition = request.getXMLDocument().positionAt(request.getParentElement().getEndTagCloseOffset() + 1);
						additionalEdits.add(new TextEdit(new Range(insertionPosition, insertionPosition), request.getLineIndentInfo().getLineDelimiter() + request.getLineIndentInfo().getWhitespacesIndent() + DOMUtils.getOneLevelIndent(request) + "<version>" + artifactInfo.getVersion() + "</version>"));
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				if (!additionalEdits.isEmpty()) {
					item.setAdditionalTextEdits(additionalEdits);
				}
				return item;
			case "groupId":
				item.setLabel(artifactInfo.getGroupId());
				textEdit.setNewText(artifactInfo.getGroupId());
				return item;
			case "version":
				item.setLabel(artifactInfo.getVersion());
				textEdit.setNewText(artifactInfo.getVersion());
				return item;
			}
		} else {
			item.setLabel(artifactInfo.getArtifactId() + " - " + artifactInfo.getGroupId() + ":" + artifactInfo.getArtifactId() + ":" + artifactInfo.getVersion());
			item.setKind(CompletionItemKind.Struct);
			try {
				textEdit.setRange(request.getReplaceRange());
				String newText = "";
				String suffix = ""; 
				String gavElementsIndent = request.getLineIndentInfo().getWhitespacesIndent(); 
				if (strategy instanceof GAVInsertionStrategy.NodeWithChildrenInsertionStrategy) {
					String elementName = ((GAVInsertionStrategy.NodeWithChildrenInsertionStrategy)strategy).elementName;
					gavElementsIndent += DOMUtils.getOneLevelIndent(request);
					newText += "<" + elementName + ">" + request.getLineIndentInfo().getLineDelimiter() + gavElementsIndent;
					suffix = request.getLineIndentInfo().getLineDelimiter() + request.getLineIndentInfo().getWhitespacesIndent() + "</" + elementName + ">";
				}
				if (insertGroupId) {
					newText += "<groupId>" + artifactInfo.getGroupId() + "</groupId>" + request.getLineIndentInfo().getLineDelimiter() + gavElementsIndent;
				}
				newText += "<artifactId>" + artifactInfo.getArtifactId() + "</artifactId>";
				if (insertVersion) {
					newText += request.getLineIndentInfo().getLineDelimiter() + gavElementsIndent;
					newText += "<version>" + artifactInfo.getVersion() + "</version>";
				}
				newText += suffix;
				textEdit.setNewText(newText);
			} catch (BadLocationException ex) {
				ex.printStackTrace();
				return null;
			}
		}
		return item;
	}

	private CompletionItem toTag(String name, MarkupContent description, ICompletionRequest request) {
		CompletionItem res = new CompletionItem(name);
		res.setDocumentation(Either.forRight(description));
		res.setInsertTextFormat(InsertTextFormat.Snippet);
		TextEdit edit = new TextEdit();
		edit.setNewText('<' + name + ">$0</" + name + '>');
		edit.setRange(request.getReplaceRange());
		res.setTextEdit(edit);
		res.setKind(CompletionItemKind.Field);
		res.setFilterText(edit.getNewText());
		return res;
	}

	private Collection<CompletionItem> completeProperties(ICompletionRequest request) {
		DOMDocument xmlDocument = request.getXMLDocument();
		String documentText = xmlDocument.getText();
		int initialPropertyOffset = request.getOffset();
		for (int i = request.getOffset() - 1; i >= request.getNode().getStart(); i--) {
			char currentChar = documentText.charAt(i);
			if (currentChar == '}') {
				// properties area ended, return all properties
				break;
			} else if (currentChar == '$') {
				initialPropertyOffset = i;
				break;
			}
		}

		MavenProject project = cache.getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project == null) {
			return Collections.emptySet();
		}
		Map<String, String> allProps = MavenHoverParticipant.getMavenProjectProperties(project);

		return allProps.entrySet().stream().map(property -> {
			try {
				CompletionItem item = toTextCompletionItem(request, "${" + property.getKey() + '}');
				item.setDocumentation("Default Value: " + (property.getValue() != null ? property.getValue() : "unknown"));
				item.setKind(CompletionItemKind.Property);
				return item;
			} catch (BadLocationException e) {
				e.printStackTrace();
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

	private void internalCollectRemoteGAVCompletion(ICompletionRequest request, boolean onlyPlugins, Collection<ArtifactInfo> artifactInfosCollector, ICompletionResponse nonArtifactCollector) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();

		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1, node.getEndTagOpenOffset(),
				doc);
		List<String> remoteArtifactRepositories = Collections.singletonList(RemoteRepositoryIndexSearcher.CENTRAL_REPO.getUrl());
		Dependency artifactToSearch = MavenParseUtils.parseArtifact(node);
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		if (project != null) {
			remoteArtifactRepositories = project.getRemoteArtifactRepositories().stream().map(ArtifactRepository::getUrl).collect(Collectors.toList());
		}
		Set<CompletionItem> updateItems = Collections.synchronizedSet(new HashSet<>(remoteArtifactRepositories.size()));
		try {
			CompletableFuture.allOf(remoteArtifactRepositories.stream().map(repository -> {
				final CompletionItem updatingItem = new CompletionItem("Updating index for " + repository);
				updatingItem.setPreselect(true);
				updatingItem.setInsertText("");
				updatingItem.setKind(CompletionItemKind.Event);
				updateItems.add(updatingItem);
				return indexSearcher.getIndexingContext(URI.create(repository)).thenApplyAsync(index -> {
					switch (node.getLocalName()) {
					case "groupId":
						// TODO: just pass only plugins boolean, and make getGroupId's accept a boolean parameter
						if (onlyPlugins) {
							indexSearcher.getPluginGroupIds(artifactToSearch, index).stream()
									.map(groupId -> toCompletionItem(groupId, null, range)).forEach(nonArtifactCollector::addCompletionItem);
						} else {
							indexSearcher.getGroupIds(artifactToSearch, index).stream()
									.map(groupId -> toCompletionItem(groupId, null, range)).forEach(nonArtifactCollector::addCompletionItem);
						}
						return updatingItem;
					case "artifactId":
						if (onlyPlugins) {
							artifactInfosCollector.addAll(indexSearcher.getPluginArtifacts(artifactToSearch, index));
						} else {
							artifactInfosCollector.addAll(indexSearcher.getArtifacts(artifactToSearch, index));
						}
						return updatingItem;
					case "version":
						if (onlyPlugins) {
							indexSearcher.getPluginArtifactVersions(artifactToSearch, index).stream()
									.map(version -> toCompletionItem(version.toString(), null, range))
									.forEach(nonArtifactCollector::addCompletionItem);
						} else {
							indexSearcher.getArtifactVersions(artifactToSearch, index).stream()
									.map(version -> toCompletionItem(version.toString(), null, range))
									.forEach(nonArtifactCollector::addCompletionItem);
						}
						return updatingItem;
					case "dependencies":
					case "dependency":
						artifactInfosCollector.addAll(indexSearcher.getArtifacts(artifactToSearch, index));
						return updatingItem;
					case "plugins":
					case "plugin":
						artifactInfosCollector.addAll(indexSearcher.getPluginArtifacts(artifactToSearch, index));
						return updatingItem;
					}
					return (CompletionItem)null;
				}).whenComplete((ok, error) -> updateItems.remove(ok));
			}).toArray(CompletableFuture<?>[]::new)).get(2, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException exception) {
			exception.printStackTrace();
		} catch (TimeoutException e) {
			// nothing to log, some work still pending
			updateItems.forEach(nonArtifactCollector::addCompletionItem);
		}
	}

	private Collection<CompletionItem> collectSubModuleCompletion(ICompletionRequest request) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		File docFolder = new File(URI.create(doc.getTextDocument().getUri())).getParentFile();
		String prefix = doc.getText().substring(node.getStartTagCloseOffset() + 1, request.getOffset());
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (!prefix.isEmpty() && !prefix.endsWith("/")) {
			files.addAll(Arrays.asList(prefixFile.getParentFile().listFiles((dir, name) -> name.startsWith(prefixFile.getName()))));
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles(File::isDirectory)));
		}
		// make folder that have a pom show higher
		files.sort(Comparator.comparing((File file) -> new File(file, Maven.POMv4).exists()).reversed().thenComparing(Function.identity()));
		if (prefix.isEmpty()) {
			files.add(docFolder.getParentFile());
		}
		return files.stream()
			.map(file -> toFileCompletionItem(file, docFolder, request))
			.collect(Collectors.toList());
	}

	private Collection<CompletionItem> collectRelativePathCompletion(ICompletionRequest request) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		File docFile = new File(URI.create(doc.getTextDocument().getUri()));
		File docFolder = docFile.getParentFile();
		String prefix = doc.getText().substring(node.getStartTagCloseOffset() + 1, request.getOffset());
		File prefixFile = new File(docFolder, prefix);
		List<File> files = new ArrayList<>();
		if (prefix.isEmpty()) {
			Arrays.stream(docFolder.getParentFile().listFiles())
				.filter(file -> file.getName().contains("parent"))
				.map(file -> new File(file, Maven.POMv4))
				.filter(File::isFile)
				.forEach(files::add);
			files.add(docFolder.getParentFile());
		} else {
			try {
				prefixFile = prefixFile.getCanonicalFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (!prefix.endsWith("/")) {
				final File thePrefixFile = prefixFile;
				files.addAll(Arrays.asList(prefixFile.getParentFile().listFiles(file -> file.getName().startsWith(thePrefixFile.getName()))));
			}
		}
		if (prefixFile.isDirectory()) {
			files.addAll(Arrays.asList(prefixFile.listFiles()));
		}
		return files.stream()
			.filter(file -> file.getName().equals(Maven.POMv4) || file.isDirectory())
			.filter(file -> !(file.equals(docFolder) || file.equals(docFile)))
			.flatMap(file -> {
				if (docFile.toPath().startsWith(file.toPath()) || file.getName().contains("parent")) {
					File pomFile = new File(file, Maven.POMv4);
					if (pomFile.exists()) {
						return Stream.of(pomFile, file);
					}
				}
				return Stream.of(file);
			}).sorted(Comparator
				.comparing(File::isFile) // pom files before folders
				.thenComparing(file -> (file.isFile() && docFile.toPath().startsWith(file.getParentFile().toPath())) || (file.isDirectory() && file.equals(docFolder.getParentFile()))) // `../pom.xml` before ...
				.thenComparing(file -> file.getParentFile().getName().contains("parent")) // folders containing "parent" before...
				.thenComparing(file -> file.getParentFile().getParentFile().equals(docFolder.getParentFile())) // siblings before...
				.reversed().thenComparing(Function.identity())// other folders and files
			).map(file -> toFileCompletionItem(file, docFolder, request))
			.collect(Collectors.toList());
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
		String pathString = builder.toString();
		res.setLabel(pathString);
		res.setFilterText(pathString);
		res.setKind(file.isDirectory() ? CompletionItemKind.Folder : CompletionItemKind.File);
		res.setTextEdit(new TextEdit(request.getReplaceRange(), pathString));
		return res;
	}

	private void initSnippets() {
		if (snippetsLoaded) {
			return;
		}
		try {
			try {
				SnippetRegistry.getInstance()
						.load(MavenCompletionParticipant.class.getResourceAsStream("pom-snippets.json"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			snippetsLoaded = true;
		}

	}

	private <T> Collection<CompletionItem> collectSimpleCompletionItems(Collection<T> items, Function<T, String> insertionTextExtractor,
			Function<T, String> documentationExtractor, ICompletionRequest request) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		boolean needClosingTag = node.getEndTagOpenOffset() == DOMNode.NULL_VALUE;
		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1,
				needClosingTag ? node.getStartTagOpenOffset() + 1 : node.getEndTagOpenOffset(), doc);

		return items.stream().map(o -> {
			String label = insertionTextExtractor.apply(o);
			CompletionItem item = new CompletionItem();
			item.setLabel(label);
			String insertText = label + (needClosingTag ? "</" + node.getTagName() + ">" : "");
			item.setKind(CompletionItemKind.Property);
			item.setDocumentation(Either.forLeft(documentationExtractor.apply(o)));
			item.setFilterText(insertText);
			item.setTextEdit(new TextEdit(range, insertText));
			item.setInsertTextFormat(InsertTextFormat.PlainText);
			return item;
		}).collect(Collectors.toList());
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
		item.setTextEdit(new TextEdit(range, insertText));
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
}
