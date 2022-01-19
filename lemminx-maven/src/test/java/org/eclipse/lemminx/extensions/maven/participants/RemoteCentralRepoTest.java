/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.participants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.lemminx.extensions.maven.searcher.RemoteCentralRepositorySearcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RemoteCentralRepoTest {
	private static RemoteCentralRepositorySearcher repoSearcher = new RemoteCentralRepositorySearcher(null);
	
	@BeforeAll
	private static void setup() {
		RemoteCentralRepositorySearcher.disableCentralSearch = false;
	}	
	
	@Test
	public void testGetArtifacts() {
		String G = null;
		String A = null;
		String V = null;
		
		Dependency dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		
		System.out.println("\nDep: " + dep.toString());
		Collection<Artifact> artifactInfos = repoSearcher.getArtifacts(dep);
		assertNotNull(artifactInfos);
		assertFalse(artifactInfos.isEmpty());
	
		artifactInfos.forEach(a -> {System.out.println("g: " + a.getGroupId() + ". a: " + a.getArtifactId() + ", v: " + a.getVersion());});
		
		G = "org.fujion.webjars";
		A = "webjar-angular";
		V = "13.1.1-1";
		
		dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		System.out.println("\nDep: " + dep.toString());
		
		artifactInfos = repoSearcher.getArtifacts(dep);
		assertNotNull(artifactInfos);
		assertFalse(artifactInfos.isEmpty());
	
		artifactInfos.forEach(a -> {System.out.println("g: " + a.getGroupId() + ". a: " + a.getArtifactId() + ", v: " + a.getVersion());});
	}
	
	@Test
	public void testGetArtifactVersionss() {

		String G = "org.fujion.webjars";
		String A = "webjar-angular";
		String V = "1";

		Dependency dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		System.out.println("\nDep: " + dep.toString());
		
		Set<ArtifactVersion> artifactVersions = repoSearcher.getArtifactVersions(dep);
		assertNotNull(artifactVersions);
		assertFalse(artifactVersions.isEmpty());
	
		artifactVersions.forEach(v -> {System.out.println("v: " + v.toString());});
		assertTrue(artifactVersions.size() > 1);
	}	
	
	@Test
	public void testGetGroupIdss() {

		String G = "org.fujion";
		
		Dependency dep = new Dependency();
		dep.setGroupId(G);
		System.out.println("\nDep: " + dep.toString());
		
		Set<String> artifactGroups = repoSearcher.getGroupIds(dep);
		assertNotNull(artifactGroups);
		assertFalse(artifactGroups.isEmpty());
	
		artifactGroups.forEach(v -> {System.out.println("g: " + v.toString());});

		G = "org.fuj*";
		
		dep = new Dependency();
		dep.setGroupId(G);
		System.out.println("\nDep: " + dep.toString());
		
		artifactGroups = repoSearcher.getGroupIds(dep);
		assertNotNull(artifactGroups);
		assertFalse(artifactGroups.isEmpty());
	
		artifactGroups.forEach(v -> {System.out.println("g: " + v.toString());});
	}	
		
	@Test
	public void testGetPlugiArtifacts() {

		String G = null;
		String A = null;
		String V = null;
		
		Dependency dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		
		System.out.println("\nDep: " + dep.toString());
		Collection<Artifact> artifactInfos = repoSearcher.getPluginArtifacts(dep);
		assertNotNull(artifactInfos);
		assertFalse(artifactInfos.isEmpty());
	
		artifactInfos.forEach(a -> {System.out.println("g: " + a.getGroupId() + ". a: " + a.getArtifactId() + ", v: " + a.getVersion());});
		
		G = "com.github.gianttreelp.proguardservicesmapper";
		A = "proguard-services-mapper-maven";
		V = "1.0";
		
		dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		System.out.println("\nDep: " + dep.toString());
		
		artifactInfos = repoSearcher.getPluginArtifacts(dep);
		assertNotNull(artifactInfos);
		assertFalse(artifactInfos.isEmpty());
	
		artifactInfos.forEach(a -> {System.out.println("g: " + a.getGroupId() + ". a: " + a.getArtifactId() + ", v: " + a.getVersion());});
	}
	
	@Test
	public void testGetPluginArtifactVersionss() {

		String G = "com.github.gianttreelp.proguardservicesmapper";
		String A = "proguard-services-mapper-maven";
		String V = "1";
		
		Dependency dep = new Dependency();
		dep.setArtifactId(A);
		dep.setGroupId(G);
		dep.setVersion(V);
		
		System.out.println("\nDep: " + dep.toString());
		
		Set<ArtifactVersion> artifactVersions = repoSearcher.getPluginArtifactVersions(dep);
		assertNotNull(artifactVersions);
		assertFalse(artifactVersions.isEmpty());

		artifactVersions.forEach(v -> {System.out.println("v: " + v.toString());});
	}	
	
	@Test
	public void testGetPluginGroupIdss() {

		String G = "com.github.gianttreelp.proguardservicesmapper";
		
		Dependency dep = new Dependency();
		dep.setGroupId(G);
		System.out.println("\nDep: " + dep.toString());
		
		Set<String> artifactGroups = repoSearcher.getPluginGroupIds(dep);
		assertNotNull(artifactGroups);
		assertFalse(artifactGroups.isEmpty());
	
		artifactGroups.forEach(v -> {System.out.println("g: " + v.toString());});

		G = "com.github.giant*";
		
		dep = new Dependency();
		dep.setGroupId(G);
		System.out.println("\nDep: " + dep.toString());
		
		artifactGroups = repoSearcher.getGroupIds(dep);
		assertNotNull(artifactGroups);
		assertFalse(artifactGroups.isEmpty());
	
		artifactGroups.forEach(v -> {System.out.println("g: " + v.toString());});
	}	
		

}
