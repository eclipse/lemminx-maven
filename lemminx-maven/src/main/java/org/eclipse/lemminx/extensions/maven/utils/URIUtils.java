/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMDocument;

public class URIUtils {
	private static final Logger LOGGER = Logger.getLogger(URIUtils.class.getName());

	/**
	 * Returns an URI String identifying a specified document
	 * 
	 * @param document A Maven Project document
	 * @return URI String identificator
	 */
	public static String toURIString(DOMDocument document) {
		return document.getDocumentURI();
	}

	/**
	 * Returns an URI String identifying a specified POM file
	 * 
	 * @param file A POM file
	 * @return URI String identificator
	 */
	public static String toURIString(File file) {
		return toUri(file).toASCIIString();
	}

	/**
	 * Returns an URI String key for a specified document to be used to store
	 * a Maven Project document information in a map
	 * 
	 * @param document A Maven Project document
	 * @return URI String key
	 */
	public static String toURIKey(DOMDocument document) {
		return toURIKey(toURIString(document));
	}
	
	/**
	 * Returns an URI String key for a specified URI String identificator to be 
	 * used to store a Maven Project document information in a map
	 * 
	 * @param uriString URI String identificator
	 * @return URI String key
	 */
	public static String toURIKey(String uriString) {
		String normalizedURI = URI.create(uriString).normalize().toASCIIString() ;
		return URIUtils.encodeFileURI(normalizedURI, StandardCharsets.UTF_8)
				.toUpperCase();
	}
	
	/**
	 * Returns an URI String key for a specified POM file to be used to store
	 * a Maven Project document information in a map
	 * 
	 * @param file POM file
	 * @return URI String key
	 */
	public static String toURIKey(File file) {
		return toURIString(file).toUpperCase();
	}
	
	// Copied from https://github.com/eclipse/lsp4e/blob/master/org.eclipse.lsp4e/src/org/eclipse/lsp4e/LSPEclipseUtils.java
	private static URI toUri(File file) {
		// URI scheme specified by language server protocol and LSP
		try {
			return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (URISyntaxException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return file.getAbsoluteFile().toURI();
		}
	}
	
	// Copied from https://github.com/eclipse/lsp4mp/blob/master/microprofile.ls/org.eclipse.lsp4mp.ls/src/main/java/org/eclipse/lsp4mp/utils/URIUtils.java
	private static String encodeFileURI(String source, Charset charset) {
		String fileScheme = "";
		int index = -1;
		if (source.startsWith("file://")) {
			index = 6;
			if (source.charAt(7) == '/') {
				index = 7;
			}
		}
		if (index != -1) {
			fileScheme = source.substring(0, index + 1);
			source = source.substring(index + 1, source.length());
		}

		byte[] bytes = source.getBytes(charset);
		boolean original = true;
		for (byte b : bytes) {
			if (!isAllowed(b)) {
				original = false;
				break;
			}
		}
		if (original) {
			return source;
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
		for (byte b : bytes) {
			if (isAllowed(b)) {
				baos.write(b);
			} else {
				baos.write('%');
				char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
				char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
				baos.write(hex1);
				baos.write(hex2);
			}
		}
		return fileScheme + copyToString(baos, charset);
	}
	
	/**
	 * Copy the contents of the given {@link ByteArrayOutputStream} into a
	 * {@link String}.
	 * <p>
	 * This is a more effective equivalent of
	 * {@code new String(baos.toByteArray(), charset)}.
	 * 
	 * @param baos    the {@code ByteArrayOutputStream} to be copied into a String
	 * @param charset the {@link Charset} to use to decode the bytes
	 * @return the String that has been copied to (possibly empty)
	 */
	private static String copyToString(ByteArrayOutputStream baos, Charset charset) {
		try {
			// Can be replaced with toString(Charset) call in Java 10+
			return baos.toString(charset.name());
		} catch (UnsupportedEncodingException ex) {
			// Should never happen
			throw new IllegalArgumentException("Invalid charset name: " + charset, ex);
		}
	}
	
	private static boolean isAllowed(int c) {
		return isUnreserved(c) || '/' == c;
	}

	/**
	 * Indicates whether the given character is in the {@code unreserved} set.
	 * 
	 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
	 */
	private static boolean isUnreserved(int c) {
		return (isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c);
	}

	/**
	 * Indicates whether the given character is in the {@code ALPHA} set.
	 * 
	 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
	 */
	private static boolean isAlpha(int c) {
		return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
	}

	/**
	 * Indicates whether the given character is in the {@code DIGIT} set.
	 * 
	 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
	 */
	private static boolean isDigit(int c) {
		return (c >= '0' && c <= '9');
	}
}
