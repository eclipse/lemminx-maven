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
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.model.Dependency;

public class LocalRepositorySearcher {
	
	private File localRepository;

	public LocalRepositorySearcher(File localRepository) {
		this.localRepository = localRepository;
	}

	private Map<File, Collection<Gav>> cache = new HashMap<>();
	private WatchKey watchKey;
	private WatchService watchService;

	public Set<String> searchGroupIds() throws IOException {
		return getLocalArtifactsLastVersion().stream().map(Gav::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Set<String> searchPluginGroupIds() throws IOException {
		return getLocalPluginArtifacts().stream().map(Gav::getGroupId).distinct().collect(Collectors.toSet());
	}

	public Collection<Gav> getLocalPluginArtifacts() throws IOException {
		return getLocalArtifactsLastVersion().stream().filter(gav -> gav.getArtifactId().contains("-plugin")).collect(Collectors.toSet());
	}

	public Collection<Gav> getLocalArtifactsLastVersion() throws IOException {
		Collection<Gav> res = cache.get(localRepository);
		if (res == null) {
			res = computeLocalArtifacts();
			Path localRepoPath = localRepository.toPath();
			watchService = localRepoPath.getFileSystem().newWatchService();
			watchKey = localRepoPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
			new Thread(() -> {
				WatchKey key;
				try {
					while ((key = watchService.take()) != null) {
						cache.remove(localRepository);
						key.reset();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}).start();
			cache.put(localRepository, res);
		}
		return res;
	}

	public Collection<Gav> computeLocalArtifacts() throws IOException {
		final Path repoPath = localRepository.toPath();
		Map<String, Gav> groupIdArtifactIdToVersion = new HashMap<>();
		Files.walkFileTree(repoPath, Collections.emptySet(), 10, new SimpleFileVisitor<Path>() { 
			@Override
			public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().charAt(0) == '.') {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (Character.isDigit(file.getFileName().toString().charAt(0))) {
					Path artifactFolderPath = repoPath.relativize(file);
					ArtifactVersion version = new DefaultArtifactVersion(artifactFolderPath.getFileName().toString());
					String artifactId = artifactFolderPath.getParent().getFileName().toString();
					String groupId = artifactFolderPath.getParent().getParent().toString().replace(artifactFolderPath.getFileSystem().getSeparator(), ".");
					String groupIdArtifactId = groupId + ':' + artifactId;
					Gav existingGav = groupIdArtifactIdToVersion.get(groupIdArtifactId);
					boolean replace = existingGav == null;
					if (existingGav != null) {
						ArtifactVersion existingVersion = new DefaultArtifactVersion(existingGav.getVersion());
						replace |= existingVersion.compareTo(version) < 0;
						replace |= (existingVersion.toString().endsWith("-SNAPSHOT") && !version.toString().endsWith("-SNAPSHOT"));
					}
					if (replace) {
						groupIdArtifactIdToVersion.put(groupIdArtifactId, new Gav(groupId, artifactId, version.toString()));
					}
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return groupIdArtifactIdToVersion.values();
	}

	public File findLocalFile(Dependency dependency) {
		return new File(localRepository, dependency.getGroupId().replace('.', File.separatorChar) + File.separatorChar + dependency.getArtifactId() + File.separatorChar + dependency.getVersion() + File.separatorChar + dependency.getArtifactId() + '-' + dependency.getVersion() + ".pom");
	}

	public void stop() {
		if (watchService != null && watchKey != null) {
			watchKey.cancel();
			try {
				watchService.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			watchKey = null;
			watchService = null;
		}
	}

}
