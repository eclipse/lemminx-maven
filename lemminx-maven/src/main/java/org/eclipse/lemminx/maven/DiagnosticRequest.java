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

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.LineIndentInfo;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class DiagnosticRequest implements IPositionRequest {
	private DOMNode node;
	private DOMDocument xmlDocument;
	private List<Diagnostic> diagnostics;

	public DiagnosticRequest(DOMNode node, DOMDocument xmlDocument, List<Diagnostic> diagnostics) {
		this.setNode(node);
		this.setXMLDocument(xmlDocument);
		this.setDiagnostics(diagnostics);
	}

	public DOMDocument getXMLDocument() {
		return xmlDocument;
	}

	private void setXMLDocument(DOMDocument xmlDocument) {
		this.xmlDocument = xmlDocument;
	}

	@Override
	public DOMNode getNode() {
		return node;
	}

	private void setNode(DOMNode node) {
		this.node = node;
	}

	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	private void setDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
	}

	public Range getRange() {
		return XMLPositionUtility.createRange(((DOMElement) node).getStartTagCloseOffset() + 1,
				((DOMElement) node).getEndTagOpenOffset(), xmlDocument);
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public Position getPosition() {
		return null;
	}

	@Override
	public DOMElement getParentElement() {
		return this.node.getParentElement();
	}

	@Override
	public String getCurrentTag() {
		return this.node.getLocalName();
	}

	@Override
	public String getCurrentAttributeName() {
		return null;
	}

	@Override
	public LineIndentInfo getLineIndentInfo() throws BadLocationException {
		return null;
	}

	@Override
	public <T> T getComponent(Class clazz) {
		// TODO: Not sure how to implement this..
		return null;
	}

}
