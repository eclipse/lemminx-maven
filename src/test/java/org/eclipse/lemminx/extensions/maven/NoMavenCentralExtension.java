/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import org.eclipse.lemminx.extensions.maven.searcher.RemoteCentralRepositorySearcher;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class NoMavenCentralExtension implements BeforeAllCallback {

	@Override
	public void beforeAll(ExtensionContext arg0) throws Exception {
		RemoteCentralRepositorySearcher.disableCentralSearch = true;
	}

}