/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.lemminx.dom.DOMDocument;
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
				return documents.get(uri);
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
