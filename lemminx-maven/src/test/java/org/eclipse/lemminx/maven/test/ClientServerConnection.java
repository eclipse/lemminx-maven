/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import org.eclipse.lemminx.XMLServerLauncher;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public class ClientServerConnection {

	private final Future<?> server;
	private final Future<Void> clientFuture;
	public final LanguageServer languageServer;
	protected List<Diagnostic> diagnostics;

	public ClientServerConnection() throws IOException {
		PipedInputStream serverInputStream = new PipedInputStream();
		PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
		PipedOutputStream serverOutputStream = new PipedOutputStream();
		PipedInputStream clientInputStream = new PipedInputStream(serverOutputStream);
		server = XMLServerLauncher.launch(serverInputStream, serverOutputStream);
		Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(new LanguageClient() {
			@Override public void telemetryEvent(Object object) {
			}

			@Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
				return null;
			}

			@Override public void showMessage(MessageParams messageParams) {
			}

			@Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
				ClientServerConnection.this.diagnostics = diagnostics.getDiagnostics();
			}

			@Override public void logMessage(MessageParams message) {
			}
		}, clientInputStream, clientOutputStream);
		clientFuture = clientLauncher.startListening();
		languageServer = clientLauncher.getRemoteProxy();
		InitializeParams initParams = new InitializeParams();
		initParams.setCapabilities(new ClientCapabilities(new WorkspaceClientCapabilities(), new TextDocumentClientCapabilities(), false));
		languageServer.initialize(initParams);
	}

	public void stop() throws InterruptedException, ExecutionException {
		languageServer.shutdown().get();
		clientFuture.cancel(true);
	}

	public boolean waitForDiagnostics(Predicate<Collection<Diagnostic>> predicate, int timeoutMilliseconds) {
		boolean res = false;
		long start = System.currentTimeMillis();
		do {
			final Collection<Diagnostic> currentDiagnostics = this.diagnostics;
			res = diagnostics != null && predicate.test(currentDiagnostics);
			if (!res) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while (!res && System.currentTimeMillis() - start < timeoutMilliseconds);
		return res;
	}

}
