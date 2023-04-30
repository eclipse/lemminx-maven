/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.CodeActionFactory;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMText;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class ExtractPropertyCodeAction implements ICodeActionParticipant {
	private static final Logger LOGGER = Logger.getLogger(ExtractPropertyCodeAction.class.getName());
	private final MavenLemminxExtension plugin;
	
	public ExtractPropertyCodeAction(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public void doCodeActionUnconditional(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker)  throws CancellationException {
		Range range = request.getRange();
		if (range == null) {
			return;
		}

		cancelChecker.checkCanceled();
		try {
			DOMDocument document = request.getDocument();
			int startOffset = document.offsetAt(range.getStart());
			int endOffset = document.offsetAt(range.getStart());
			
			// We cannot search by start nor by end of text node as these positions
			// will be treaded as parts of 'DOMElement'. 
			// Also, value length cannot be less than 1
			DOMNode node = document.findNodeAt(startOffset);
			if (node == null) {
				return;
			}

			if(node.isElement()) {
				 Optional<DOMText> text = node.getChildren().stream()
					.filter(DOMText.class::isInstance).map(DOMText.class::cast).findFirst();
				 if (text.isPresent()) {
					 node = text.get();
				 }
			}
			if (!node.isText() || !DOMNode.isIncluded(node, startOffset) 
					|| !DOMNode.isIncluded(node, endOffset))  {
				return;
			}
			
			String propertyValue = node.getNodeValue();
			if (propertyValue == null || propertyValue.trim().isBlank()) {
				return;
			}
			
			Map.Entry<Range, String> mavenProperty = ParticipantUtils.getMavenProperty(document.findNodeAt(startOffset), startOffset);
			if (mavenProperty != null) {
				// We cannot extract property from an existing property
				return;
			}
			
			cancelChecker.checkCanceled();
			MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(document);
			if (project != null) {
				cancelChecker.checkCanceled();
				Map<String, String> properties = ParticipantUtils.getMavenProjectProperties(project);
				String propertyName = constructPropertyName(project, node, properties, cancelChecker);
				
				cancelChecker.checkCanceled();
				Optional<DOMElement> propettiesElement = DOMUtils.findChildElement(document.getDocumentElement(), "properties");

				TextEdit propertiesHeaderTextEdit = createPropertiesHeaderTextEdit(request, 
						propettiesElement.isPresent() ? propettiesElement.get() : document.getDocumentElement(),
						propertyName, propertyValue, cancelChecker);

				// Create code action to extract a single value into a property
				List<TextEdit> singleTextEdits = new ArrayList<>();
				singleTextEdits.add(propertiesHeaderTextEdit);
				collectExtractPropertyTextEdit(request, (DOMText)node, propertyName, singleTextEdits, cancelChecker);
				
				// The list should contain header and one extracted property
				if (singleTextEdits.size() > 1) {
					codeActions.add(CodeActionFactory.replace(
							"Extract text value into a property", 
							singleTextEdits, document.getTextDocument(),  null));
				}

				// Create code action to extract all the value entries into a properties
				List<TextEdit> multipleTextEdits = new ArrayList<>();
				multipleTextEdits.add(propertiesHeaderTextEdit);
				collectExtractPropertyTextEdits(request, document.getDocumentElement(), (DOMText)node, 
						propertyName, propertyValue, multipleTextEdits, cancelChecker);

				// The list should contain header and two or more extracted properties
				if (multipleTextEdits.size() > 2) {
					codeActions.add(CodeActionFactory.replace(
							"Extract all the text value entries into a property", 
							multipleTextEdits, document.getTextDocument(),  null));
				}
			}
		} catch (CancellationException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	void collectExtractPropertyTextEdits(ICodeActionRequest request, DOMElement rootElement, DOMText text, String property, String value,
			List<TextEdit> textEditss, CancelChecker cancelChecker) throws CancellationException {
		cancelChecker.checkCanceled();

		String textElementName = text.getParentElement().getNodeName();
		if (textElementName.equals(rootElement.getNodeName())) {
			Optional<DOMText> rootElementTextNode = rootElement.getChildren().stream()
					.filter(DOMText.class::isInstance).map(DOMText.class::cast)
					.filter(textNode -> value.equals(textNode.getNodeValue()))
					.findFirst();
			rootElementTextNode.ifPresent(rootTextNode -> 
				collectExtractPropertyTextEdit(request, rootTextNode, property, textEditss, cancelChecker));		
		}
		
		// collect in this element's children 
		rootElement.getChildren().stream().filter(DOMElement.class::isInstance).map(DOMElement.class::cast)
			.forEach(child -> collectExtractPropertyTextEdits(request, child, text, property, value, textEditss, cancelChecker));
	}

	private static TextEdit createPropertiesHeaderTextEdit(ICodeActionRequest request, DOMElement parent, String propertyName, String propertyValue,
			CancelChecker cancelChecker) throws CancellationException {
		cancelChecker.checkCanceled();
		try {
			String newline = request.getXMLGenerator().getLineDelimiter();
			String indent = DOMUtils.getOneLevelIndent(request);

			// Properties section
			int start =  parent.getStartTagCloseOffset() + 1;
			StringBuilder sb = new StringBuilder();
			sb.append(newline);
			if ("project".equals(parent.getNodeName())) {
				// Create a new properties section header
				sb.append(indent).append("<properties>").append(newline);
			}
			// Add property
			sb.append(indent).append(indent)
				.append('<').append(propertyName).append('>').append(propertyValue)
				.append('<').append('/').append(propertyName).append('>').append(newline);
	
			if ("project".equals(parent.getNodeName())) {
				// Create a new properties section footer
				sb.append(indent).append("</properties>").append(newline);
			}
	
			Position startPosition = request.getDocument().positionAt(start);
			cancelChecker.checkCanceled();
			return new TextEdit(new Range(startPosition, startPosition),
					sb.toString());
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}
	
	private static void collectExtractPropertyTextEdit(ICodeActionRequest request, DOMText text, String propertyName,
			List<TextEdit> textEdits, CancelChecker cancelChecker) throws CancellationException {
		cancelChecker.checkCanceled();
		try {
			Position startPosition = request.getDocument().positionAt(text.getStart());
			Position endPosition = request.getDocument().positionAt(text.getEnd());
			textEdits.add(new TextEdit(new Range(startPosition, endPosition), "${" + propertyName + "}"));
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}	
	}

	private static String constructPropertyName(MavenProject project, DOMNode node, Map<String, String> properties, CancelChecker cancelChecker) {
		DOMElement parent = node.getParentElement();
		String property = null;
		cancelChecker.checkCanceled();
		
		// Try constructing from an artifact id
		Dependency artifact = ParticipantUtils.getArtifactToSearch(project, node);
		if (artifact != null && artifact.getArtifactId() != null) {
			property = artifact.getArtifactId() + '-' + parent.getNodeName();
		}
		
		// Try constructing from am id (f.i., execution id)
		if (property == null) {
			cancelChecker.checkCanceled();
			DOMElement grarndParent = parent.getParentElement();
			Optional<String> id = DOMUtils.findChildElementText(grarndParent, "id");
			if (id.isEmpty()) { // Checking only for two steps back
				id = DOMUtils.findChildElementText(grarndParent.getParentNode(), "id");
			}
			
			if (id.isPresent() && !id.get().isBlank()) {
				property = id.get().trim();
			}
		}
			
		// Search from a parent node
		if (property == null) {
			DOMElement grarndParent = parent.getParentElement();
			property = grarndParent.getNodeName() + '-' + parent.getNodeName();
		}

		int index = 0; 
		while (properties.get(property + (index > 0 ? '-' + index : "")) != null) {
			cancelChecker.checkCanceled();
			index++;
		}
		if (index > 0) {
			property += '-' + index;
		}
		cancelChecker.checkCanceled();
		return clearPropertyName(property);
	}
	
	private static String clearPropertyName(String value) {
		StringBuilder sb = new StringBuilder();
		value.trim().chars()
			.filter(ch -> Character.isAlphabetic(ch) || Character.isDigit(ch) || ch == '.' || ch == '-') 
			.forEach(ch -> {
				if (ch == '.' || ch == '-') {
					// Do not duplicate these chars
					if(sb.length() > 0 && ch != sb.charAt(sb.length() - 1)) {
						sb.append((char)ch);
					}
				} else {
					sb.append((char)ch);
				}
			});
		return sb.toString();
	}
}
