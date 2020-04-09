/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class NoMavenCentralIndexTestRule extends TestWatcher {
	@Override
	protected void starting(Description description) {
		RemoteRepositoryIndexSearcher.disableCentralIndex = true;
	}
}
