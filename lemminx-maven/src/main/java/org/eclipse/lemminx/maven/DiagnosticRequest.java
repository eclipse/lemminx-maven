/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.LineIndentInfo;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class DiagnosticRequest implements IPositionRequest {
	private DOMNode node;
	private DOMDocument xmlDocument;
	
	/**
	 * A diagnosticRequest allows creation of diagnostics for a supplied node.
	 * 
	 * @param node        The node where diagnostics should be added.
	 * @param xmlDocument The XMLDocument where the diagnostics will appear
	 */
	public DiagnosticRequest(DOMNode node, DOMDocument xmlDocument) {
		this.node = node;
		this.xmlDocument = xmlDocument;
	}
	
	public Diagnostic createDiagnostic(String errorMessage, DiagnosticSeverity severity) {
		return new Diagnostic(this.getRange(), errorMessage, severity, this.getXMLDocument().getDocumentURI(), "XML");
	}
	
	private Range getRange() {
		return XMLPositionUtility.createRange(((DOMElement) node).getStartTagCloseOffset() + 1,
				((DOMElement) node).getEndTagOpenOffset(), xmlDocument);
	}
	
	@Override
	public DOMDocument getXMLDocument() {
		return xmlDocument;
	}

	@Override
	public DOMNode getNode() {
		return node;
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
