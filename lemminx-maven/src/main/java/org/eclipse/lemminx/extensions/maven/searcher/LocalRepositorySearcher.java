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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class LocalRepositorySearcher {

	private static final Logger LOGGER = Logger.getLogger(LocalRepositorySearcher.class.getName());

	private final File localRepository;

	public LocalRepositorySearcher(File localRepository) {
		this.localRepository = localRepository;
	}

	private Map<File, Collection<Artifact>> cache = new HashMap<>();
	private WatchKey watchKey;
	private WatchService watchService;

	public Set<String> searchGroupIds() throws IOException {
		return getLocalArtifactsLastVersion().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Set<String> searchPluginGroupIds() throws IOException {
		return getLocalPluginArtifacts().stream().map(Artifact::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Collection<Artifact> getLocalPluginArtifacts() throws IOException {
		return getLocalArtifactsLastVersion().stream().filter(gav -> gav.getArtifactId().contains("-plugin")).collect(Collectors.toSet());
	}

	public Collection<Artifact> getLocalArtifactsLastVersion() throws IOException {
		Collection<Artifact> res = cache.get(localRepository);
		if (res == null) {
			res = computeLocalArtifacts();
			Path localRepoPath = localRepository.toPath();
			watchService = localRepoPath.getFileSystem().newWatchService();
			watchKey = localRepoPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
			new Thread(() -> {
				WatchKey key;
				try {
					while ((key = (watchService != null ? watchService.take() : null)) != null) {
						if (watchKey.equals(key)) {
							cache.remove(localRepository);
							key.reset();
						}
					}
				} catch (ClosedWatchServiceException e) {
					LOGGER.log(Level.WARNING, "Local repo thread watcher is closed");
				} catch (InterruptedException e) {
					LOGGER.log(Level.SEVERE, "Local repo thread watcher interrupted", e);
				}
			}).start();
			cache.put(localRepository, res);
		}
		return res;
	}

	public Collection<Artifact> computeLocalArtifacts() throws IOException {
		final Path repoPath = localRepository.toPath();
		Map<String, Artifact> groupIdArtifactIdToVersion = new HashMap<>();
		Files.walkFileTree(repoPath, Collections.emptySet(), 10, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().charAt(0) == '.') {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (!Character.isDigit(file.getFileName().toString().charAt(0))) {
					return FileVisitResult.CONTINUE;
				}
				Path artifactFolderPath = repoPath.relativize(file);
				if (artifactFolderPath.getNameCount() < 3) {
					// eg "maven-dependency-plugin/3.1.2"
					return FileVisitResult.SKIP_SUBTREE;
				}
				ArtifactVersion version = new DefaultArtifactVersion(artifactFolderPath.getFileName().toString());
				String artifactId = artifactFolderPath.getParent().getFileName().toString();
				String groupId = artifactFolderPath.getParent().getParent().toString().replace(artifactFolderPath.getFileSystem().getSeparator(), ".");
				if (!new File(file.toFile(), artifactId + '-' + version.toString() + ".pom").isFile()) {
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
