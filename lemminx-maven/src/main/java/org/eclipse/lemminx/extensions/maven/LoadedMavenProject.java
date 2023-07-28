/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.Collection;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.dom.DOMDocument;

/**
 * Loaded maven project from:
 * 
 * <ul>
 * <li>a given pom.xml file</li>
 * <li>from a {@link DOMDocument} when the pom.xml is editing</li>
 * </ul>
 * 
 * @author Angelo ZERR
 *
 */
public class LoadedMavenProject {

	private final MavenProject mavenProject;
	private int lastCheckedVersion;
	private final Collection<ModelProblem> problems;
	private final DependencyResolutionResult dependencyResolutionResult;

	public LoadedMavenProject(MavenProject mavenProject, Collection<ModelProblem> problems,
			DependencyResolutionResult dependencyResolutionResult) {
		this.mavenProject = mavenProject;
		this.problems = problems;
		this.dependencyResolutionResult = dependencyResolutionResult;
	}

	/**
	 * Returns the loaded maven project if the pom.xml content is valid and null
	 * otherwise.
	 * 
	 * @return the loaded maven project if the pom.xml content is valid and null
	 *         otherwise.
	 */
	public MavenProject getMavenProject() {
		return mavenProject;
	}

	/**
	 * Returns the list of maven problems during the load of the pom.xml.
	 * 
	 * @return the list of maven problems during the load of the pom.xml.
	 */
	public Collection<ModelProblem> getProblems() {
		return problems;
	}

	/**
	 * Returns the list of dependencies which can be not resolved during the load of
	 * the pom.xml.
	 * 
	 * @return the list of dependencies which can be not resolved during the load of
	 *         the pom.xml.
	 */
	public DependencyResolutionResult getDependencyResolutionResult() {
		return dependencyResolutionResult;
	}

	/**
	 * Returns the last checked version of the document of the pom.xml.
	 * <p>
	 * 0 means that the loaded maven project has been loaded by a pom.xml file (it
	 * is not editing).
	 * </p>
	 * 
	 * @return the last checked version of the document of the pom.xml.
	 */
	public int getLastCheckedVersion() {
		return lastCheckedVersion;
	}
}
