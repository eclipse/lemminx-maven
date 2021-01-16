/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

/**
 * Maven DOM constants.
 *
 */
public class DOMConstants {

	private DOMConstants() {

	}

	public static final String MODULE_ELT = "module";
	public static final String RELATIVE_PATH_ELT = "relativePath";
	public static final String CONFIGURATION_ELT = "configuration";
	public static final String DEPENDENCIES_ELT = "dependencies";
	public static final String DEPENDENCY_ELT = "dependency";
	public static final String GROUP_ID_ELT = "groupId";
	public static final String ARTIFACT_ID_ELT = "artifactId";
	public static final String VERSION_ELT = "version";
	public static final String PLUGINS_ELT = "plugins";
	public static final String PLUGIN_ELT = "plugin";
	public static final String PARENT_ELT = "parent";
	public static final String SCOPE_ELT = "scope";
	public static final String PHASE_ELT = "phase";
	public static final String GOALS_ELT = "goals";
	public static final String GOAL_ELT = "goal";
	public static final String EXECUTION_ELT = "execution";
	public static final String PROPERTIES_ELT = "properties";
	public static final String TYPE_ELT = "type";
	public static final String CLASSIFIER_ELT = "classifier";
}
