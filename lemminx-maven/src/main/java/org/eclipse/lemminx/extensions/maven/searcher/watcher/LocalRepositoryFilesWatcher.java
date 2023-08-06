/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher.watcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.lemminx.extensions.maven.searcher.LocalRepositorySearcher;

/**
 * This class provides the capability to track the deleted/created folder from
 * the Maven local repository folder to maintain the artifact cache which store
 * the last version of the artifact.
 * 
 * @author Angelo ZERR
 *
 */
public class LocalRepositoryFilesWatcher extends WatchDir {

	private static final Logger LOGGER = Logger.getLogger(LocalRepositoryFilesWatcher.class.getName());

	private final Path localRepoPath;
	private final Collection<Artifact> cacheArtifacts;
	private final Thread thread;

	public LocalRepositoryFilesWatcher(Path localRepoPath, Collection<Artifact> cacheArtifacts) throws IOException {
		super(localRepoPath, true);
		this.localRepoPath = localRepoPath;
		this.cacheArtifacts = cacheArtifacts;
		thread = new Thread(this);
		thread.setName("Watch Maven local repository '" + localRepoPath + "'");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	protected void notifyListeners(WatchEvent<?> event, Path child) {
		try {
			boolean checkExistingPomFile = event.kind() == StandardWatchEventKinds.ENTRY_CREATE;
			DefaultArtifact fileArtifact = LocalRepositorySearcher.createArtifact(child, localRepoPath, null,
					checkExistingPomFile);
			if (fileArtifact != null) {
				// The created / delete folder is an artifact
				DefaultArtifactVersion fileVersion = new DefaultArtifactVersion(fileArtifact.getVersion());
				// Get the artifact and version from the cache (which store the last version of
				// the artifact) for the groupId/artifactId.s
				Optional<Artifact> cacheArtifactResult = cacheArtifacts //
						.stream() //
						.filter(a -> a.getArtifactId().equals(fileArtifact.getArtifactId())
								&& a.getGroupId().equals(fileArtifact.getGroupId())) //
						.findFirst();
				Artifact cacheArtifact = cacheArtifactResult.isPresent() ? cacheArtifactResult.get() : null;
				DefaultArtifactVersion cacheVersion = cacheArtifact != null
						? new DefaultArtifactVersion(cacheArtifact.getVersion())
						: null;
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					// A new artifact is added...
					if (cacheArtifact == null) {
						// The new artifact doesn't exists, add it to the cache
						synchronized (cacheArtifacts) {
							cacheArtifacts.add(fileArtifact);
						}
					} else if (fileVersion.compareTo(cacheVersion) > 0) {
						// The new artifact have a version > of artifact from the cache, update the
						// cache with the new artifact
						synchronized (cacheArtifacts) {
							cacheArtifacts.remove(cacheArtifact);
							cacheArtifacts.add(fileArtifact);
						}
					}
				} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					// An artifact is deleted...
					if (cacheArtifact != null && fileVersion.equals(cacheVersion)) {
						// The last version of artifact is deleted

						// - v1
						// - v2
						// - v3 <-- this artifact is deleted
						// The cache should be replaced with v2

						// Get the v2 artifact
						Artifact newArtifact = null;
						DefaultArtifactVersion newVersion = null;
						try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(child.getParent())) {
							for (Path entry : directoryStream) {
								if (Files.isDirectory(entry)) {
									DefaultArtifactVersion version = new DefaultArtifactVersion(
											entry.getFileName().toString());
									if (newVersion == null || newVersion.compareTo(version) < 0) {
										Artifact currentNewArtifact = LocalRepositorySearcher.createArtifact(entry,
												localRepoPath, null, true);
										if (currentNewArtifact != null) {
											newArtifact = currentNewArtifact;
											newVersion = version;
										}
									}
								}
							}
						} catch (IOException e) {
							// Do nothing
						}
						if (newArtifact != null) {
							synchronized (cacheArtifacts) {
								// The new artifact (v2) replace the deleted artifact (v3)
								cacheArtifacts.add(newArtifact);
								cacheArtifacts.remove(cacheArtifact);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
				LOGGER.log(Level.SEVERE,
						"Error while updating local repo cache with created path '" + child.toString() + "'", e);
			} else {
				LOGGER.log(Level.SEVERE,
						"Error while updating local repo cache with deleted path '" + child.toString() + "'", e);
			}
		}
	}

	@Override
	public void stop() {
		super.stop();
		thread.interrupt();
	}
}