/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.searcher;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.SearchType;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.repository.RemoteRepository;

public class RemoteRepositoryIndexSearcher {
	private static final String PACKAGING_TYPE_JAR = "jar";
	private static final String PACKAGING_TYPE_MAVEN_PLUGIN = "maven-plugin";

	public static final RemoteRepository CENTRAL_REPO = new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();
	private final Set<RemoteRepository> knownRepositories;
	
	private Indexer indexer;

	private IndexUpdater indexUpdater;

	private ResourceFetcher resourceFetcher;

	private List<IndexCreator> indexers = new ArrayList<>();

	private File indexPath;

	private Map<URI, IndexingContext> indexingContexts = new HashMap<>();
	private Map<IndexingContext, CompletableFuture<IndexingContext>> indexDownloadJobs = new HashMap<>();

	public RemoteRepositoryIndexSearcher(PlexusContainer plexusContainer) {
		try {
			indexer = plexusContainer.lookup(Indexer.class);
			indexUpdater = plexusContainer.lookup(IndexUpdater.class);
			resourceFetcher = new WagonHelper.WagonFetcher(plexusContainer.lookup(Wagon.class, "http"), new AbstractTransferListener() {
				@Override
				public void transferStarted(TransferEvent transferEvent) {
					System.out.println("Downloading " + transferEvent.getResource().getName());
				}

				@Override
				public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
				}

				@Override
				public void transferCompleted(TransferEvent transferEvent) {
					System.out.println("Done downloading " + transferEvent.getResource().getName());
				}
			}, null, null);
			indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
			indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
			indexers.add(plexusContainer.lookup(IndexCreator.class, PACKAGING_TYPE_MAVEN_PLUGIN));
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.knownRepositories = new HashSet<>();
		knownRepositories.add(CENTRAL_REPO);
		File localRepository = new File(RepositorySystem.defaultUserLocalRepository.getAbsolutePath());
		this.indexPath = new File(localRepository.getParent(), "_maven_index_");
		indexPath.mkdirs();
		knownRepositories.stream().map(RemoteRepository::getUrl).map(URI::create).forEach(this::getIndexingContext);
		// TODO knownRepositories.addAll(readRepositoriesFromSettings());
	}

	public CompletableFuture<IndexingContext> getIndexingContext(URI repositoryUrl) {
//		if (!repositoryUrl.toString().endsWith("/")) {
//			repositoryUrl = URI.create(repositoryUrl.toString() + "/");
//		}
		synchronized (indexingContexts) {
			IndexingContext res = indexingContexts.get(repositoryUrl);
			if (res != null && indexDownloadJobs.containsKey(res)) {
				final IndexingContext context = res;
				return indexDownloadJobs.get(res).thenApply(theVoid -> context);
			}
			final IndexingContext context = initializeContext(repositoryUrl);
			indexingContexts.put(repositoryUrl, context);
			CompletableFuture<IndexingContext> future = updateIndex(context).thenApply(theVoid -> context);
			indexDownloadJobs.put(context, future);
			return future;
		}
	}
	
	private Set<ArtifactVersion> internalGetArtifactVersions(Dependency artifactToSearch, String packaging, IndexingContext... requestSpecificContexts) {
		if (artifactToSearch.getArtifactId() == null || artifactToSearch.getArtifactId().trim().isEmpty()) {
			return Collections.emptySet();
		}
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		if (artifactToSearch.getGroupId() != null) {
			builder.add(indexer.constructQuery(MAVEN.GROUP_ID, artifactToSearch.getGroupId(), SearchType.EXACT), Occur.MUST);
		}
		builder.add(indexer.constructQuery(MAVEN.ARTIFACT_ID, artifactToSearch.getArtifactId(), SearchType.EXACT), Occur.SHOULD);
		builder.add(indexer.constructQuery(MAVEN.PACKAGING, packaging, SearchType.EXACT), Occur.MUST);
		final BooleanQuery query = builder.build();

		List<IndexingContext> contexts = Collections.unmodifiableList(requestSpecificContexts != null && requestSpecificContexts.length > 0 ?
				Arrays.asList(requestSpecificContexts) :
				new LinkedList<>(indexingContexts.values()));
		final IteratorSearchRequest request = new IteratorSearchRequest(query, contexts, null);

		return createIndexerQuery(artifactToSearch, request).stream()
				.map(ArtifactInfo::getVersion)
				.map(DefaultArtifactVersion::new)
				.sorted()
				.collect(Collectors.toSet());
	}

