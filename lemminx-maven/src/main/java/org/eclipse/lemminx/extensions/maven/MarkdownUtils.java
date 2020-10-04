/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

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
		if (description.contains("<pre>") && description.contains("&lt;")) {
			//Add markdown formatting to XML
			String xmlContent = description.substring(description.indexOf("<pre>") + 6, description.indexOf("</pre>") - 1);
			description = description.substring(0, description.indexOf("<pre>"));
			xmlContent = xmlContent.replaceAll("&lt;", "<");
			xmlContent = xmlContent.replaceAll("&gt;", ">");
			xmlContent = "```XML" + "\n" + xmlContent + "\n" + "```";
			description = description + LINE_BREAK + xmlContent;
		}
		return description;
	}
}
