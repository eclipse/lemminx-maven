/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.model.building.FileModelSource;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.utils.DOMModelSource;
import org.eclipse.lemminx.extensions.maven.MavenProjectCache.ProjectBuildManager;

import org.eclipse.lemminx.services.IXMLDocumentProvider;
import org.eclipse.lemminx.utils.FilesUtils;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class LoadedMavenProjectProvider {
	private static final Logger LOGGER = Logger.getLogger(LoadedMavenProjectProvider.class.getName());

	private final String uri;

	private final IXMLDocumentProvider documentProvider;
	
	private final ProjectBuildManager buildManager;
	
	private int lastCheckedVersion;

	private CompletableFuture<LoadedMavenProject> future;
	public LoadedMavenProjectProvider(String uri, IXMLDocumentProvider documentProvider, ProjectBuildManager buildManager) {
		this.uri = uri;
		this.documentProvider = documentProvider;
		this.buildManager = buildManager;
		this.lastCheckedVersion = -1;
	}
	public CompletableFuture<LoadedMavenProject> getLoadedMavenProject() {
		DOMDocument document = documentProvider.getDocument(uri);
		// Check if future must be created
		// 1. is the future exist?
		boolean shouldLoad = future == null || future.isCompletedExceptionally();
		if (!shouldLoad) {
			// 2. is the current future is not out of dated?
			if (document != null) {
				if (lastCheckedVersion !=  document.getTextDocument().getVersion()) {
					shouldLoad = true;
				}				
			}
		}
		
		if (shouldLoad) {
			if (future != null) {
				future.cancel(true);
			}
			if (document != null) {
				lastCheckedVersion = document.getTextDocument().getVersion();
			}
			future = load(uri, document);
		}
		return future;
	}
	
	private CompletableFuture<LoadedMavenProject> load(String uri, DOMDocument document) {
//		return CompletableFutures.computeAsync(cancelChecker -> {			
//			cancelChecker.checkCanceled();
		try {
			FileModelSource source = null;			
			if (document != null) {
				source  = new DOMModelSource(document);				
			} else {
				source = new FileModelSource(FilesUtils.toFile(uri));
			}
			return buildManager.build(uri, source);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage() + ": " + uri, e);
			throw e;
		}
//		});
	}
	
	public String getUri() {
		return uri;
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