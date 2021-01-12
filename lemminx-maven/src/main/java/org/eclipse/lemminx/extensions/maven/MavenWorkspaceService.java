/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.net.URI;

import org.eclipse.lemminx.services.extensions.IWorkspaceServiceParticipant;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.WorkspaceService;

public class MavenWorkspaceService implements IWorkspaceServiceParticipant {

	private MavenLemminxExtension mavenLemminxExtension;

	public MavenWorkspaceService(MavenLemminxExtension mavenLemminxExtension) {
		this.mavenLemminxExtension = mavenLemminxExtension;
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		this.mavenLemminxExtension.didChangeWorkspaceFolders(
				params.getEvent().getAdded().stream().map(WorkspaceFolder::getUri).map(URI::create).toArray(URI[]::new),
				params.getEvent().getRemoved().stream().map(WorkspaceFolder::getUri).map(URI::create).toArray(URI[]::new));
	}

}
