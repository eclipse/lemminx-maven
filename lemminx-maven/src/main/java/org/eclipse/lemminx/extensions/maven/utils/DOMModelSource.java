/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.utils.FilesUtils;

/**
 * A Maven {@link ModelSource} implementation based on LemMinx
 * {@link DOMDocument}.
 * 
 * This class provides the capability to use the content of the DOM document and
 * cancel the load of the Maven Project as soon as the DOM document has the
 * content which is updated (when user type something in the XML editor).
 * 
 * @author azerr
 *
 */
public class DOMModelSource extends FileModelSource {

	private final DOMDocument document;

	public DOMModelSource(DOMDocument document) {
		super(FilesUtils.toFile(document.getDocumentURI()));
		this.document = document;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new DOMInputStream(document);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(getFile(), document.getTextDocument().getVersion());
	}
}
