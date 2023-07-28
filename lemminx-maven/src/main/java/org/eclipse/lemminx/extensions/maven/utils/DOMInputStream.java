/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.maven.MavenModelOutOfDatedException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * {@link InputStream} implementation whichuses the content of the
 * {@link DOMDocument} and throws a {@link CancellationException} while the
 * stream is reading as soon as the DOM document has the content which is
 * updated (when user type something in the XML editor).
 * 
 * @author azerr
 *
 */
class DOMInputStream extends ByteArrayInputStream {

	private final CancelChecker cancelChecker;

	public DOMInputStream(DOMDocument document) {
		super(document.getText().getBytes());
		this.cancelChecker = document.getCancelChecker();
	}

	@Override
	public synchronized int read() {
		checkCanceled();
		return super.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		checkCanceled();
		return super.read(b);
	}

	@Override
	public synchronized int read(byte[] b, int off, int len) {
		checkCanceled();
		return super.read(b, off, len);
	}

	@Override
	public synchronized byte[] readAllBytes() {
		checkCanceled();
		return super.readAllBytes();
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) {
		checkCanceled();
		return super.readNBytes(b, off, len);
	}

	@Override
	public byte[] readNBytes(int len) throws IOException {
		checkCanceled();
		return super.readNBytes(len);
	}

	@Override
	public synchronized void reset() {
		checkCanceled();
		super.reset();
	}

	@Override
	public void mark(int readAheadLimit) {
		checkCanceled();
		super.mark(readAheadLimit);
	}

	@Override
	public void close() throws IOException {
		checkCanceled();
		super.close();
	}

	@Override
	public boolean markSupported() {
		checkCanceled();
		return super.markSupported();
	}

	@Override
	public synchronized long skip(long n) {
		checkCanceled();
		return super.skip(n);
	}

	@Override
	public void skipNBytes(long n) throws IOException {
		checkCanceled();
		super.skipNBytes(n);
	}

	@Override
	public synchronized long transferTo(OutputStream out) throws IOException {
		checkCanceled();
		return super.transferTo(out);
	}

	@Override
	public synchronized int available() {
		checkCanceled();
		return super.available();
	}

	private void checkCanceled() {
		if (cancelChecker != null) {
			if(cancelChecker.isCanceled()) {
				throw new MavenModelOutOfDatedException();
			}
		}
	}

}
