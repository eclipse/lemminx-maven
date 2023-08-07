/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.project;

import java.io.File;

import org.apache.maven.project.MavenProject;

/**
 * A listener to be used to be notified when a new or updated Maven Project is 
 * built and cached.
 */
public interface IMavenProjectBuildListener {
	
	/**
	 * Called when a new or updated Maven Project is built and cached
	 * 
	 * @param repository A repository holding the project's built artifacts
	 * @param mavenProject A new/updated Maven Project
	 */
	void builtMavenProject(File repository, MavenProject mavenProject);
}
