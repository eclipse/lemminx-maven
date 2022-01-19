/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestParsingException;
import kong.unirest.json.JSONObject;

public class RemoteCentralRepositorySearcher {
	private static final Logger LOGGER = Logger.getLogger(RemoteCentralRepositorySearcher.class.getName());

	private static final String WT = "wt";
	private static final String QUERY = "q";
	
	private static final String GROUP_ID = "g";
	private static final String ARTIFACT_ID = "a";
	private static final String VERSION = "v";
	private static final String LATEST_VERSION = "latestVersion";
	private static final String PACKAGING = "p";
	private static final String SEPARATOR = ":";
	private static final String PARAM_SEPARATOR = " AND ";
	private static final String ASTERISK = "*";

	private static final String WT_JSON = "json";

	private static final String PACKAGING_TYPE_JAR = "jar";
	private static final String PACKAGING_TYPE_MAVEN_PLUGIN = "maven-plugin";

	public static final String CENTRAL_REPO_SEARCH_URI = "https://search.maven.org/solrsearch/select";
	
	public static final RemoteRepository CENTRAL_REPO = new RemoteRepository.Builder("central", "default",
			"https://repo.maven.apache.org/maven2").build();

	public static boolean disableCentralSearch = Boolean
			.parseBoolean(System.getProperty(RemoteCentralRepositorySearcher.class.getName() + ".disableCentralSearch"));
	
	static {
		Unirest.config()
        .socketTimeout(5000)
        .connectTimeout(10000)
        .concurrency(10, 5)
//        .proxy(new Proxy("https://proxy"))
        .setDefaultHeader("Accept", "application/json")
        .followRedirects(false);
	}
	
	public RemoteCentralRepositorySearcher(MavenLemminxExtension lemminxMavenPlugin) {
	}
	
	public void closeContext() {
	}
	
	/**
	 * @param artifactToSearch a CompletableFuture containing a
	 *                         {@code Map<String artifactId, String artifactDescription>}
	 * @return
	 */
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
		GetRequest request = createDefaultRequest();
		
		Map<String, String> queryPatams = new HashMap<>();
		queryPatams.put(PACKAGING, packaging);
		queryPatams.put(GROUP_ID, artifactToSearch.getGroupId());
		queryPatams.put(ARTIFACT_ID, isEmpty(artifactToSearch.getArtifactId()) ? null : artifactToSearch.getArtifactId().trim()  + ASTERISK);
		request = addQuery(request, QUERY,  queryPatams);
		
		JSONObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.getInt("numFound") <= 0 ) {
			return Collections.emptySet();
		}

		final int[] count = {0};
		List<Artifact> artifactInfos = new ArrayList<>();
		responseBody.getJSONArray("docs").forEach(d -> {
			count[0]++;
			Artifact artifactInfo = toArtifact((JSONObject)d);
			artifactInfos.add(artifactInfo);
		});
				
		return artifactInfos;
	}
	
	private Set<ArtifactVersion> internalGetArtifactVersions(Dependency artifactToSearch, String packaging) {
		if (artifactToSearch.getArtifactId() == null || artifactToSearch.getArtifactId().trim().isEmpty()) {
			return Collections.emptySet();
		}
		
		GetRequest request = createDefaultRequest();

		Map<String, String> queryPatams = new HashMap<>();
		queryPatams.put(PACKAGING, packaging);
		queryPatams.put(GROUP_ID, artifactToSearch.getGroupId());
		queryPatams.put(ARTIFACT_ID, artifactToSearch.getArtifactId());
		if (!isEmpty(artifactToSearch.getVersion())) {
			queryPatams.put(VERSION, artifactToSearch.getVersion().trim()  +ASTERISK);
		}		
		request = addQuery(request, QUERY,  queryPatams);

		JSONObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.getInt("numFound") <= 0 ) {
			return Collections.emptySet();
		}

		Set<ArtifactVersion> artifactVersions = new HashSet<ArtifactVersion>();
		responseBody.getJSONArray("docs").forEach(d -> {
			artifactVersions.add(toArtifactVersion((JSONObject)d));
		});
				
		return artifactVersions;
	}
	
	private Set<String> internalGetGroupIds(Dependency artifactToSearch, String packaging) {
		if (artifactToSearch.getGroupId() == null || artifactToSearch.getGroupId().trim().isEmpty()) {
			return Collections.emptySet();
		}
		
		GetRequest request = createDefaultRequest();

		Map<String, String> queryPatams = new HashMap<>();
		queryPatams.put(PACKAGING, packaging);
		queryPatams.put(GROUP_ID, (isEmpty(artifactToSearch.getGroupId()) ? "" : artifactToSearch.getGroupId().trim() )  +ASTERISK);
		request = addQuery(request, QUERY,  queryPatams);

		JSONObject responseBody = getResponseBody(request, artifactToSearch);
		if (responseBody == null || responseBody.getInt("numFound") <= 0 ) {
			return Collections.emptySet();
		}

		Set<String> artifactGroupIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		responseBody.getJSONArray("docs").forEach(d -> {
			artifactGroupIds.add(toGroupId((JSONObject)d));
		});
				
		return artifactGroupIds;
	}
	
	private static String ROWS = "rows";
	private static String SPELLCHECK = "spellcheck";
	
	private static String FALSE = "false";
	
	private static GetRequest createDefaultRequest() {
		return Unirest.get( CENTRAL_REPO_SEARCH_URI)
		        .queryString(WT, WT_JSON)
		        .queryString(SPELLCHECK, FALSE)
				.queryString(ROWS, "20");
	}
	
	private static GetRequest addQuery(GetRequest request, String query, Map<String, String> parameters) {
		StringBuilder params = new StringBuilder();
		parameters.entrySet().forEach(param -> 
		{
			if (param.getKey() != null &&  !param.getKey().trim().isEmpty() && 
					param.getValue() != null && !param.getValue().trim().isEmpty()) {
				if(params.length() > 0) {
					params.append(PARAM_SEPARATOR);
				}
				params.append(param.getKey()).append(SEPARATOR).append(param.getValue());
			}
		});
		return request.queryString(query, params.toString());
	}

	private JSONObject getResponseBody(GetRequest request, Dependency artifactToSearch) {
		HttpResponse<JsonNode> response = request.asJson();
		if (response.isSuccess() && response.getBody() != null) {
			JSONObject bodyObject = response.getBody().getObject();
			if (bodyObject.has("response")) {
				JSONObject responseBody = (JSONObject)bodyObject.get("response");
				if (responseBody.getInt("numFound") > 0) {
					return responseBody;
				}
			}
		} else {
			if (!response.isSuccess()) {
				Optional<UnirestParsingException> parsingError = response.getParsingError();
				LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
						artifactToSearch.getArtifactId(), artifactToSearch.getVersion()), parsingError.get());
			}
		}

		return null;
	}

	private static final Artifact toArtifact(JSONObject object) {
		String g = object.has(GROUP_ID) ? object.getString(GROUP_ID) : null;
		String a = object.has(ARTIFACT_ID) ? object.getString(ARTIFACT_ID) : null;
		String v = object.has(LATEST_VERSION) ? object.getString(LATEST_VERSION) : null;
		String p = object.has(PACKAGING) ? object.getString(PACKAGING) : null;
		Artifact artifactInfo = new DefaultArtifact(g, a, null, v);
		return artifactInfo;
	}
	
	private static final ArtifactVersion toArtifactVersion(JSONObject object) {
		String v = object.has(LATEST_VERSION) ? object.getString(LATEST_VERSION) : null;
		if (v == null) {
			v = object.has(VERSION) ? object.getString(VERSION) : null;
		}
		return new DefaultArtifactVersion(v);
	}
	
	private static final String toGroupId(JSONObject object) {
		return object.has(GROUP_ID) ? object.getString(GROUP_ID) : null;
	}
	
	private static final boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
}
