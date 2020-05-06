/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.services.extensions.ICompletionRequest;
import org.eclipse.lemminx.services.extensions.IPositionRequest;

public class DOMUtils {

	public static DOMNode findClosestParentNode(final IPositionRequest request, final String localName) {
		if (localName == null || request == null) {
			return null;
		}
		DOMNode pluginNode = request.getNode();
		try {
			while (!localName.equals(pluginNode.getLocalName())) {
				pluginNode = pluginNode.getParentNode();
			}
		} catch (NullPointerException e) {
			return null;
		}

		if (localName.equals(pluginNode.getLocalName())) {
			return pluginNode;
		}
		return null;
	}
	
	// TODO: use optional?
	public static DOMNode findNodeByLocalName(final DOMDocument document, final String localName) {
		Deque<DOMNode> nodes = new ArrayDeque<>();
		for (DOMNode node : document.getChildren()) {
			nodes.push(node);
		}
		while (!nodes.isEmpty()) {
			DOMNode node = nodes.pop();
				if (node.getLocalName() != null && node.getLocalName().equals(localName)) {
					return node;
				}
			if (node.hasChildNodes()) {
				for (DOMNode childNode : node.getChildren()) {
					nodes.push(childNode);
				}
			}
		}
		return null;
	}

	public static Optional<String> findChildElementText(DOMNode pluginNode, final String elementName) {
		return pluginNode.getChildren().stream().filter(node -> elementName.equals(node.getLocalName()))
				.flatMap(node -> node.getChildren().stream()).findAny().map(DOMNode::getTextContent).map(String::trim);
	}

	static String getOneLevelIndent(ICompletionRequest request) throws BadLocationException {
		String oneLevelIndent = request.getLineIndentInfo().getWhitespacesIndent();
		int nodeDepth = 0;
		DOMElement element = request.getParentElement();
		while (element != null) {
			nodeDepth++;
			element = element.getParentElement();
		}
		oneLevelIndent = oneLevelIndent.substring(0, oneLevelIndent.length() / nodeDepth);
		return oneLevelIndent;
	}
}
