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
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * This workspace reader allows to resolve GAV to local workspaceFolders that match
 * instead of usual Maven repos.
 */
public class MavenLemminxWorkspaceReader implements WorkspaceReader {

	@Override
	public WorkspaceRepository getRepository() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File findArtifact(Artifact artifact) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> findVersions(Artifact artifact) {
		// TODO Auto-generated method stub
		return null;
	}

}
