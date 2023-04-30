/*******************************************************************************
 * Copyright (c) 2020, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMText;
import org.eclipse.lemminx.services.extensions.IPositionRequest;
import org.eclipse.lemminx.services.extensions.completion.ICompletionRequest;
import org.w3c.dom.Text;

public class DOMUtils {

	public static DOMNode findClosestParentNode(final DOMNode node, final String localName) {
		if (localName == null || node == null) {
			return null;
		}

		DOMNode parentNode = node;
		while (parentNode != null) {
			if (localName.equals(parentNode.getLocalName())) {
				return parentNode;
			}
			parentNode = parentNode.getParentNode();
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
	
	public static boolean isADescendantOf(DOMNode tag, String parentName) {
		DOMNode parent = tag;
		while (parent != null) {
			if (parent.getLocalName() != null && parent.getLocalName().equals(parentName)) {
				return true;
			}
			parent = parent.getParentNode();
		}
		return false;
	}

	public static Optional<DOMElement> findChildElement(DOMNode parent, String elementName) {
		return parent.getChildren().stream().filter(node -> elementName.equals(node.getLocalName()))
				.filter(DOMElement.class::isInstance).map(DOMElement.class::cast).findAny();
	}

	public static List<DOMElement> findChildElements(DOMElement parent, String elementName) {
		return parent.getChildren().stream().filter(node -> elementName.equals(node.getLocalName()))
				.filter(DOMElement.class::isInstance).map(DOMElement.class::cast).collect(Collectors.toList());
	}

	public static Optional<String> findChildElementText(DOMNode rootNode, final String elementName) {
		return findChildElement(rootNode, elementName) //
				.stream() //
				.flatMap(element -> element.getChildren().stream()) //
				.filter(Text.class::isInstance)
				.map(Text.class::cast)
				.map(Text::getData)
				.findFirst();
	}
	
	public static Optional<String> findElementText(DOMElement element) {
		return element.getChildren().stream() //
				.filter(Text.class::isInstance)
				.map(Text.class::cast)
				.map(Text::getData)
				.findFirst();
	}

	public static List<DOMText> findElementTextChildren(DOMElement element) {
		return element.getChildren().stream()
				.filter(DOMText.class::isInstance)
				.map(DOMText.class::cast)
				.collect(Collectors.toList());
	}
	
	public static String getOneLevelIndent(ICompletionRequest request) throws BadLocationException {
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
		if (parentNode == null) {
			return null;
		}
		
		DOMNode ancestorNode = parentNode;
		while(parentNode != null) {
			if (parentName.equals(parentNode.getLocalName())) {
				return ancestorNode;
			}
			ancestorNode = parentNode;
			parentNode = parentNode.getParentNode();
		}
		return null;
	}
}
