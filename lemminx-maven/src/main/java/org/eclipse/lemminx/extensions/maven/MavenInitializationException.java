/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.concurrent.CompletableFuture;

/**
 * Exception thrown when Maven is initializing.
 * 
 * @author Angelo ZERR
 *
 */
public class MavenInitializationException extends RuntimeException {
	private static final long serialVersionUID = 6811208967929378721L;
	
	private final CompletableFuture<Void> future;

	public MavenInitializationException(CompletableFuture<Void> future) {
		super();
		this.future = future;
	}

	public CompletableFuture<Void> getFuture() {
		return future;
	}
}
