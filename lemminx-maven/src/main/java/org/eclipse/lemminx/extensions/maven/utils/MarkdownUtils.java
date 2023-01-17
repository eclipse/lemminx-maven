/*******************************************************************************
 * Copyright (c) 2020, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

public class MarkdownUtils {
	public static final String LINE_BREAK = "\n\n";

	private MarkdownUtils() {}
	
	public static String getLineBreak(boolean supportsMarkdown) {
		return supportsMarkdown ? MarkdownUtils.LINE_BREAK : "\n";
	}
	
	public static String toBold(String message) {
		StringBuilder bold = new StringBuilder();

		bold.append("**");
		bold.append(message.trim());
		bold.append("**");
		
		if (message.endsWith(" ")) {
			bold.append(" ");
		}
		
		return bold.toString();
	}

	public static String htmlXMLToMarkdown(String description) {
		int openPre = description.indexOf("<pre>");
		int closePre = description.indexOf("</pre>", openPre);
		if (openPre >= 0 && description.contains("&lt;") && closePre > openPre) {
			//Add markdown formatting to XML
			String xmlContent = description.substring(openPre + "<pre>".length() + 1, closePre - 1);
			description = description.substring(0, openPre);
			xmlContent = xmlContent.replaceAll("&lt;", "<");
			xmlContent = xmlContent.replaceAll("&gt;", ">");
			xmlContent = "```XML" + "\n" + xmlContent + "\n" + "```";
			description = description + LINE_BREAK + xmlContent;
		}
		return description;
	}
	
	public static String toLink(String uri, String message, String title) {
		StringBuilder link = new StringBuilder();
		
		// [Message](http://example.com/ "Title")
		link.append('[').append(message != null ? message : "This link").append(']');   
		if (uri != null) {
			link.append('(').append(uri);
			if (title != null && title.trim().length() > 0) {
				link.append(' ').append('"').append(title.trim()).append('"');
			}
			link.append(')');
		}
		return link.toString();
	}
}
