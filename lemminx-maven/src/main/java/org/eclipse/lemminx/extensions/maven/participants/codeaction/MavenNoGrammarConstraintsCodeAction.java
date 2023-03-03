/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import static org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils.getDocumentLineSeparator;

import java.util.List;

import org.eclipse.lemminx.commons.CodeActionFactory;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.participants.XMLSyntaxErrorCode;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenNoGrammarConstraintsCodeAction implements ICodeActionParticipant {
	public static final String XSI_VALUE_PATTERN = " xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"%s" //$NON-NLS-1$
		      + "    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\""; //$NON-NLS-1$

	@Override
	public void doCodeAction(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker) {
		Diagnostic diagnostic = request.getDiagnostic();
		
		if (!ParticipantUtils.match(diagnostic, XMLSyntaxErrorCode.NoGrammarConstraints.getCode())) {
			return;
		}
		DOMDocument document = request.getDocument();
		Range diagnosticRange = diagnostic.getRange();

		// Add Maven XML Schema declaration
		String insertText = String.format(XSI_VALUE_PATTERN, getDocumentLineSeparator(document));
		CodeAction addSchemaAction = CodeActionFactory.insert("Add Maven XML Schema declaration", diagnosticRange.getEnd(), 
				insertText, document.getTextDocument(),  diagnostic);
		codeActions.add(addSchemaAction);
	}
}
