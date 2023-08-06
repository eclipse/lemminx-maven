/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.project;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.model.building.FileModelSource;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.project.MavenProjectCache.ProjectBuildManager;
import org.eclipse.lemminx.extensions.maven.utils.DOMModelSource;
import org.eclipse.lemminx.services.IXMLDocumentProvider;
import org.eclipse.lemminx.utils.FilesUtils;

/**
 * An object aggregating a build results of a Maven Document, controlling the 
 * asynchronous access to the Maven Project built from the latest version of 
 * the provided document.
 */
public class LoadedMavenProjectProvider {
	private static final Logger LOGGER = Logger.getLogger(LoadedMavenProjectProvider.class.getName());

	private final String uri;
	private final IXMLDocumentProvider documentProvider;
	private final ProjectBuildManager buildManager;

	private int lastCheckedVersion;
	private CompletableFuture<LoadedMavenProject> future;
	
	/**
	 * Creates a LoadedMavenProjectProvider using provided URI String identifying the 
	 * document to be built into a Maven Project. A document found by using Document 
	 * Provider is the latest version of the document being currently edited or a document 
	 * read from a file specified by document URI String.
	 * 
	 * @param uri A URI String identifying the document
	 * @param documentProvider An IXMLDocumentProvider instance used to find the latest 
	 * 		version of the document
	 * @param buildManager A MavenProject builder
	 */
	public LoadedMavenProjectProvider(String uri, IXMLDocumentProvider documentProvider, ProjectBuildManager buildManager) {
		this.uri = uri;
		this.documentProvider = documentProvider;
		this.buildManager = buildManager;
		this.lastCheckedVersion = -1;
	}
	
	/**
	 * Returns a `CompletableFuture<LoadedMavenProject>` for asynchronous access
	 * to the Maven Project built from the latest version of the document.
	 *  
	 * @return CompletableFuture of LoadedMavenProject object 
	 */
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
	}
	
	/**
	 * Returns URI String identifying a Maven Project document or file
	 * 
	 * @return
	 */
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