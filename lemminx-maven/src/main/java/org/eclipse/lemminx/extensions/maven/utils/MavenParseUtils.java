/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.utils;

import static org.eclipse.lemminx.extensions.maven.DOMConstants.ARTIFACT_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.CLASSIFIER_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.GROUP_ID_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.SCOPE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.TYPE_ELT;
import static org.eclipse.lemminx.extensions.maven.DOMConstants.VERSION_ELT;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.model.Dependency;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;

public class MavenParseUtils {

	private static final Logger LOGGER = Logger.getLogger(MavenParseUtils.class.getName());

	public static Dependency parseArtifact(DOMNode node) {
		if (node == null) {
			return null;
		}
		if (node.isElement()) {
			Dependency res = parseArtifactInternal((DOMElement) node);
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
					case GROUP_ID_ELT:
						res.setGroupId(value);
						break;
					case ARTIFACT_ID_ELT:
						res.setArtifactId(value);
						break;
					case VERSION_ELT:
						res.setVersion(value);
						break;
					case SCOPE_ELT:
						res.setScope(value);
						break;
					case TYPE_ELT:
						res.setType(value);
						break;
					case CLASSIFIER_ELT:
						res.setClassifier(value);
						break;
					}
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error parsing Artifact", e);
		}
		return isEmpty(res) ? null : res;
	}

	private static boolean isEmpty(Dependency res) {
		return res.getGroupId() == null && res.getArtifactId() == null && res.getVersion() == null
				&& res.getScope() == null && res.getClassifier() == null;
	}
}
