/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven.test;

import static org.junit.Assert.assertEquals;

import org.apache.maven.Maven;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.maven.MavenParseUtils;
import org.junit.Test;

public class MavenParseUtilsTest {

	@Test
	public void testAsParentNode() {
		DOMDocument document = DOMParser.getInstance().parse(new TextDocument("<blah><groupId>aGroupId</groupId><artifactId>anArtifactId</artifactId><version>aVersion</version></blah>", Maven.POMv4), null);
		assertEquals("anArtifactId", MavenParseUtils.parseArtifact(document.getRoots().get(0)).getArtifactId());
		assertEquals("anArtifactId", MavenParseUtils.parseArtifact(document.getRoots().get(0).getChild(0)).getArtifactId());
	}
}
