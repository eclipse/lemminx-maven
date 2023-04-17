/*******************************************************************************
 * Copyright (c) 2021-2023 Red Hat Inc. and others.
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

	public static final String PACKAGING_ELT = "packaging";
	public static final String PROJECT_ELT = "project";
	public static final String MODULE_ELT = "module";
	public static final String RELATIVE_PATH_ELT = "relativePath";
	public static final String CONFIGURATION_ELT = "configuration";
	public static final String DEPENDENCIES_ELT = "dependencies";
	public static final String DEPENDENCY_ELT = "dependency";
	public static final String DEPENDENCY_MANAGEMENT_ELT = "dependencyManagement";
	public static final String GROUP_ID_ELT = "groupId";
	public static final String ARTIFACT_ID_ELT = "artifactId";
	public static final String VERSION_ELT = "version";
	public static final String PLUGINS_ELT = "plugins";
	public static final String PLUGIN_ELT = "plugin";
	public static final String PROFILE_ELT = "profile";
	public static final String PROFILES_ELT = "profiles";
	public static final String PARENT_ELT = "parent";
	public static final String SCOPE_ELT = "scope";
	public static final String PHASE_ELT = "phase";
	public static final String GOALS_ELT = "goals";
	public static final String GOAL_ELT = "goal";
	public static final String EXECUTION_ELT = "execution";
	public static final String PROPERTIES_ELT = "properties";
	public static final String TYPE_ELT = "type";
	public static final String CLASSIFIER_ELT = "classifier";
	public static final String EXCLUSION_ELT = "exclusion";
	public static final String EXCLUSIONS_ELT = "exclusions";
	public static final String ID_ELT = "id";
	public static final String BUILD_ELT = "build";
	
	public static final String TARGET_PATH_ELT = "targetPath";
	public static final String DIRECTORY_ELT = "directory";
	public static final String SOURCE_DIRECTORY_ELT = "sourceDirectory";
	public static final String SCRIPT_SOURCE_DIRECTORY_ELT = "scriptSourceDirectory";
	public static final String TEST_SOURCE_DIRECTORY_ELT = "testSourceDirectory";
	public static final String OUTPUT_DIRECTORY_ELT = "outputDirectory";
	public static final String TEST_OUTPUT_DIRECTORY_ELT = "testOutputDirectory";
	
	public static final String FILTERS_ELT = "filters";
	public static final String FILTER_ELT = "filter";
	public static final String SYSTEM_PATH_ELT = "systemPath";
	public static final String FILE_ELT = "file";
	public static final String EXISTS_ELT = "exists";
	public static final String MISSING_ELT = "missing";
	
	// Packaging values
	public static final String PACKAGING_TYPE_JAR ="jar";
	public static final String PACKAGING_TYPE_WAR = "war";
	public static final String PACKAGING_TYPE_EJB = "ejb"; 
	public static final String PACKAGING_TYPE_EAR = "ear";
	public static final String PACKAGING_TYPE_POM = "pom";
	public static final String PACKAGING_TYPE_MAVEN_PLUGIN = "maven-plugin";
}
