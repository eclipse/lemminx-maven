/*******************************************************************************
 * Copyright (c) 2021-2022 Red Hat Inc. and others.
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

import java.util.Arrays;

import org.eclipse.lemminx.extensions.maven.NoMavenCentralExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NoMavenCentralExtension.class)
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

	@Test
	public void testNotPrefixedPropertyCompletion() throws Exception {
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
				"  <properties>\n" + //
				"    <spring.version>5.0.2</spring.version>\n" + //
				"  </properties>\n" + //
				"  <dependencies>\n" + //
				"    <dependency>\n" + //
				"      <groupId>org.springframework</groupId>\n" + //
				"      <artifactId>spring-context</artifactId>\n" + //
				"      <version>spring.ver|</version>\n" + //
				"      <scope>runtime</scope>\n" + //
				"    </dependency>\n" + //
				"  </dependencies>\n" + //
				"</project>\n";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("${spring.version}", //
				te(17, 25, 17, 25, //
						"${spring.version}"),
						"${spring.version}"));
	}

	@Test
	public void testInsertionWithAlreadyExistingGroupId() throws Exception {
		String pom = //
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "	xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n"
				+ "	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				+ "	<modelVersion>4.0.0</modelVersion>\n"
				+ "	<groupId>org.test</groupId>\n"
				+ "	<artifactId>test</artifactId>\n"
				+ "	<version>0.0.1-SNAPSHOT</version>\n"
				+ "	<dependencies>\n"
				+ "		<dependency>\n"
				+ "			<groupId>org.apache.maven.plugins</groupId>\n"
				+ "			|\n"
				+ "		</dependency>\n"
				+ "	</dependencies>\n"
				+ "</project>";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-surefire-plugin - org.apache.maven.plugins:maven-surefire-plugin:2.22.2", //
				te(10, 3, 10, 3, //
						"<artifactId>maven-surefire-plugin</artifactId>\n			<version>2.22.2</version>"),
						"maven-surefire-plugin"));
	}

	@Test
	public void testInsertionDependencyWithPartiallyTypedInArtifactId() throws Exception {
		String pom = //
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				+ "  <modelVersion>4.0.0</modelVersion>\n"
				+ "  <groupId>org.test</groupId>\n"
				+ "  <artifactId>test</artifactId>\n"
				+ "  <version>0.0.1-SNAPSHOT</version>\n"
				+ "  <dependencies>\n"
				+ "    <dependency>\n"
				+ "      <artifactId>maven-surefire-plu|</artifactId>\n"
				+ "    </dependency>\n"
				+ "  </dependencies>\n"
				+ "</project>";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-surefire-plugin - org.apache.maven.plugins:maven-surefire-plugin:2.22.2", //
					te(9, 18, 9, 36, //
							"maven-surefire-plugin"),
					Arrays.asList(
							te(8, 16, 8, 16, 
								"\n      <groupId>org.apache.maven.plugins</groupId>"),
							te(9, 49, 9, 49, 
								"\n      <version>2.22.2</version>")),
					"maven-surefire-plugin"));
	}

	@Test
	public void testInsertionDependencyWithPartiallyTypedInArtifactIdNotWellFormed() throws Exception {
		String pom = //
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				+ "  <modelVersion>4.0.0</modelVersion>\n"
				+ "  <groupId>org.test</groupId>\n"
				+ "  <artifactId>test</artifactId>\n"
				+ "  <version>0.0.1-SNAPSHOT</version>\n"
				+ "  <dependencies>\n"
				+ "    <dependency>\n"
				+ "      <artifactId>maven-surefire-plu|</artifactId>\n";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
			c("maven-surefire-plugin - org.apache.maven.plugins:maven-surefire-plugin:2.22.2", //
					te(9, 18, 9, 36, //
							"maven-surefire-plugin"),
					Arrays.asList(
							te(8, 16, 8, 16, 
								"\n      <groupId>org.apache.maven.plugins</groupId>"),
							te(9, 49, 9, 49, 
								"\n      <version>2.22.2</version>")),
					"maven-surefire-plugin"));
	}

	@Test
	public void testInsertionDependencyWithPartiallyTypedInArtifactIdNotWellFormedNoEOL() throws Exception {
		String pom = //
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				+ "  <modelVersion>4.0.0</modelVersion>\n"
				+ "  <groupId>org.test</groupId>\n"
				+ "  <artifactId>test</artifactId>\n"
				+ "  <version>0.0.1-SNAPSHOT</version>\n"
				+ "  <dependencies>\n"
				+ "    <dependency>\n"
				+ "      <artifactId>maven-surefire-plu|</artifactId>";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-surefire-plugin - org.apache.maven.plugins:maven-surefire-plugin:2.22.2", //
					te(9, 18, 9, 36, //
						"maven-surefire-plugin"),
					Arrays.asList(
							te(8, 16, 8, 16, 
								"\n      <groupId>org.apache.maven.plugins</groupId>"),
							te(9, 49, 9, 49, 
								"\n      <version>2.22.2</version>")),
					"maven-surefire-plugin"));
	}

	@Test
	public void testInsertionDependencyWithPartiallyTypedInArtifactIdBrokenEnd() throws Exception {
		String pom = //
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				+ "  <modelVersion>4.0.0</modelVersion>\n"
				+ "  <groupId>org.test</groupId>\n"
				+ "  <artifactId>test</artifactId>\n"
				+ "  <version>0.0.1-SNAPSHOT</version>\n"
				+ "  <dependencies>\n"
				+ "    <dependency>\n"
				+ "      <artifactId>maven-surefire-plu|";
		testCompletionFor(pom, null, "file:///pom.xml", null, //
				c("maven-surefire-plugin - org.apache.maven.plugins:maven-surefire-plugin:2.22.2", //
					te(9, 18, 9, 36, //
						"maven-surefire-plugin</artifactId>"),
					Arrays.asList(
							te(8, 16, 8, 16, 
								"\n      <groupId>org.apache.maven.plugins</groupId>"),
							te(9, 36, 9, 36, 
								"\n      <version>2.22.2</version>")),
					"maven-surefire-plugin"));
	}
}