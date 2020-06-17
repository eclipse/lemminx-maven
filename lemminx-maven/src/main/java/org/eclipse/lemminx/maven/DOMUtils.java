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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
	
	public static List<DOMNode> findNodesByLocalName(final DOMDocument document, final String localName) {
		List<DOMNode> foundNodes = new ArrayList<>();
		Deque<DOMNode> nodes = new ArrayDeque<>();
		for (DOMNode node : document.getChildren()) {
			nodes.push(node);
		}
		while (!nodes.isEmpty()) {
			DOMNode node = nodes.pop();
				if (node.getLocalName() != null && node.getLocalName().equals(localName)) {
					foundNodes.add(node);
				}
			if (node.hasChildNodes()) {
				for (DOMNode childNode : node.getChildren()) {
					nodes.push(childNode);
				}
			}
		}
		return foundNodes;
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

	public static DOMNode findAncestorThatIsAChildOf(IPositionRequest request, String parentName) {
		if (parentName == null || request == null) {
			return null;
		}
		DOMNode parentNode = request.getNode().getParentNode();
		DOMNode ancestorNode = parentNode;
		try {
			while (!parentName.equals(parentNode.getLocalName())) {
				ancestorNode = parentNode;
				parentNode = parentNode.getParentNode();
			}
		} catch (NullPointerException e) {
			return null;
		}

		if (parentName.equals(parentNode.getLocalName())) {
			return ancestorNode;
		}
		return null;
	}
}
