/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import org.eclipse.lemminx.maven.MavenPlugin;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ LocalRepoTests.class, MavenParseUtilsTest.class, MavenProjectCacheTest.class, PluginTest.class,
		RemoteRepositoryTest.class, SimpleModelTest.class })
public class AllTests {

	@BeforeClass
	public static void skipCentral() {
		System.err.println("local repo=" + MavenPlugin.LOCAL_REPOSITORY);
		RemoteRepositoryIndexSearcher.disableCentralIndex = true;
	};
}
