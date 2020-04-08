/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.snippets;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.utils.StringUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public class SnippetRegistry {

	private static final SnippetRegistry INSTANCE = new SnippetRegistry();

	public static SnippetRegistry getInstance() {
		return INSTANCE;
	}

	private static class LineContext {

		private final TextDocument document;
		private final int completionOffset;
		private String lineDelimiter;
		private String whitespacesIndent;

		public LineContext(TextDocument document, int completionOffset) {
			this.document = document;
			this.completionOffset = completionOffset;
		}

		public String getLineDelimiter() {
			if (lineDelimiter == null) {
				compute();
			}
			return lineDelimiter;
		}

		private void compute() {
			try {
				int lineNumber = document.positionAt(completionOffset).getLine();
				String lineText = document.lineText(lineNumber);
				lineDelimiter = document.lineDelimiter(lineNumber);
				whitespacesIndent = StringUtils.getStartWhitespaces(lineText);
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public String getWhitespacesIndent() {
			if (whitespacesIndent == null) {
				compute();
			}
			return whitespacesIndent;
		}

	}

	private final List<Snippet> snippets;

	public SnippetRegistry() {
		snippets = new ArrayList<>();
	}

	public void registerSnippet(Snippet snippet) {
		snippets.add(snippet);
	}

	public void load(InputStream in) throws IOException {
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			Snippet snippet = new GsonBuilder().create().fromJson(reader, Snippet.class);
			if (snippet.getDescription() == null) {
				snippet.setDescription(name);
			}
			registerSnippet(snippet);
		}
		reader.endObject();
	}

	public List<Snippet> getSnippets() {
		return snippets;
	}

	public Collection<CompletionItem> getCompletionItems(TextDocument document, int completionOffset,
			boolean canSupportMarkdown, Predicate<SnippetContext> contextFilter) {
		LineContext lineContext = new LineContext(document, completionOffset);
		return getSnippets().stream().filter(s -> {
			if (s.getContext() == null) {
				return true;
			}
			return contextFilter.test(s.getContext());
		}).map(snippet -> {
			String filter = snippet.getPrefix();
			int startOffset = StringUtils.findExprBeforeAt(document.getText(), filter, completionOffset);
			if (startOffset == -1) {
				startOffset = completionOffset;
			}
			int endOffset = completionOffset;
			try {
				Range range = getReplaceRange(startOffset + 1, endOffset, document, completionOffset);
				String label = snippet.getPrefix();
				CompletionItem item = new CompletionItem();
				item.setLabel(label);
				String insertText = getInsertText(snippet, true, lineContext);
				item.setKind(CompletionItemKind.Snippet);
				item.setDocumentation(Either.forRight(createDocumentation(snippet, canSupportMarkdown, lineContext)));
				item.setFilterText(insertText);
				item.setTextEdit(new TextEdit(range, insertText));
				item.setInsertTextFormat(InsertTextFormat.Snippet);
				return item;
			} catch (BadLocationException e) {
				e.printStackTrace();
				return null;
			}

		}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	private static MarkupContent createDocumentation(Snippet snippet, boolean canSupportMarkdown,
			LineContext lineContext) {
		String description = snippet.getDescription();
		StringBuilder doc = new StringBuilder(description);
		doc.append(System.lineSeparator());
		if (canSupportMarkdown) {
			doc.append("___");
		}
		doc.append(System.lineSeparator());
		if (canSupportMarkdown) {
			doc.append(System.lineSeparator());
			doc.append("```");
			String scope = snippet.getScope();
			if (scope != null) {
				doc.append(scope);
			}
			doc.append(System.lineSeparator());
		}
		String insertText = getInsertText(snippet, false, lineContext);
		doc.append(insertText);
		if (canSupportMarkdown) {
			doc.append("```");
			doc.append(System.lineSeparator());
		}
		return new MarkupContent(canSupportMarkdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, doc.toString());
	}

	private static String getInsertText(Snippet snippet, boolean indent, LineContext lineContext) {
		StringBuilder text = new StringBuilder();
		int i = 0;
		for (String bodyLine : snippet.getBody()) {
			if (i > 0) {
				text.append(lineContext.getLineDelimiter());
				if (indent) {
					text.append(lineContext.getWhitespacesIndent());
				}
			}
			text.append(bodyLine);
			i++;
		}
		text.append(lineContext.getLineDelimiter());
		return text.toString();
	}

	private static Range getReplaceRange(int replaceStart, int replaceEnd, TextDocument document, int offset)
			throws BadLocationException {
		if (replaceStart > offset) {
			replaceStart = offset;
		}
		return new Range(document.positionAt(replaceStart), document.positionAt(replaceEnd));
	}
}
