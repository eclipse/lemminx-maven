/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.List;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

public class DiagnosticRequest {
	private DOMNode node;
	private DOMDocument xmlDocument;
	private List<Diagnostic> diagnostics;

	public DiagnosticRequest(DOMNode node, DOMDocument xmlDocument, List<Diagnostic> diagnostics) {
		this.setNode(node);
		this.setDOMDocument(xmlDocument);
		this.setDiagnostics(diagnostics);
	}

	public DOMDocument getDOMDocument() {
		return xmlDocument;
	}

	public void setDOMDocument(DOMDocument xmlDocument) {
		this.xmlDocument = xmlDocument;
	}

	public DOMNode getNode() {
		return node;
	}

	public void setNode(DOMNode node) {
		this.node = node;
	}

	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
	}

	public Range getRange() {
		return XMLPositionUtility.createRange(((DOMElement) node).getStartTagCloseOffset() + 1,
				((DOMElement) node).getEndTagOpenOffset(), xmlDocument);
	}

}
