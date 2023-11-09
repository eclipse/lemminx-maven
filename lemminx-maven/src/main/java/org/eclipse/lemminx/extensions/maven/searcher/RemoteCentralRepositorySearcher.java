/*******************************************************************************
 * Copyright (c) 2022, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher;

import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.ARTIFACT_ID;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.DOCS;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.GROUP_ID;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.LATEST_VERSION;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.NUM_FOUND;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.RESPONSE;
import static org.eclipse.lemminx.extensions.maven.searcher.JsonRemoteCentralRepositoryConstants.VERSION;
import static org.eclipse.lemminx.utils.ExceptionUtils.getRootCause;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.lemminx.utils.platform.Platform;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class RemoteCentralRepositorySearcher {
	private static final Logger LOGGER = Logger.getLogger(RemoteCentralRepositorySearcher.class.getName());

	private static final String PACKAGING_TYPE_JAR = "jar";
	private static final String PACKAGING_TYPE_MAVEN_PLUGIN = "maven-plugin";

	public static final String SEARCH_URI = "https://search.maven.org/solrsearch/select?";
	public static final String SEARCH_PARAMS = "wt=json&q=";

	public static final RemoteRepository CENTRAL_REPO = new RemoteRepository.Builder("central", "default",
			"https://repo.maven.apache.org/maven2").build();

	public static boolean disableCentralSearch = Boolean.parseBoolean(
			System.getProperty(RemoteCentralRepositorySearcher.class.getName() + ".disableCentralSearch"));

	private final static long DEFAULT_CACHE_EXPIRATION_TIMEOUT = 30L; // Minutes

	private final HttpClient client;

	private final ExecutorService executorService;

	private final Cache<RequestKey, CompletableFuture<?>> cache;

	private final CacheManager<RequestKey, Collection<Artifact>> artifactsCache;

	private final CacheManager<RequestKey, Collection<String>> groupIdsCache;

	private final CacheManager<RequestKey, Collection<ArtifactVersion>> artifactVersionsCache;

	private enum RequestKind {
		KIND_GET_GROUP_IDS("Get Group IDs"), //
		KIND_GET_ARTIFACTS("Get Artifacts"), //
		KIND_GET_ARTIFACT_VERSIONS("Get Artifact Versions"); //

		String kind;

		private RequestKind(String kind) {
			this.kind = kind;
		}

		public String getKindName() {
			return kind;
		}
	}

	private static class RequestKey {
		String url;
		RequestKind kind;
		Dependency artifact;
		String packaging;

		public RequestKey(String url, RequestKind kind, Dependency artifact, String packaging) {
			this.url = url;
			this.kind = kind;
			this.artifact = artifact;
			this.packaging = packaging;
		}

		@Override
		public boolean equals(Object obj) {
			if (super.equals(obj)) {
				return true;
			}
			if (obj instanceof RequestKey rdObj) {
				return this.hashCode() == rdObj.hashCode();
			}
			return false;
		}

		@Override
		public int hashCode() {
			String artifactString = artifact.toString();
			return Objects.hash(url, kind, packaging, artifactString);
		}
	}

	public enum OngoingOperationError {
		REMOTE_SEARCH_DISABLED("The Maven Search ''{0}'' operation is disabled."), //
		REMOTE_SEARCH_OPERATION_IN_PROGRESS(
				"The Maven Search ''{0}'' operation is in progress for artifact: ''{1}, packaging: ''{2}''."); //

		private final String rawMessage;

		private OngoingOperationError(String rawMessage) {
			this.rawMessage = rawMessage;
		}

		public String getMessage(Object... arguments) {
			return MessageFormat.format(rawMessage, arguments);
		}
	}

	public static class OngoingOperationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		@SuppressWarnings("rawtypes")
		private final CompletableFuture future;
		private final OngoingOperationError errorCode;

		@SuppressWarnings("rawtypes")
		public OngoingOperationException(RequestKey requestDescriptor, OngoingOperationError errorCode,
				CompletableFuture future, Throwable e) {
			super(errorCode.getMessage(requestDescriptor.kind.getKindName(), requestDescriptor.artifact,
					requestDescriptor.packaging), e);
			this.errorCode = errorCode;
			this.future = future;
		}

		public OngoingOperationError getErrorCode() {
			return errorCode;
		}

		@SuppressWarnings("rawtypes")
		public CompletableFuture getFuture() {
			return future;
		}
	}

	private class CacheManager<RequeatKey, V> {

		private final Cache<RequestKey, CompletableFuture<?>> cache;

		public CacheManager(Cache<RequestKey, CompletableFuture<?>> cache) {
			this.cache = cache;
		}

		V getAssync(RequestKey key, Callable<? extends V> loader) {
			// If value is already cached - just return it
			@SuppressWarnings("unchecked")
			CompletableFuture<V> cachedValue = (CompletableFuture<V>) cache.getIfPresent(key);
			if (cachedValue == null) {
				synchronized (cache) {
					cachedValue = callLoader(key, loader);
					cache.put(key, cachedValue);
				}
			}
			if (cachedValue.isCompletedExceptionally()) {
				// There were an error while receiving results from
				// Maven Search API, to avoid trying to repeat the
				// request on each key stroke - just exiting here.
				// (TODO: Do we need to throw the last cached exception
				// again?)
				return null;
			}
			if (!cachedValue.isDone()) {
				// If a Maven Search API request is ongoing - just exiting here
				// to avoid trying to repeat the request on each key stroke.
				// (TODO: Do we need to throw the last cached exception again
				// providing a way to receive the ongoing search future?)
				throw new OngoingOperationException(key, OngoingOperationError.REMOTE_SEARCH_OPERATION_IN_PROGRESS,
						cachedValue, null);
			}
			return cachedValue.getNow(null);
		}

		private CompletableFuture<V> callLoader(final RequestKey key, final Callable<? extends V> loader) {
			return CompletableFuture.supplyAsync(() -> {
				try {
					V result = loader.call();
					return result;
				} catch (Exception e) {
					Throwable rootCause = getRootCause(e);
					String error = "[" + rootCause.getClass().getTypeName() + "] " + rootCause.getMessage();
					LOGGER.log(Level.SEVERE, "Error while requesting data : " + error, rootCause);
					return null;
				}
			}, executorService);
		}
	}

	public RemoteCentralRepositorySearcher() {
		this.client = newHttpClient();
		this.executorService = Executors.newFixedThreadPool(3);
		this.cache = CacheBuilder.newBuilder() //
				.expireAfterWrite(DEFAULT_CACHE_EXPIRATION_TIMEOUT, TimeUnit.MINUTES)//
				.build();
		this.artifactsCache = new CacheManager<RemoteCentralRepositorySearcher.RequestKey, Collection<Artifact>>(cache);
		this.groupIdsCache = new CacheManager<RemoteCentralRepositorySearcher.RequestKey, Collection<String>>(cache);
		this.artifactVersionsCache = new CacheManager<RemoteCentralRepositorySearcher.RequestKey, Collection<ArtifactVersion>>(
				cache);
	}

	private CloseableHttpClient newHttpClient() {
		// Proxy
		String proxyHost = System.getProperty("http.proxyHost");
		Integer proxyPort = null;
		if (proxyHost != null) {
			proxyPort = Integer.getInteger(System.getProperty("http.proxyPort"));
		} else {
			proxyHost = System.getProperty("https.proxyHost");
			if (proxyHost != null) {
				proxyPort = Integer.getInteger(System.getProperty("https.proxyPort"));
			}
		}

		// ex: LemMinX/0.27.1-SNAPSHOT (Windows 11 10.0)
		String userAgent = "LemMinX/" + Platform.getVersion().getVersionNumber() + " (" + Platform.getOS().getName()
				+ " " + Platform.getOS().getVersion() + ")";
		
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT)
				.setConnectTimeout(5*ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT)
				.build();

		HttpClientBuilder builder = HttpClientBuilder.create() //
	            .useSystemProperties() //
	            .disableConnectionState() //
	            .setUserAgent(userAgent) //
	            .setDefaultHeaders(Collections.emptySet()) //
	            .setDefaultRequestConfig(requestConfig); //

		if (proxyHost != null && proxyPort != null) {
			builder = builder.setProxy(
					new HttpHost(proxyHost, proxyPort, null));
		}

		return builder.build();
	}

	public Collection<Artifact> getArtifacts(Dependency artifactToSearch) throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetArtifacts(artifactToSearch, PACKAGING_TYPE_JAR);
	}

	public Collection<ArtifactVersion> getArtifactVersions(Dependency artifactToSearch)
			throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_JAR);
	}

	public Collection<String> getGroupIds(Dependency artifactToSearch) throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_JAR);
	}

	public Collection<Artifact> getPluginArtifacts(Dependency artifactToSearch) throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetArtifacts(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}

	public Collection<ArtifactVersion> getPluginArtifactVersions(Dependency artifactToSearch)
			throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetArtifactVersions(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}

	public Collection<String> getPluginGroupIds(Dependency artifactToSearch) throws OngoingOperationException {
		return disableCentralSearch ? Collections.emptySet()
				: internalGetGroupIds(artifactToSearch, PACKAGING_TYPE_MAVEN_PLUGIN);
	}

	private Collection<Artifact> internalGetArtifacts(Dependency artifactToSearch, String packaging) {
		String url = createArtifactIdsRequesty(artifactToSearch, packaging);
		Collection<Artifact> result = artifactsCache.getAssync(
				new RequestKey(url, RequestKind.KIND_GET_ARTIFACTS, artifactToSearch, packaging),
				new Callable<Collection<Artifact>>() {
					@Override
					public Collection<Artifact> call() throws Exception {
						JsonObject responseBody = getResponseBody(url, artifactToSearch);
						if (responseBody == null || responseBody.get(NUM_FOUND).getAsInt() <= 0) {
							return Collections.emptyList();
						}

						List<Artifact> artifactInfos = new ArrayList<>();
						responseBody.get(DOCS).getAsJsonArray().forEach(d -> {
							artifactInfos.add(toArtifactInfo(d.getAsJsonObject()));
						});

						return artifactInfos;
					}
				});
		return result != null ? result : Collections.emptySet();
	}

	private Collection<ArtifactVersion> internalGetArtifactVersions(Dependency artifactToSearch, String packaging) {
		if (isEmpty(artifactToSearch.getArtifactId())) {
			return Collections.emptySet();
		}

		String url = createArtifactVersionsRequest(artifactToSearch, packaging);
		Collection<ArtifactVersion> result = artifactVersionsCache.getAssync(
				new RequestKey(url, RequestKind.KIND_GET_ARTIFACT_VERSIONS, artifactToSearch, packaging),
				new Callable<Collection<ArtifactVersion>>() {
					@Override
					public Collection<ArtifactVersion> call() throws Exception {
						JsonObject responseBody = getResponseBody(url, artifactToSearch);
						if (responseBody == null || responseBody.get(NUM_FOUND).getAsInt() <= 0) {
							return Collections.emptySet();
						}

						Set<ArtifactVersion> artifactVersions = new HashSet<ArtifactVersion>();
						responseBody.get(DOCS).getAsJsonArray().forEach(d -> {
							artifactVersions
									.add(new DefaultArtifactVersion(d.getAsJsonObject().get(VERSION).getAsString()));
						});

						return artifactVersions;
					}
				});
		return result != null ? result : Collections.emptySet();
	}

	private Collection<String> internalGetGroupIds(Dependency artifactToSearch, String packaging) {
		if (isEmpty(artifactToSearch.getGroupId())) {
			return Collections.emptySet();
		}

		String url = createGroupIdsRequest(artifactToSearch, packaging);
		Collection<String> result = groupIdsCache.getAssync(
				new RequestKey(url, RequestKind.KIND_GET_GROUP_IDS, artifactToSearch, packaging),
				new Callable<Collection<String>>() {
					@Override
					public Collection<String> call() throws Exception {
						JsonObject responseBody = getResponseBody(url, artifactToSearch);
						if (responseBody == null || responseBody.get(NUM_FOUND).getAsInt() <= 0) {
							return Collections.emptySet();
						}

						Collection<String> artifactGroupIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						responseBody.get(DOCS).getAsJsonArray().forEach(d -> {
							artifactGroupIds.add(d.getAsJsonObject().get(GROUP_ID).getAsString());
						});

						return artifactGroupIds;
					}
				});
		return result != null ? result : Collections.emptySet();
	}

	private static String createGroupIdsRequest(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging).append(" AND ").append("g:");
		if (!isEmpty(artifactToSearch.getGroupId())) {
			query.append(artifactToSearch.getGroupId().trim());
		}
		query.append("*");

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=200&").append(SEARCH_PARAMS)
					.append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":",
					artifactToSearch.getGroupId(), artifactToSearch.getArtifactId(), artifactToSearch.getVersion()),
					ex.getMessage());
			return null;
		}
		return url.toString();
	}

	private static String createArtifactIdsRequesty(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging);

		if (!isEmpty(artifactToSearch.getGroupId())) {
			query.append(" AND ").append("g:\"").append(artifactToSearch.getGroupId()).append("\"");
		}
		if (!isEmpty(artifactToSearch.getArtifactId())) {
			query.append(" AND ").append("a:").append(artifactToSearch.getArtifactId()).append("*");
		}

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=100&").append(SEARCH_PARAMS)
					.append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":",
					artifactToSearch.getGroupId(), artifactToSearch.getArtifactId(), artifactToSearch.getVersion()),
					ex.getMessage());
			return null;
		}
		return url.toString();
	}

	private static String createArtifactVersionsRequest(Dependency artifactToSearch, String packaging) {
		StringBuilder query = new StringBuilder();
		query.append("p:").append(packaging).append(" AND ").append("g:").append(artifactToSearch.getGroupId())
				.append(" AND ").append("a:").append(artifactToSearch.getArtifactId());

		if (!isEmpty(artifactToSearch.getVersion())) {
			query.append(" AND v:").append(artifactToSearch.getVersion()).append("*");
		}

		final StringBuilder url = new StringBuilder();
		try {
			url.append(SEARCH_URI).append("rows=100&core=gav&").append(SEARCH_PARAMS)
					.append(URLEncoder.encode(query.toString(), "UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			LOGGER.log(Level.SEVERE, "Maven Central Repo search failed for " + String.join(":",
					artifactToSearch.getGroupId(), artifactToSearch.getArtifactId(), artifactToSearch.getVersion()),
					ex.getMessage());
			return null;
		}
		return url.toString();
	}

	private JsonObject getResponseBody(String url, Dependency artifactToSearch) throws Exception {
		// Response is closable
		HttpUriRequest request = new HttpGet(url);
		try (CloseableHttpResponse response = (CloseableHttpResponse)client.execute(request)) {
			if (isSuccessful(response)) {
				JsonObject bodyObject = JsonParser.parseReader(new InputStreamReader(response.getEntity().getContent()))
						.getAsJsonObject();
				if (bodyObject.has(RESPONSE)) {
					JsonObject responseObject = bodyObject.get(RESPONSE).getAsJsonObject();
					if (responseObject.has(NUM_FOUND) && responseObject.has(DOCS)) {
						return responseObject;
					}
				}
			} else {
				LOGGER.log(Level.SEVERE,
						"Maven Central Repo search failed for " + String.join(":", artifactToSearch.getGroupId(),
								artifactToSearch.getArtifactId(), artifactToSearch.getVersion()),
						response.getStatusLine().getReasonPhrase());
			}
		}
		return null;
	}

	/**
	 * Returns true if the code is in [200..300), which means the request was
	 * successfully received, understood, and accepted.
	 * 
	 * @throws IOException
	 */
	private static boolean isSuccessful(HttpResponse response) {
		try {
			int code = response.getStatusLine().getStatusCode();
			return code >= 200 && code < 300;
		} catch (Exception e) {
			return false;
		}
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

	public void stop() {
		HttpClientUtils.closeQuietly(client);
		executorService.shutdown();
	}
}
