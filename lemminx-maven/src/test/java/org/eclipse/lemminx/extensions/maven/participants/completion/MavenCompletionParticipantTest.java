/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants.completion;

import static org.eclipse.lemminx.XMLAssert.c;
import static org.eclipse.lemminx.XMLAssert.te;
import static org.eclipse.lemminx.XMLAssert.testCompletionFor;

import org.eclipse.lemminx.extensions.maven.NoMavenCentralIndexExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralIndexExtension.class)
public class MavenCompletionParticipantTest {

	@Test
	public void testOneExistingDependencyDependenciesCompletion() throws Exception {
		String pom = //
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
				"<project\n" + //
				"  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" + //
				"                      https://maven.apache.org/xsd/maven-4.0.0.xsd\"\n" + //
				"  xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" + //
				"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" + //
				"  <modelVersion>4.0.0</modelVersion>\n" + //
				"  <artifactId>just-a-pom</artifactId>\n" + //
				"  <groupId>com.datho7561</groupId>\n" + //
				"  <version>0.1.0</version>\n" + //
				"  <dependencies>\n" + //
				"    <dependency>\n" + //
				"      <groupId>com.fasterxml.jackson.core</groupId>\n" + //
				"      <artifactId>jackson-core</artifactId>\n" + //
				"      <version>2.11.3</version>\n" + //
				"    </dependency>\n" + //
				"    maven-core|\n" + //
				"  </dependencies>\n" + //
				"</project>\n";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-core - org.apache.maven:maven-core:3.0", //
				te(16, 4, 16, 14, //
						"<dependency>\n" + //
						"      <groupId>org.apache.maven</groupId>\n" + //
						"      <artifactId>maven-core</artifactId>\n" + //
						"      <version>3.0</version>\n" + //
						"    </dependency>"),
				"maven-core"));
	}

	@Test
	public void testNoExistingDependencyDependenciesCompletion() throws Exception {
		String pom = //
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
				"<project\n" + //
				"  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" + //
				"                      https://maven.apache.org/xsd/maven-4.0.0.xsd\"\n" + //
				"  xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" + //
				"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" + //
				"  <modelVersion>4.0.0</modelVersion>\n" + //
				"  <artifactId>just-a-pom</artifactId>\n" + //
				"  <groupId>com.datho7561</groupId>\n" + //
				"  <version>0.1.0</version>\n" + //
				"  <dependencies>\n" + //
				"    maven-core|\n" + //
				"  </dependencies>\n" + //
				"</project>\n";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-core - org.apache.maven:maven-core:3.0", //
				te(11, 4, 11, 14, //
						"<dependency>\n" + //
						"      <groupId>org.apache.maven</groupId>\n" + //
						"      <artifactId>maven-core</artifactId>\n" + //
						"      <version>3.0</version>\n" + //
						"    </dependency>"),
				"maven-core"));
	}

	@Test
	public void testNoExistingDependencyDependencyCompletion() throws Exception {
		String pom = //
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
				"<project\n" + //
				"  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" + //
				"                      https://maven.apache.org/xsd/maven-4.0.0.xsd\"\n" + //
				"  xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" + //
				"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" + //
				"  <modelVersion>4.0.0</modelVersion>\n" + //
				"  <artifactId>just-a-pom</artifactId>\n" + //
				"  <groupId>com.datho7561</groupId>\n" + //
				"  <version>0.1.0</version>\n" + //
				"  <dependencies>\n" + //
				"    <dependency>\n" + //
				"      maven-core|\n" + //
				"    </dependency>\n" + //
				"  </dependencies>\n" + //
				"</project>\n";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-core - org.apache.maven:maven-core:3.0", //
				te(12, 6, 12, 16, //
						"<groupId>org.apache.maven</groupId>\n" + //
						"      <artifactId>maven-core</artifactId>\n" + //
										"      <version>3.0</version>"),
						"maven-core"));
	}

}