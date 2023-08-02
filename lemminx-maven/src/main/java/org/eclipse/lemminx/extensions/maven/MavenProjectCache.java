/*******************************************************************************
 * Copyright (c) 2020-2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelProblem;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.apache.maven.model.building.ModelProblem.Version;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.DefaultProjectBuilder;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.utils.DOMModelSource;
import org.eclipse.lemminx.services.IXMLDocumentProvider;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenProjectCache {

	private static final Logger LOGGER = Logger.getLogger(MavenProjectCache.class.getName());
	private final Map<String, LoadedMavenProjectProvider> projectCache;
	private final MavenSession mavenSession;
	private final IXMLDocumentProvider documentProvider;
	MavenXpp3Reader mavenReader = new MavenXpp3Reader();
	private ProjectBuilder projectBuilder;

	public MavenProjectCache(MavenSession mavenSession, IXMLDocumentProvider documentProvider) {
		this.mavenSession = mavenSession;
		this.projectCache = new HashMap<>();
		this.documentProvider = documentProvider;
		initializeMavenBuildState();
	}

	/**
	 * Returns the last successfully parsed and cached Maven Project for the given
	 * document
	 * 
	 * @param document A given Document
	 * @return the last MavenDocument that could be build for the more recent
	 *         version of the provided document. If document fails to build a
	 *         MavenProject, a former version will be returned. Can be
	 *         <code>null</code>.
	 */
	public MavenProject getLastSuccessfulMavenProject(DOMDocument document) {
		CompletableFuture<LoadedMavenProject> project = getLoadedMavenProject(document.getTextDocument().getUri());
		try {
			return project != null ? project.get().getMavenProject() : null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
		}
		return null;
	}

	/**
	 * Returns the last successfully parsed and cached Maven Project for the given
	 * POM file
	 * 
	 * @param pomFile A given POM File
	 * @return the last MavenDocument that could be build for the more recent
	 *         version of the provided document. If document fails to build a
	 *         MavenProject, a former version will be returned. Can be
	 *         <code>null</code>.
	 */
	public MavenProject getLastSuccessfulMavenProject(File pomFile) {
		CompletableFuture<LoadedMavenProject> project = getLoadedMavenProject(pomFile.toURI().toASCIIString());
		try {
			return project != null ? project.get().getMavenProject() : null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
		}
		return null;
	}

	/**
	 *
	 * @param document
	 * @return the problems for the latest version of the document (either in cache,
	 *         or the one passed in arguments)
	 */
	public CompletableFuture<LoadedMavenProject> getLoadedMavenProject(DOMDocument document) {
		String uri = document.getTextDocument().getUri();
		return getLoadedMavenProject(uri);
	}

	public Optional<MavenProject> getSnapshotProject(File file) {
		return Optional.of(getLastSuccessfulMavenProject(file));
	}
	
	public LoadedMavenProject build(FileModelSource source, CancelChecker cancelChecker) {
		Collection<ModelProblem> problems = new ArrayList<>();
		DependencyResolutionResult dependencyResolutionResult = null;
		MavenProject project = null;
		File file = source.getFile();
		try {			
			ProjectBuildingResult buildResult = projectBuilder.build(source, newProjectBuildingRequest());
			cancelChecker.checkCanceled();
			project = buildResult.getProject();
			problems.addAll(buildResult.getProblems());
			dependencyResolutionResult = buildResult.getDependencyResolutionResult();

			if (project != null) {
				// setFile should ideally be invoked during project build, but related methods
				// to pass modelSource and pomFile are private
				project.setFile(file);
			}
		} catch (ProjectBuildingException e) {
			if (e.getResults() == null) {
				if (e.getCause() instanceof ModelBuildingException modelBuildingException) {
					// Try to manually build a minimal project from the document to collect
					// lower-level
					// errors and to have something usable in cache for most basic operations
					modelBuildingException.getProblems().stream()
							.filter(p -> !(p.getException() instanceof ModelParseException)).forEach(problems::add);
					try (InputStream documentStream = source.getInputStream()) {
						Model model = mavenReader.read(documentStream);
						cancelChecker.checkCanceled();
						project = new MavenProject(model);
						project.setRemoteArtifactRepositories(model.getRepositories().stream()
								.map(repo -> new MavenArtifactRepository(repo.getId(), repo.getUrl(),
										new DefaultRepositoryLayout(),
										new ArtifactRepositoryPolicy(true,
												ArtifactRepositoryPolicy.UPDATE_POLICY_INTERVAL,
												ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN),
										new ArtifactRepositoryPolicy(true,
												ArtifactRepositoryPolicy.UPDATE_POLICY_INTERVAL,
												ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN)))
								.distinct().collect(Collectors.toList()));
						project.setFile(file);
						project.setBuild(new Build());
					} catch (XmlPullParserException parserException) {
						// XML document is invalid fo parsing (eg user is typing), it's a valid state
						// that shouldn't log
						// exceptions
					} catch (IOException ex) {
						LOGGER.log(Level.SEVERE, e.getMessage(), e);
					}
				} else {
					problems.add(
							new DefaultModelProblem(e.getMessage(), Severity.FATAL, Version.BASE, null, -1, -1, e));
				}
			} else {
				e.getResults().stream().flatMap(result -> result.getProblems().stream()).forEach(problems::add);
				if (e.getResults().size() == 1) {
					project = e.getResults().get(0).getProject();
					if (project != null) {
						project.setFile(file);
					}
				}
			}
		} catch (CancellationException e) {
			// The document which has been used to load Maven project is out of dated
			throw e;
		} catch (Exception e) {
			// Do not add any info, like lastCheckedVersion or problems, to the cache
			// In case of project/problems etc. is not available due to an exception
			// happened.
			//
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return null; // Nothing to be cached
		}
		return new LoadedMavenProject(project, problems, dependencyResolutionResult);
	}

	private ProjectBuildingRequest newProjectBuildingRequest() {
		return newProjectBuildingRequest(true);
	}

	private ProjectBuildingRequest newProjectBuildingRequest(boolean resolveDependencies) {
		ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
		MavenExecutionRequest mavenRequest = mavenSession.getRequest();
		request.setSystemProperties(mavenRequest.getSystemProperties());
		request.setLocalRepository(mavenRequest.getLocalRepository());
		request.setRemoteRepositories(mavenRequest.getRemoteRepositories());
		request.setPluginArtifactRepositories(mavenRequest.getPluginArtifactRepositories());
		// TODO more to transfer from mavenRequest to ProjectBuildingRequest?
		request.setRepositorySession(mavenSession.getRepositorySession());
		request.setResolveDependencies(resolveDependencies);

		// See: https://issues.apache.org/jira/browse/MRESOLVER-374
		request.getUserProperties().setProperty("aether.syncContext.named.factory", "noop");
		return request;
	}

	private void initializeMavenBuildState() {
		if (projectBuilder != null) {
			return;
		}
		try {
			projectBuilder = getPlexusContainer().lookup(ProjectBuilder.class);
			System.setProperty(DefaultProjectBuilder.DISABLE_GLOBAL_MODEL_CACHE_SYSTEM_PROPERTY,
					Boolean.toString(true));
		} catch (ComponentLookupException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public PlexusContainer getPlexusContainer() {
		return mavenSession.getContainer();
	}

	public Collection<MavenProject> getProjects() {
		return null; //projectCache.values().stream().map(LoadedMavenProject::getMavenProject).toList();
	}

	public MavenProject getSnapshotProject(DOMDocument document, String profileId) {
		return getSnapshotProject(document, profileId, true);
	}

	public MavenProject getSnapshotProject(DOMDocument document, String profileId, boolean resolveDependencies) {
		// it would be nice to directly rebuild from Model instead of reparsing text
		ProjectBuildingRequest request = newProjectBuildingRequest(resolveDependencies);
		if (profileId != null) {
			request.setActiveProfileIds(List.of(profileId));
		}
		try {
			DOMModelSource source = new DOMModelSource(document);
			return projectBuilder.build(source, request).getProject();
		} catch (CancellationException e) {
			// The document which has been used to load Maven project is out of dated
			throw e;
		} catch (ProjectBuildingException e) {
			List<ProjectBuildingResult> result = e.getResults();
			if (result != null && result.size() == 1 && result.get(0).getProject() != null) {
				return result.get(0).getProject();
			}
		}
		return null;
	}

	private CompletableFuture<LoadedMavenProject> getLoadedMavenProject(String uri) {
		LoadedMavenProjectProvider provider = projectCache.get(uri);
		if (provider == null) {
			synchronized (projectCache) {
				provider = projectCache.get(uri);
				if (provider == null) {
					provider = new LoadedMavenProjectProvider(uri, documentProvider, this);
					projectCache.put(uri, provider);
				}
			}
		}
		return provider.getLoadedMavenProject();
	}
}
