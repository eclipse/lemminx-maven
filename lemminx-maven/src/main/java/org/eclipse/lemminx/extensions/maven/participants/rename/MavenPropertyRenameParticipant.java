/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.rename;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROPERTIES_ELT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.project.MavenProject;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.rename.IPrepareRenameRequest;
import org.eclipse.lemminx.services.extensions.rename.IRenameParticipant;
import org.eclipse.lemminx.services.extensions.rename.IRenameRequest;
import org.eclipse.lemminx.services.extensions.rename.IRenameResponse;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class MavenPropertyRenameParticipant implements IRenameParticipant {
	private static final Logger LOGGER = Logger.getLogger(MavenPropertyRenameParticipant.class.getName());

	private static final String PROPERTY_START = "${";
	private static final String PROPERTY_END = "}";
	
	private final MavenLemminxExtension plugin;

	public MavenPropertyRenameParticipant(MavenLemminxExtension plugin) {
		this.plugin = plugin;
	}

	@Override
	public Either<Range, PrepareRenameResult> prepareRename(IPrepareRenameRequest request,
			CancelChecker cancelChecker) {
		if (!(request.getNode() instanceof DOMElement element)) {
			return null;
		}
		
		cancelChecker.checkCanceled();
		MavenProject project = plugin.getProjectCache().getLastSuccessfulMavenProject(request.getXMLDocument());

		cancelChecker.checkCanceled();
		Range range = getMavenPropertyRange(request.getOffset(), element,  project);

		cancelChecker.checkCanceled();
		return range == null ? null : Either.forLeft(range);
	}

	@Override
	public void doRename(IRenameRequest request, IRenameResponse renameResponse, CancelChecker cancelChecker) {
		DOMDocument document = request.getXMLDocument();
		String newPropertyName = request.getNewText();
		
		if (!(request.getNode() instanceof DOMElement element)) {
			return;
		}

		cancelChecker.checkCanceled();
		MavenProject thisProject = plugin.getProjectCache().getLastSuccessfulMavenProject(document);
		if (thisProject == null) {
			return;
		}
		
		cancelChecker.checkCanceled();
		Range range = getMavenPropertyRange(request.getOffset(), element,  thisProject);
		if (range == null) {
			return;
		}

		cancelChecker.checkCanceled();
		LinkedHashSet<MavenProject> projects = new LinkedHashSet<>();
		projects.add(thisProject);
		plugin.getProjectCache().getProjects().stream().forEach(child -> 
			projects.addAll(findParentsOfChildProject(thisProject, child)));

		String propertyName = element.getNodeName();
		URI thisProjectUri = ParticipantUtils.normalizedUri(document.getDocumentURI());
		projects.stream().forEach(project -> {
			cancelChecker.checkCanceled();
			URI projectUri = ParticipantUtils.normalizedUri(project.getFile().toURI().toString());
			DOMDocument projectDocumentt = null;
			if (projectUri.equals(thisProjectUri)) {
				projectDocumentt = document;
			} else {
				projectDocumentt = org.eclipse.lemminx.utils.DOMUtils.loadDocument(
					project.getFile().toURI().toString(),
					request.getNode().getOwnerDocument().getResolverExtensionManager());
			}
			
			cancelChecker.checkCanceled();
			// Collect Text Edits for the document
			List<TextEdit> projectTextEdits = new ArrayList<>();
			collectPropertyElementTextEdits(projectDocumentt, propertyName, newPropertyName, projectTextEdits, cancelChecker);
			collectPropertyUseTextEdits(projectDocumentt.getDocumentElement(), propertyName, newPropertyName, projectTextEdits, cancelChecker);
			VersionedTextDocumentIdentifier projectVersionedTextDocumentIdentifier = new VersionedTextDocumentIdentifier(
					projectDocumentt.getTextDocument().getUri(), projectDocumentt.getTextDocument().getVersion());
			renameResponse.addTextDocumentEdit(new TextDocumentEdit(projectVersionedTextDocumentIdentifier, projectTextEdits));
		});

		cancelChecker.checkCanceled();
	}

	/*
	 *  Returns the list of parent projects between the given child and parent project, 
	 *  the list includes the given child, but not the parent.
	 */
	private static List<MavenProject> findParentsOfChildProject(MavenProject parent, MavenProject child) {
		List<MavenProject> parents = new LinkedList<>();
		MavenProject currentChild = child;
		URI childProjectUri = ParticipantUtils.normalizedUri(currentChild.getFile().toURI().toString());
		URI thisProjectUri = ParticipantUtils.normalizedUri(parent.getFile().toURI().toString());
		
		while (currentChild != null) {
			parents.add(currentChild); // Include the child as well
			if (childProjectUri.equals(thisProjectUri)) {
				break; // Stop searching
			}
			currentChild = currentChild.getParent();
			if (currentChild != null) {
				childProjectUri = ParticipantUtils.normalizedUri(currentChild.getFile().toURI().toString());
			}
		}
		return currentChild != null ? parents : Collections.emptyList();
	}
	
	private static void collectPropertyElementTextEdits(DOMDocument document, String propertyName, String newPropertyName,
			List<TextEdit> textEdits, CancelChecker cancelChecker) {
		DOMUtils.findChildElement(document.getDocumentElement(), PROPERTIES_ELT).ifPresent(properties -> {
			DOMUtils.findChildElement(properties, propertyName).ifPresent(property -> {
				int startTagOpenOffset = property.getStartTagOpenOffset() + 1;
				int endTagOpenOffset = property.getEndTagOpenOffset() + 2;
				try {
					Range startRange = new Range(document.positionAt(startTagOpenOffset), 
							document.positionAt(startTagOpenOffset + propertyName.length()));
					Range endRange = new Range(document.positionAt(endTagOpenOffset), 
							document.positionAt(endTagOpenOffset + propertyName.length()));
					textEdits.add(new TextEdit(startRange, newPropertyName));
					textEdits.add(new TextEdit(endRange, newPropertyName));
				} catch (BadLocationException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			}); 
		});
	}
	
	private static void collectPropertyUseTextEdits(DOMElement rootElement, String property, String newPropertyName,
			List<TextEdit> textEditss, CancelChecker cancelChecker) throws CancellationException {
		cancelChecker.checkCanceled();
	
		// Check this element's text
		collectInElementTextEdits(rootElement, property, newPropertyName, textEditss, cancelChecker);
		
		// collect in this element's children 
		rootElement.getChildren().stream().filter(DOMElement.class::isInstance).map(DOMElement.class::cast)
			.forEach(child -> collectPropertyUseTextEdits(child, property, newPropertyName, textEditss, cancelChecker));
	}

	private static void collectInElementTextEdits(DOMElement element, String propertyName, String newPropertyName, 
			List<TextEdit> textEditss, CancelChecker cancelChecker) throws CancellationException {
		TextDocument textDocument = element.getOwnerDocument().getTextDocument();
		String propertyUse = PROPERTY_START + propertyName + PROPERTY_END;
		DOMUtils.findElementTextChildren(element).stream()
			.filter(text -> text.getData().contains(propertyUse))
			.forEach(text -> {
				String data = text.getData();
				int index = 0;
				for (index = data.indexOf(propertyUse); index != -1; index = data.indexOf(propertyUse, index + propertyUse.length())) {
					cancelChecker.checkCanceled();
					try {
						int propertyUseStart = text.getStart() + index;
						int replaceStart = propertyUseStart + PROPERTY_START.length();
						int replaceEnd= replaceStart + propertyName.length();
						Range range = new Range(textDocument.positionAt(replaceStart), 
								textDocument.positionAt(replaceEnd));
						textEditss.add(new TextEdit(range,  newPropertyName));
					} catch (BadLocationException e) {
						LOGGER.log(Level.SEVERE, e.getMessage(), e);
						return;
					}
				}
			});
	}

	private static Range getMavenPropertyRange(int offset, DOMElement propertyElement, MavenProject project) {
		if (propertyElement == null || project == null) {
			return null;
		}
		DOMElement parentElement = propertyElement.getParentElement();
		if (parentElement == null || !PROPERTIES_ELT.equals(parentElement.getNodeName())) {
			return null;
		}

		if (!propertyElement.hasEndTag()) {
			return null;
		}

		int startTagOpenOffset = propertyElement.getStartTagOpenOffset() + 1;
		int startTagCloseOffset = propertyElement.getStartTagCloseOffset();
		int endTagOpenOffset = propertyElement.getEndTagOpenOffset() + 2;
		int endTagCloseOffset = propertyElement.getEndTagCloseOffset();
		
		DOMDocument document = propertyElement.getOwnerDocument();
		Range range = null;
		try {
			if (offset >= startTagOpenOffset && offset < startTagCloseOffset) {
				range = new Range(document.positionAt(startTagOpenOffset), document.positionAt(startTagCloseOffset));
			} else if (offset >= endTagOpenOffset && offset < endTagCloseOffset) {
				range = new Range(document.positionAt(endTagOpenOffset), document.positionAt(endTagCloseOffset));
			} else {
				return null;
			}
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return null;
		}

		String mavenProperty = propertyElement.getNodeName();
		Map<String, String> properties = ParticipantUtils.getMavenProjectProperties(project);
		String value = properties.get(mavenProperty);
		if (value == null) {
			return null;
		}
		return range;
	}
}
