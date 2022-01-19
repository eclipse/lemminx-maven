/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.lemminx.extensions.maven.MavenLemminxExtension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteCentralRepositorySearcher {
	private static final Logger LOGGER = Logger.getLogger(RemoteCentralRepositorySearcher.class.getName());

	private static final String GROUP_ID = "g";
	private static final String ARTIFACT_ID = "a";
	private static final String VERSION = "v";
	private static final String LATEST_VERSION = "latestVersion";

	private static final String PACKAGING_TYPE_JAR = "jar";
	private static final String PACKAGING_TYPE_MAVEN_PLUGIN = "maven-plugin";

	public static final String SEARCH_URI = "https://search.maven.org/solrsearch/select?";
	public static final String SEARCH_PARAMS = "wt=json&q=";
	
	public static final RemoteRepository CENTRAL_REPO = new RemoteRepository.Builder("central", "default",
			"https://repo.maven.apache.org/maven2").build();

	public static boolean disableCentralSearch = Boolean
			.parseBoolean(System.getProperty(RemoteCentralRepositorySearcher.class.getName() + ".disableCentralSearch"));

	private OkHttpClient client = new OkHttpClient();

	public RemoteCentralRepositorySearcher(MavenLemminxExtension lemminxMavenPlugin) {
	}
	
	public void closeContext() {
	}
	
	public Collection<Artifact> getArtifacts(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetArtifacts(artifactToSearch, PACKAGING_TYPE_JAR);
	}
				
	public Set<ArtifactVersion> getArtifactVersions(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_JAR);
	}
	
	public Set<String> getGroupIds(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_JAR);
	}

	public Collection<Artifact> getPluginArtifacts(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetArtifacts(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}

	public Set<ArtifactVersion> getPluginArtifactVersions(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}

	public Set<String> getPluginGroupIds(Dependency artifactToSearch) {
		return disableCentralSearch ? Collections.emptySet() : internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}
	
	private Collection<Artifact> internalGetArtifacts(Dependency artifactToSearch, String packaging) {
		Request request = createArtifactIdsRequesty(artifactToSearch, packaging);
		JsonObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.get("numFound").getAsInt() <= 0 ) {
			return Collections.emptyList();
		}

		List<Artifact> artifactInfos = new ArrayList<>();
		responseBody.get("docs").getAsJsonArray().forEach(d -> {
			artifactInfos.add(toArtifactInfo(d.getAsJsonObject()));
		});
		return artifactInfos;
	}
	
	private Set<ArtifactVersion> internalGetArtifactVersions(Dependency artifactToSearch, String packaging) {
		if (isEmpty(artifactToSearch.getArtifactId())) {
			return Collections.emptySet();
		}

		Request request = createArtifactVersionsRequest(artifactToSearch, packaging);
		JsonObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.get("numFound").getAsInt() <= 0 ) {
			return Collections.emptySet();
		}

		Set<ArtifactVersion> artifactVersions = new HashSet<ArtifactVersion>();
		responseBody.get("docs").getAsJsonArray().forEach(d -> {
			artifactVersions.add(new DefaultArtifactVersion(d.getAsJsonObject().get(VERSION).getAsString()));
		});
		return artifactVersions;
	}
	
	private Set<String> internalGetGroupIds(Dependency artifactToSearch, String packaging) {
		if (isEmpty(artifactToSearch.getGroupId())) {
			return Collections.emptySet();
		}
		
		Request request = createGroupIdsRequest(artifactToSearch, packaging);
		JsonObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.get("numFound").getAsInt() <= 0 ) {
			return Collections.emptySet();
		}
		
		Set<String> artifactGroupIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		responseBody.get("docs").getAsJsonArray().forEach(d -> {
			artifactGroupIds.add(d.getAsJsonObject().get(GROUP_ID).getAsString());
		});
		return artifactGroupIds;
	}
	
	private static Request createGroupIdsRequest(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging).append(" AND ").append("g:");
		if (!isEmpty(artifactToSearch.getGroupId())) {
			query.append(artifactToSearch.getGroupId().trim());
		}
		query.append("*");

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=200&")
				.append(SEARCH_PARAMS).append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch(UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
					artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), ex.getMessage());
			return null;
		}
		return new Request.Builder().url(url.toString()).build();
	}
	
	private static Request createArtifactIdsRequesty(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging);

	    if(!isEmpty(artifactToSearch.getGroupId())) {
			query.append(" AND ").append("g:\"").append(artifactToSearch.getGroupId()).append("\"");
	    }
	    if(!isEmpty(artifactToSearch.getArtifactId())) {
    		query.append(" AND ").append("a:").append(artifactToSearch.getArtifactId()).append("*");
	    }

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=100&")
				.append(SEARCH_PARAMS).append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch(UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
					artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), ex.getMessage());
			return null;
		}
		return new Request.Builder().url(url.toString()).build();
	}
	
	private static Request createArtifactVersionsRequest(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging).append(" AND ")
			.append("g:").append(artifactToSearch.getGroupId()).append(" AND ")
			.append("a:").append(artifactToSearch.getArtifactId());

	    if(!isEmpty(artifactToSearch.getVersion())) {
			query.append(" AND v:").append(artifactToSearch.getVersion()).append("*");
	    }

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=100&core=gav&")
				.append(SEARCH_PARAMS).append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch(UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
					artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), ex.getMessage());
			return null;
		}
		return new Request.Builder().url(url.toString()).build();
	}

	private JsonObject getResponseBody(Request request, Dependency artifactToSearch) {
		try {
			Response response = client.newCall(request).execute();
			if (response.isSuccessful()) {
				JsonObject bodyObject = new JsonParser().parse(response.body().charStream()).getAsJsonObject();
				if (bodyObject.has("response")) {
					JsonObject responseObject = bodyObject.get("response").getAsJsonObject();
					if (responseObject.has("numFound") &&
							responseObject.has("docs")) {
						return responseObject;
					}
				}
			} else {
				LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
						artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), response.message());
			}
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
					artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), ex);
		}
		return null;
	}

	private static final Artifact toArtifactInfo(JsonObject object) {
		String g = object.has(GROUP_ID) ? object.get(GROUP_ID).getAsString() : null;
		String a = object.has(ARTIFACT_ID) ? object.get(ARTIFACT_ID).getAsString() : null;
		String v = object.has(LATEST_VERSION) ? object.get(LATEST_VERSION).getAsString() : null;
		Artifact artifactInfo = new DefaultArtifact(g, a, null, v);
		return artifactInfo;
	}
	
	private static final boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
}
