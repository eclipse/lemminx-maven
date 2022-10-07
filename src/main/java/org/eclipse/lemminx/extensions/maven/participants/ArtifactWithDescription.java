/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class ArtifactWithDescription {

	public final Artifact artifact;
	public final String description;

	public ArtifactWithDescription(MavenProject p) {
		this.artifact = new DefaultArtifact(p.getGroupId(), p.getArtifactId(), null, p.getVersion());
		this.description = p.getDescription();
	}

	public ArtifactWithDescription(Artifact a) {
		this.artifact = a;
		this.description = null;
	}
}