	public Set<ArtifactVersion> getArtifactVersions(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_JAR, requestSpecificContexts);
	}
	
	public Set<ArtifactVersion> getPluginArtifactVersions(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN, requestSpecificContexts);
	}
	
	private Collection<ArtifactInfo> internalGetArtifactIds(Dependency artifactToSearch, String packaging, IndexingContext... requestSpecificContexts) {
		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
		if (artifactToSearch.getGroupId() != null) {
			queryBuilder.add(indexer.constructQuery(MAVEN.GROUP_ID, artifactToSearch.getGroupId(), SearchType.EXACT), Occur.MUST);
		}
		if (artifactToSearch.getArtifactId() != null) {
			queryBuilder.add(indexer.constructQuery(MAVEN.ARTIFACT_ID, artifactToSearch.getArtifactId(), SearchType.EXACT), Occur.MUST);
		}
		queryBuilder.add(indexer.constructQuery(MAVEN.PACKAGING, packaging, SearchType.EXACT), Occur.MUST);
		final BooleanQuery query = queryBuilder.build();
		List<IndexingContext> contexts = Collections.unmodifiableList(requestSpecificContexts != null && requestSpecificContexts.length > 0 ?
				Arrays.asList(requestSpecificContexts) :
				new LinkedList<>(indexingContexts.values()));
		final IteratorSearchRequest request = new IteratorSearchRequest(query, contexts, null);
		return createIndexerQuery(artifactToSearch, request);
	}

	/**
	 * @param artifactToSearch a CompletableFuture containing a {@code Map<String artifactId, String artifactDescription>} 
	 * @return
	 */
	public Collection<ArtifactInfo> getArtifactIds(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetArtifactIds(artifactToSearch, PACKAGING_TYPE_JAR, requestSpecificContexts);
	}
	
	public Collection<ArtifactInfo> getPluginArtifactIds(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetArtifactIds(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN, requestSpecificContexts);
	}

	private Set<String> internalGetGroupIds(Dependency artifactToSearch, String packaging, IndexingContext... requestSpecificContexts) {
		final Query groupIdQ = indexer.constructQuery(MAVEN.GROUP_ID, artifactToSearch.getGroupId(), SearchType.SCORED);
		final Query jarPackagingQ = indexer.constructQuery(MAVEN.PACKAGING, packaging, SearchType.EXACT);
		final BooleanQuery query = new BooleanQuery.Builder().add(groupIdQ, Occur.MUST).add(jarPackagingQ, Occur.MUST)
				.build();
		List<IndexingContext> contexts = Collections.unmodifiableList(requestSpecificContexts != null && requestSpecificContexts.length > 0 ?
				Arrays.asList(requestSpecificContexts) :
				new LinkedList<>(indexingContexts.values()));
		final IteratorSearchRequest request = new IteratorSearchRequest(query, contexts, null);
		// TODO: Find the Count sweet spot
		request.setCount(7500);
		return createIndexerQuery(artifactToSearch, request).stream().map(ArtifactInfo::getGroupId).collect(Collectors.toSet());		
	}
	// TODO: Get groupid description for completion
	public Set<String> getGroupIds(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_JAR, requestSpecificContexts);
	}
	
	public Set<String> getPluginGroupIds(Dependency artifactToSearch, IndexingContext... requestSpecificContexts) {
		return internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN, requestSpecificContexts);
	}

	private CompletableFuture<Void> updateIndex(IndexingContext context) {
		if (context == null) {
			return CompletableFuture.runAsync(() -> { throw new IllegalArgumentException("context mustn't be null"); });
		}
		System.out.println("Updating Index for " + context.getRepositoryUrl() + "...");
		Date contextCurrentTimestamp = context.getTimestamp();
		IndexUpdateRequest updateRequest = new IndexUpdateRequest(context, resourceFetcher);
		return CompletableFuture.runAsync(() -> {

			try {
				IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
				if (updateResult.isSuccessful()) {
					System.out.println("Update successful for " + context.getRepositoryUrl());
					if (updateResult.isFullUpdate()) {
						System.out.println("Full update happened!");
					} else if (contextCurrentTimestamp.equals(updateResult.getTimestamp())) {
						System.out.println("No update needed, index is up to date!");
					} else {
						System.out.println("Incremental update happened, change covered " + contextCurrentTimestamp
								+ " - " + updateResult.getTimestamp() + " period.");
					}
				} else {
					System.err.println("Index update failed for " + context.getRepositoryUrl());
				}
			} catch (IOException e) {
				// TODO: Fix this - the maven central context gets reported as broken when
				// another context is broken
				indexDownloadJobs.remove(context);
				CompletableFuture.runAsync(() -> {
					e.printStackTrace();
					throw new IllegalArgumentException(
							"Invalid Context: " + context.getRepositoryId() + " @ " + context.getRepositoryUrl());
				});
				
				// TODO: Maybe scan for maven metadata to use as an alternative to retrieve GAV
			}
		});
	}

	private IndexingContext initializeContext(URI repoUrl) {
		String fileSystemFriendlyName = repoUrl.getHost() + repoUrl.hashCode();
		File repoFile = new File(indexPath, fileSystemFriendlyName + "-cache");
		File repoIndex = new File(indexPath, fileSystemFriendlyName + "-index");
		try {
			return indexer.createIndexingContext(repoUrl.toString(), repoUrl.toString(),
					repoFile, repoIndex, repoUrl.toString(), null, true, true, indexers);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void closeContext() {
		for (IndexingContext context : indexingContexts.values()) {
			try {
				indexer.closeIndexingContext(context, false);
				CompletableFuture<?> download = indexDownloadJobs.get(context);
				if (!download.isDone()) {
					download.cancel(true);
				}
			} catch (IOException e) {
				System.out.println("Warning - could not close context: " + context.getId());
				e.printStackTrace();
			}
		}
		indexingContexts.clear();
		indexDownloadJobs.clear();
	}

	public void updateKnownRepositories(MavenProject project) {
		if (project == null || project.getModel() == null) {
			return;

		}
		Model model = project.getModel();

		knownRepositories.addAll(model.getRepositories().stream()
				.map(repo -> new RemoteRepository.Builder(repo.getId(), repo.getLayout(), repo.getUrl()).build())
				.distinct().collect(Collectors.toList()));
		Deque<MavenProject> parentHierarchy = new ArrayDeque<>();
		if (model.getParent() != null && project.getParent() != null) {
			parentHierarchy.add(project.getParent());
			while (!parentHierarchy.isEmpty()) {
				MavenProject currentParentProj = parentHierarchy.pop();
				if (currentParentProj.getParent() != null) {
					Model parentModel = currentParentProj.getParent().getModel();
					knownRepositories.addAll(parentModel.getRepositories().stream()
							.map(repo -> new RemoteRepository.Builder(repo.getId(), repo.getLayout(), repo.getUrl())
									.build())
							.distinct().collect(Collectors.toList()));
					parentHierarchy.add(currentParentProj.getParent());
				}
			}
		}

		// TODO: get repositories from maven user and global settings.xml
	}


	private List<ArtifactInfo> createIndexerQuery(Dependency artifactToSearch, final IteratorSearchRequest request) {
		IteratorSearchResponse response = null;
		try {
			response = indexer.searchIterator(request);
		} catch (IOException e) {
			System.out.println("Index search failed for " + String.join(":", artifactToSearch.getGroupId(),
					artifactToSearch.getArtifactId(), artifactToSearch.getVersion()));
			e.printStackTrace();
		}
		List<ArtifactInfo> artifactInfos = new ArrayList<>();
		if (response != null) {
			response.getResults().forEach(artifactInfos::add);
		}
		return artifactInfos;
	}

}
