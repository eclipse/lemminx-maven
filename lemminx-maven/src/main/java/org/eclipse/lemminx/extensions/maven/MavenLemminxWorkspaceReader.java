/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * This workspace reader allows to resolve GAV to local workspaceFolders that match
 * instead of usual Maven repos.
 */
public class MavenLemminxWorkspaceReader implements WorkspaceReader {
	private WorkspaceRepository repository;
	private MavenLemminxExtension plugin;
	
	public MavenLemminxWorkspaceReader(MavenLemminxExtension plugin) {
		this.plugin = plugin;
		repository = new WorkspaceRepository("workspace");
	}

	@Override
	public WorkspaceRepository getRepository() {
		return repository;
	}

	@Override
	public File findArtifact(Artifact artifact) {
		String projectKey = ArtifactUtils.key(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
		// Optimize: Instead of storing MavenProjects (which are expensive), we should make the workspace folder store the
		// artifact->File mapping directly.
		Optional<MavenProject> matchingProject = plugin.getProjectCache().getProjects().stream()
				.filter(p -> projectKey.equals(ArtifactUtils.key(p.getGroupId(), p.getArtifactId(), p.getVersion())))
				.findAny();

		if (matchingProject.isPresent()) {
			MavenProject project = matchingProject.get();
			File file = find(project, artifact);
			if (file == null && project != project.getExecutionProject()) {
				file = find(project.getExecutionProject(), artifact);
			}
			return file;
		}
		return null;
	}
 
	@Override
	public List<String> findVersions(Artifact artifact) {
		String key = ArtifactUtils.versionlessKey(artifact.getGroupId(), artifact.getArtifactId());
		List<MavenProject> projects = plugin.getProjectCache().getProjects().stream()
				.filter(p -> key.equals(ArtifactUtils.versionlessKey(p.getGroupId(), p.getArtifactId())))
				.collect(Collectors.toList());

		if (projects == null || projects.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> versions = new ArrayList<>();

		for (MavenProject project : projects) {
			if (find(project, artifact) != null) {
				versions.add(project.getVersion());
			}
		}
		return Collections.unmodifiableList(versions);
	}
	
	private File find(MavenProject project, Artifact artifact) {
		if ("pom".equals(artifact.getExtension())) {
			return project.getFile();
		}

		Artifact projectArtifact = findMatchingArtifact(project, artifact);
		if (hasArtifactFileFromPackagePhase(projectArtifact)) {
			return projectArtifact.getFile();
		}
		return null;
	}
	
	private boolean hasArtifactFileFromPackagePhase(Artifact projectArtifact) {
		return projectArtifact != null && projectArtifact.getFile() != null && projectArtifact.getFile().exists();
	}
	
	private Artifact findMatchingArtifact(MavenProject project, Artifact requestedArtifact) {
		String requestedRepositoryConflictId = ArtifactIdUtils.toVersionlessId(requestedArtifact);
		Artifact mainArtifact = RepositoryUtils.toArtifact(project.getArtifact());
		if (requestedRepositoryConflictId.equals(ArtifactIdUtils.toVersionlessId(mainArtifact))) {
			return mainArtifact;
		}

		for (Artifact attachedArtifact : RepositoryUtils.toArtifacts(project.getAttachedArtifacts())) {
			if (attachedArtifactComparison(requestedArtifact, attachedArtifact)) {
				return attachedArtifact;
			}
		}
		return null;
	}
	
	private boolean attachedArtifactComparison(Artifact requested, Artifact attached) {
		return requested.getArtifactId().equals(attached.getArtifactId())
				&& requested.getGroupId().equals(attached.getGroupId())
				&& requested.getVersion().equals(attached.getVersion())
				&& requested.getExtension().equals(attached.getExtension())
				&& requested.getClassifier().equals(attached.getClassifier());
	}
}
