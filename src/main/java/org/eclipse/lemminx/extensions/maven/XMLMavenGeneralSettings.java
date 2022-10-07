/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import org.eclipse.lemminx.settings.XMLGeneralClientSettings;
import org.eclipse.lemminx.utils.JSONUtility;

public class XMLMavenGeneralSettings extends XMLGeneralClientSettings {

	public XMLMavenGeneralSettings() {
		setMaven(new XMLMavenSettings());
	}

	private XMLMavenSettings maven;

	public XMLMavenSettings getMaven() {
		return maven;
	}

	public void setMaven(XMLMavenSettings maven) {
		this.maven = maven;
	}

	public static XMLMavenGeneralSettings getGeneralXMLSettings(Object initializationOptionsSettings) {
		return JSONUtility.toModel(initializationOptionsSettings, XMLMavenGeneralSettings.class);
	}

}
