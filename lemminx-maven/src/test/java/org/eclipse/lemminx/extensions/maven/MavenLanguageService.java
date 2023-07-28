/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.utils.MavenLemminxTestsUtils;
import org.eclipse.lemminx.services.IXMLDocumentProvider;
import org.eclipse.lemminx.services.XMLLanguageService;

/**
 * Extends {@link XMLLanguageService} to do the Maven initialization synchronously.
 * 
 * @author Angelo ZERR
 *
 */
public class MavenLanguageService extends XMLLanguageService{
	
	private Map<String, DOMDocument> documents = new HashMap<>();
	
	public MavenLanguageService() {
		MavenLemminxExtension.setUnitTestMode(true);
		setDocumentProvider(new IXMLDocumentProvider() {
			
			@Override
			public DOMDocument getDocument(String uri) {
				DOMDocument doc = documents.get(uri);
				if (doc == null) {
					System.out.println(MavenLanguageService.this.getClass().getSimpleName() + ": getDocumenr() cannot find open document for URI " + uri);
					try {
						new RuntimeException(MavenLanguageService.this.getClass().getSimpleName() + ": getDocumenr() cannot find open document for URI " + uri)
							.printStackTrace();
					} catch (Exception e) {
						// TODO: handle exception
					}
//					synchronized (documents) {
//						try {
//							doc =  MavenLemminxTestsUtils.createDOMDocument(URI.create(uri), MavenLanguageService.this);
//							documents.put(uri, doc);
//						} catch (Exception e) {
//							// TODO: handle exception
//						}
//					}
				}
				return doc;
			}
		});
	}
	
	public void didOpen(DOMDocument document) {
		// We need to store the dom document instance that test created because
		// the dpcument content can changes in the test and when getDocument(String uri)
		// will be called it need to use this instance otherwise it will get the 
		// instance loaded from the file which have not changed
		documents.put(document.getDocumentURI(), document);
	}


}
