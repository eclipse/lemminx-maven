/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.codeaction;

import static org.eclipse.lemminx.client.ClientCommands.OPEN_URI;
import static org.eclipse.lemminx.extensions.maven.participants.diagnostics.ProjectValidator.ATTR_MANAGED_VERSION_LOCATION;
import static org.eclipse.lemminx.extensions.maven.participants.diagnostics.ProjectValidator.ATTR_MANAGED_VERSION_LINE;
import static org.eclipse.lemminx.extensions.maven.participants.diagnostics.ProjectValidator.ATTR_MANAGED_VERSION_COLUMN;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.CodeActionFactory;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.maven.participants.diagnostics.MavenSyntaxErrorCode;
import org.eclipse.lemminx.extensions.maven.participants.diagnostics.ProjectValidator;
import org.eclipse.lemminx.extensions.maven.utils.ParticipantUtils;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class MavenManagedVersionRemovalCodeAction  implements ICodeActionParticipant {
	private static final Logger LOGGER = Logger.getLogger(MavenIdPartRemovalCodeAction.class.getName());

	public MavenManagedVersionRemovalCodeAction() {
	}
	
	@Override
	public void doCodeAction(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker) {
		Diagnostic diagnostic = request.getDiagnostic();
		if (!ParticipantUtils.match(diagnostic, MavenSyntaxErrorCode.OverridingOfManagedDependency.getCode())
				&& !ParticipantUtils.match(diagnostic, MavenSyntaxErrorCode.OverridingOfManagedPlugin.getCode())) {
			return;
		}
		
		DOMDocument document = request.getDocument();
		Range diagnosticRange = diagnostic.getRange();

		TextDocument textDocument = document.getTextDocument();
		try {
			int startOffset = document.offsetAt(diagnosticRange.getStart());
			DOMNode version = document.findNodeAt(startOffset);
			if (version != null) {
				// It removes the current definition to rely on value inherited from parent
				Range valueRange = new Range(textDocument.positionAt(version.getStart()), textDocument.positionAt(version.getEnd()));
				CodeAction addSchemaAction = CodeActionFactory.remove(
						"Remove version declaration", 
						valueRange, document.getTextDocument(),  diagnostic);
				codeActions.add(addSchemaAction);
				
				// "Adds comment markup next to the affected element. No longer shows the warning afterwards
				valueRange = new Range(textDocument.positionAt(version.getEnd()), textDocument.positionAt(version.getEnd()));
				CodeAction addIgnoreMarkup = CodeActionFactory.insert("Ignore this warning", valueRange.getEnd(),
						"<!--" + ProjectValidator.MARKER_IGNORE_MANAGED + "-->", document.getTextDocument(),  diagnostic);
				codeActions.add(addIgnoreMarkup);
				
				// Opens the declaration of managed version
				if (diagnostic.getData() instanceof Map<?, ?>  dataMap) {
					Object locationObj = dataMap.get(ATTR_MANAGED_VERSION_LOCATION);
					if (locationObj instanceof String location) {
						int line = -1;
						int column = -1;
						try {
							Object lineObj = dataMap.get(ATTR_MANAGED_VERSION_LINE);
							if (lineObj instanceof String lineString) {
								line = Integer.valueOf(lineString);
							}
							Object columnObj = dataMap.get(ATTR_MANAGED_VERSION_COLUMN);
							if (columnObj instanceof String columnString) {
								column = Integer.valueOf(columnString);
							}
						} catch (NumberFormatException e) {
							// Ignore
						}

						boolean canSupportOpenUri = true; // TODO: Should be asked from ICodeActionRequest?
						String uri = location;
						if (line > -1) { 
							uri += "#L" + line;
							if (column > -1) {
								uri += "," + column;
							}
						}
						CodeAction openLocation = CodeActionFactory.createCommand("Open declaration of managed version", 
								canSupportOpenUri ? OPEN_URI : "",
								canSupportOpenUri ? Arrays.asList(uri) : null,
								diagnostic);
						codeActions.add(openLocation);
					}
				}
			}
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, "Unable to remove specified element", e);
		}
	}
}
