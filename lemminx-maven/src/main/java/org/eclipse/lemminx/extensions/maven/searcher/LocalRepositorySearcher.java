/*******************************************************************************
 * Copyright (c) 2019, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher;

import static org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils.isWellDefinedDependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class LocalRepositorySearcher {

	private static final Logger LOGGER = Logger.getLogger(LocalRepositorySearcher.class.getName());

	private final File localRepository;

	public LocalRepositorySearcher(File localRepository) {
		this.localRepository = localRepository;
	}

	private Map<File, CompletableFuture<Collection<Artifact>>> cache = new HashMap<>();
	private WatchKey watchKey;
	private WatchService watchService;

	public Set<String> searchGroupIds() throws IOException {
		return getLocalArtifactsLastVersion().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Set<String> searchPluginGroupIds() throws IOException {
		return getLocalPluginArtifacts().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Collection<Artifact> getLocalPluginArtifacts()  {
		return getLocalArtifactsLastVersion().stream().filter(gav -> gav.getArtifactId().contains("-plugin")).collect(Collectors.toSet());
	}

	public Collection<Artifact> getLocalArtifactsLastVersion() {
		CompletableFuture<Collection<Artifact>> loadLocalArtifacts = cache.get(localRepository);
		if (loadLocalArtifacts == null || loadLocalArtifacts.isCompletedExceptionally()) {
			loadLocalArtifacts = getOrCreateLocalArtifactsLastVersion();
		}
		if (loadLocalArtifacts.isDone()) {
			return loadLocalArtifacts.getNow(Collections.emptyList());
		}
		// The local artifacts search is not finished, returns an empty list
		return Collections.emptyList();
	}

	private synchronized CompletableFuture<Collection<Artifact>> getOrCreateLocalArtifactsLastVersion() {
		CompletableFuture<Collection<Artifact>> loadLocalArtifacts = cache.get(localRepository);
		if (loadLocalArtifacts == null || loadLocalArtifacts.isCompletedExceptionally()) {
			if (MavenLemminxExtension.isUnitTestMode()) {
				try {
					loadLocalArtifacts = CompletableFuture.completedFuture(computeLocalArtifacts(() -> {}));
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, "Local repo loading error", e);
				}
			} else {
				// Load local artifacts on background
				loadLocalArtifacts = CompletableFutures.computeAsync(cancelChecker -> {
					try {
						return computeLocalArtifacts(cancelChecker);
					} catch (IOException e) {
						throw new CompletionException(e);
					}
				});
			}

			// Update the cache
			cache.put(localRepository, loadLocalArtifacts);

			// Once the local artifacts are loaded, we track the local repository to update
			// the cache.
			loadLocalArtifacts.thenAcceptAsync(artifacts -> {
				try {
					Path localRepoPath = localRepository.toPath();
					watchService = localRepoPath.getFileSystem().newWatchService();
					watchKey = localRepoPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE);

					WatchKey key;

					while ((key = (watchService != null ? watchService.take() : null)) != null) {
						if (watchKey.equals(key)) {
							cache.remove(localRepository);
							key.reset();
						}
					}
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, "Local repo thread watcher error", e);
				} catch (ClosedWatchServiceException e) {
					LOGGER.log(Level.WARNING, "Local repo thread watcher is closed");
				} catch (InterruptedException e) {
					LOGGER.log(Level.SEVERE, "Local repo thread watcher interrupted", e);
				}
			});
		}
		return loadLocalArtifacts;
	}

	private Collection<Artifact> computeLocalArtifacts(CancelChecker cancelChecker) throws IOException {
		final Path repoPath = localRepository.toPath();
		Map<String, Artifact> groupIdArtifactIdToVersion = new HashMap<>();
		Files.walkFileTree(repoPath, Collections.emptySet(), 10, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().charAt(0) == '.') {
					cancelChecker.checkCanceled();
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (!Character.isDigit(file.getFileName().toString().charAt(0))) {
					cancelChecker.checkCanceled();
					return FileVisitResult.CONTINUE;
				}
				Path artifactFolderPath = repoPath.relativize(file);
				if (artifactFolderPath.getNameCount() < 3) {
					cancelChecker.checkCanceled();
					// eg "maven-dependency-plugin/3.1.2"
					return FileVisitResult.SKIP_SUBTREE;
				}
				ArtifactVersion version = new DefaultArtifactVersion(artifactFolderPath.getFileName().toString());
				String artifactId = artifactFolderPath.getParent().getFileName().toString();
				String groupId = artifactFolderPath.getParent().getParent().toString().replace(artifactFolderPath.getFileSystem().getSeparator(), ".");
				if (!new File(file.toFile(), artifactId + '-' + version.toString() + ".pom").isFile()) {
					cancelChecker.checkCanceled();
					return FileVisitResult.SKIP_SUBTREE;
				}
				String groupIdArtifactId = groupId + ':' + artifactId;
				Artifact existingGav = groupIdArtifactIdToVersion.get(groupIdArtifactId);
				boolean replace = existingGav == null;
				if (existingGav != null) {
					ArtifactVersion existingVersion = new DefaultArtifactVersion(existingGav.getVersion());
					replace |= existingVersion.compareTo(version) < 0;
					replace |= (existingVersion.toString().endsWith("-SNAPSHOT") && !version.toString().endsWith("-SNAPSHOT"));
				}
				if (replace) {
					groupIdArtifactIdToVersion.put(groupIdArtifactId, new DefaultArtifact(groupId, artifactId, null, version.toString()));
				}
				cancelChecker.checkCanceled();
				return FileVisitResult.SKIP_SUBTREE;
			}
		});
		return groupIdArtifactIdToVersion.values();
	}

	// TODO consider using directly ArtifactRepository for those 2 methods
	public File findLocalFile(Dependency dependency) {
		return isWellDefinedDependency(dependency)
				? new File(localRepository, dependency.getGroupId().replace('.', File.separatorChar)
						+ File.separatorChar + dependency.getArtifactId() + File.separatorChar + dependency.getVersion()
						+ File.separatorChar + dependency.getArtifactId() + '-' + dependency.getVersion() + ".pom")
				: null;
	}
	
	public File findLocalFile(Artifact gav) {
		return new File(localRepository, gav.getGroupId().replace('.', File.separatorChar) + File.separatorChar + gav.getArtifactId() + File.separatorChar + gav.getVersion() + File.separatorChar + gav.getArtifactId() + '-' + gav.getVersion() + ".pom");
	}

	public void stop() {
		// Stop the thread which collects local repository artifacts
		cache
			.values()
			.forEach(f -> f.cancel(true));
		// Close the watch service which tracks the local repository.
		if (watchService != null && watchKey != null) {
			watchKey.cancel();
			try {
				watchService.close();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			watchKey = null;
			watchService = null;
		}
	}

}
