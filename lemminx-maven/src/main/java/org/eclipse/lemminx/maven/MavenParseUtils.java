/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import org.apache.maven.model.Dependency;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;

public class MavenParseUtils {

	public static Dependency parseArtifact(DOMNode node) {
		if (node == null) {
			return null;
		}
		if (node.isElement()) {
			Dependency res = parseArtifactInternal((DOMElement)node);
			if (res != null) {
				return res;
			}
			return parseArtifactInternal(node.getParentElement());
		}
		return parseArtifact(node.getParentElement());
	}

	private static Dependency parseArtifactInternal(DOMElement element) {
		if (element == null) {
			return null;
		}
		Dependency res = new Dependency();
		try {
			for (DOMNode tag : element.getChildren()) {
				if (tag != null && tag.hasChildNodes()) {
					String value = tag.getChild(0).getNodeValue();
					if (value == null) {
						continue;
					}
					value = value.trim(); 
					switch (tag.getLocalName()) {
					case "groupId":
						res.setGroupId(value);
						break;
					case "artifactId":
						res.setArtifactId(value);
						break;
					case "version":
						res.setVersion(value);
						break;
					case "scope":
						res.setScope(value);
						break;
					case "type":
						res.setType(value);
						break;
					case "classifier":
						res.setClassifier(value);
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error parsing Artifact");
		}
		return isEmpty(res) ? null : res;
	}

	private static boolean isEmpty(Dependency res) {
		return res.getGroupId() == null &&
			res.getArtifactId() == null &&
			res.getVersion() == null &&
			res.getScope() == null &&
			res.getClassifier() == null;
	}
}
