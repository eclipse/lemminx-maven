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
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.lemminx.commons.progress.ProgressMonitor;
import org.eclipse.lemminx.commons.progress.ProgressSupport;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

/**
 * Search for collecting artifacts from the local repository /.m2
 *
 */
public class LocalRepositorySearcher {

	private static final Logger LOGGER = Logger.getLogger(LocalRepositorySearcher.class.getName());

	private final ProgressSupport progressSupport;

	private Map<File, CompletableFuture<Collection<Artifact>>> cache = new HashMap<>();

	private CompletableFuture<Collection<Artifact>> loadAllLocalArtifacts;

	public LocalRepositorySearcher(Set<File> localRepositoryDirs, ProgressSupport progressSupport) {
		this.progressSupport = progressSupport;
		// Force the load of the local artifacts done in background
		for (File localRepositoryDir : localRepositoryDirs) {
			createLocalArtifactsFuture(localRepositoryDir);
		}
	}

	public Set<String> searchGroupIds() throws IOException {
		return getLocalArtifactsLastVersion().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Set<String> searchPluginGroupIds() throws IOException {
		return getLocalPluginArtifacts().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Collection<Artifact> getLocalPluginArtifacts() {
		return getLocalArtifactsLastVersion().stream().filter(gav -> gav.getArtifactId().contains("-plugin"))
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the local artifacts (with last version) from the all local
	 * repository.
	 * 
	 * @return the local artifacts (with last version) from the all local
	 *         repository.
	 */
	public Collection<Artifact> getLocalArtifactsLastVersion() {
		if (loadAllLocalArtifacts == null || loadAllLocalArtifacts.isCompletedExceptionally()) {
			loadAllLocalArtifacts = getOrCreateLocalArtifactsLastVersion();
		}
		if (loadAllLocalArtifacts.isDone()) {
			return loadAllLocalArtifacts.getNow(Collections.emptyList());
		}
		// The local artifacts search is not finished, returns an empty list
		return Collections.emptyList();
	}

	private synchronized CompletableFuture<Collection<Artifact>> getOrCreateLocalArtifactsLastVersion() {
		if (!(loadAllLocalArtifacts == null || loadAllLocalArtifacts.isCompletedExceptionally())) {
			return loadAllLocalArtifacts;
		}
		loadAllLocalArtifacts = allOf(cache.values());
		return loadAllLocalArtifacts;
	}

	private static <T> CompletableFuture<Collection<T>> allOf(Collection<CompletableFuture<Collection<T>>> futures) {
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])) //
				.thenApply(__ -> futures.stream() //
						.map(CompletableFuture::join) //
						.flatMap(list -> list.stream()) //
						.toList());
	}

	private void createLocalArtifactsFuture(File localRepository) {
		CompletableFuture<Collection<Artifact>> loadLocalArtifacts = cache.get(localRepository);
		if (loadLocalArtifacts == null || loadLocalArtifacts.isCompletedExceptionally()) {
			if (MavenLemminxExtension.isUnitTestMode()) {
				try {
					loadLocalArtifacts = CompletableFuture
							.completedFuture(computeLocalArtifacts(localRepository, () -> {
							}));
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, "Local repo loading error", e);
				}
			} else {
				// Load local artifacts on background
				loadLocalArtifacts = CompletableFutures.computeAsync(cancelChecker -> {
					try {
						return computeLocalArtifacts(localRepository, cancelChecker);
					} catch (IOException e) {
						throw new CompletionException(e);
					}
				});
			}

			// Update the cache
			cache.put(localRepository, loadLocalArtifacts);
		}
	}

	private Collection<Artifact> computeLocalArtifacts(File localRepository, CancelChecker cancelChecker)
			throws IOException {
		final Path repoPath = localRepository.toPath();
		ProgressMonitor progressMonitor = progressSupport != null ? progressSupport.createProgressMonitor() : null;
		if (progressMonitor != null) {
			progressMonitor.begin("Loading local artifacts from '" + repoPath, null, 100, null);
		}
		Map<String, Artifact> groupIdArtifactIdToVersion = new HashMap<>();
		try {
			List<Path> subPaths = getSubDirectories(repoPath);
			int increment = Math.round(100f / subPaths.size());
			int i = 0;
			for (Path path : subPaths) {
				cancelChecker.checkCanceled();
				if (progressMonitor != null) {
					progressMonitor.report("scanning folder' " + path.getFileName().getName(0) + "' (" + i++ + "/"
							+ subPaths.size() + ")...", increment++, null);
				}
				try {
					collect(path, repoPath, groupIdArtifactIdToVersion, cancelChecker);
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, "Error while scanning local repo folder " + path, e);
				}
			}
		} finally {
			if (progressMonitor != null) {
				progressMonitor.end(null);
			}
		}
		return groupIdArtifactIdToVersion.values();
	}

	private void collect(final Path currentPath, final Path repoPath, Map<String, Artifact> groupIdArtifactIdToVersion,
			CancelChecker cancelChecker) throws IOException {
		Files.walkFileTree(currentPath, Collections.emptySet(), 10, new SimpleFileVisitor<Path>() {
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
				String groupId = artifactFolderPath.getParent().getParent().toString()
						.replace(artifactFolderPath.getFileSystem().getSeparator(), ".");
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
					replace |= (existingVersion.toString().endsWith("-SNAPSHOT")
							&& !version.toString().endsWith("-SNAPSHOT"));
				}
				if (replace) {
					groupIdArtifactIdToVersion.put(groupIdArtifactId,
							new DefaultArtifact(groupId, artifactId, null, version.toString()));
				}
				cancelChecker.checkCanceled();
				return FileVisitResult.SKIP_SUBTREE;
			}
		});
	}

	private static List<Path> getSubDirectories(Path directoryPath) {
		List<Path> subDirectories = new ArrayList<>();
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directoryPath)) {
			for (Path entry : directoryStream) {
				if (Files.isDirectory(entry)) {
					subDirectories.add(entry);
				}
			}
		} catch (IOException e) {
			// Do nothing
		}
		return subDirectories;
	}

	// TODO consider using directly ArtifactRepository for those 2 methods
	public File findLocalFile(Dependency dependency) {
		if (isWellDefinedDependency(dependency)) {
			File artifactFile = null;
			for (File localRepository : cache.keySet()) {
				artifactFile = new File(localRepository, dependency.getGroupId().replace('.', File.separatorChar)
						+ File.separatorChar + dependency.getArtifactId() + File.separatorChar + dependency.getVersion()
						+ File.separatorChar + dependency.getArtifactId() + '-' + dependency.getVersion() + ".pom");
				if (artifactFile.isFile()) {
					return artifactFile;
				}
			}
			return artifactFile;
		}
		return null;
	}

	public File findLocalFile(Artifact gav) {
		File artifactFile = null;
		for (File localRepository : cache.keySet()) {
			artifactFile = new File(localRepository,
					gav.getGroupId().replace('.', File.separatorChar) + File.separatorChar + gav.getArtifactId()
							+ File.separatorChar + gav.getVersion() + File.separatorChar + gav.getArtifactId() + '-'
							+ gav.getVersion() + ".pom");
			if (artifactFile.isFile()) {
				return artifactFile;
			}
		}
		return artifactFile;
	}

	public void stop() {
		// Stop the thread which collects local repository artifacts
		cache.values().forEach(f -> f.cancel(true));
		cache.clear();
		if (loadAllLocalArtifacts != null) {
			loadAllLocalArtifacts.cancel(true);
			loadAllLocalArtifacts = null;
		}
	}

}
