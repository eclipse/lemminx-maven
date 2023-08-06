/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.PROJECT_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.CodeActionFactory;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.extensions.maven.participants.diagnostics.MavenSyntaxErrorCode;
import org.eclipse.lemminx.extensions.maven.utils.DOMUtils;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenIdPartRemovalCodeAction implements ICodeActionParticipant {
	private static final Logger LOGGER = Logger.getLogger(MavenIdPartRemovalCodeAction.class.getName());

	public MavenIdPartRemovalCodeAction() {
	}
	
	@Override
	public void doCodeAction(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker) {
		Diagnostic diagnostic = request.getDiagnostic();

		boolean isVersion;
		if (ParticipantUtils.match(diagnostic, MavenSyntaxErrorCode.DuplicationOfParentGroupId.getCode())) {
			isVersion = false;
		} else if (ParticipantUtils.match(diagnostic, MavenSyntaxErrorCode.DuplicationOfParentVersion.getCode())) {
			isVersion = true;
		} else {
			// Code is not supported by this Code Action
			return;
		}

		DOMDocument document = request.getDocument();
		Optional<DOMElement> project = DOMUtils.findChildElement(document, PROJECT_ELT);
		project.flatMap(p ->DOMUtils.findChildElement(project.get(), isVersion ? VERSION_ELT : GROUP_ID_ELT))
			.filter(DOMElement.class::isInstance).map(DOMElement.class::cast).ifPresent(v -> {
				TextDocument textDocument = document.getTextDocument();
				try {
					Range valueRange = new Range(textDocument.positionAt(v.getStart()), textDocument.positionAt(v.getEnd()));
					// It removes the current definition to rely on value inherited from parent
					CodeAction addSchemaAction = CodeActionFactory.remove(
							isVersion ? "Remove version declaration" : "Remove groupId declaration",
							valueRange, document.getTextDocument(),  diagnostic);
					codeActions.add(addSchemaAction);
				} catch (BadLocationException e) {
					LOGGER.log(Level.SEVERE, "Unable to remove specified element", e);
				}
			});
	}
}
