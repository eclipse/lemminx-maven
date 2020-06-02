/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.io.File;
import java.net.URI;

import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.SettingsReader;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.services.extensions.ICompletionParticipant;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.services.extensions.save.ISaveContext.SaveContextType;
import org.eclipse.lsp4j.InitializeParams;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Extension for pom.xml.
 *
 */
public class MavenPlugin implements IXMLExtension {

	private static final String MAVEN_XMLLS_EXTENSION_REALM_ID = MavenPlugin.class.getName();

	private ICompletionParticipant completionParticipant;
	private IDiagnosticsParticipant diagnosticParticipant;
	private IHoverParticipant hoverParticipant;
	private MavenDefinitionParticipant definitionParticipant;

	private MavenProjectCache cache;
	private RemoteRepositoryIndexSearcher indexSearcher;
	private LocalRepositorySearcher localRepositorySearcher;

	private PlexusContainer container;
	private MavenExecutionRequest mavenRequest;
	private RepositorySystemSession repositorySystemSession;
	private MavenPluginManager mavenPluginManager;

	private XMLExtensionsRegistry currentRegistry;

	private InitializeParams params;

	private MavenSession mavenSession;

	private BuildPluginManager buildPluginManager;

	public MavenPlugin() {
	}

	@Override
	public void doSave(ISaveContext context) {
		if (context.getType() == SaveContextType.SETTINGS) {
			final XMLExtensionsRegistry registry = this.currentRegistry; // keep ref as this.currentRegistry becomes null on stop
			stop(registry);
			params.setInitializationOptions(context.getSettings());
			start(params, registry);
		}
	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
		this.params = params;
		this.currentRegistry = registry;
		initialize(params != null ? params.getInitializationOptions() : null);
		completionParticipant = new MavenCompletionParticipant(cache, localRepositorySearcher, indexSearcher, repositorySystemSession, mavenSession, mavenPluginManager, buildPluginManager);
		registry.registerCompletionParticipant(completionParticipant);
		diagnosticParticipant = new MavenDiagnosticParticipant(cache, mavenPluginManager, repositorySystemSession, mavenSession, buildPluginManager);
		registry.registerDiagnosticsParticipant(diagnosticParticipant);
		hoverParticipant = new MavenHoverParticipant(cache, localRepositorySearcher, indexSearcher, repositorySystemSession, mavenSession, mavenPluginManager, buildPluginManager);
		registry.registerHoverParticipant(hoverParticipant);
		definitionParticipant = new MavenDefinitionParticipant(cache, localRepositorySearcher);
		registry.registerDefinitionParticipant(definitionParticipant);
	}

	public void initialize(Object initializationOptions) {
		try {
			container = newPlexusContainer();
			mavenRequest = initMavenRequest(container, initializationOptions);
			DefaultRepositorySystemSessionFactory repositorySessionFactory = container.lookup(DefaultRepositorySystemSessionFactory.class);
			repositorySystemSession = repositorySessionFactory.newRepositorySession(mavenRequest);
			MavenExecutionResult mavenResult = new DefaultMavenExecutionResult();
			// TODO: MavenSession is deprecated. Investigate for alternative
			mavenSession = new MavenSession(container, repositorySystemSession, mavenRequest, mavenResult);
		} catch (Exception e) {
			e.printStackTrace();
		}
		cache = new MavenProjectCache(container, mavenRequest);
		localRepositorySearcher = new LocalRepositorySearcher(repositorySystemSession.getLocalRepository().getBasedir());
		indexSearcher = new RemoteRepositoryIndexSearcher(container);
		cache.addProjectParsedListener(indexSearcher::updateKnownRepositories);
		mavenPluginManager = null;
		buildPluginManager = null;
		try {
			mavenPluginManager = container.lookup(MavenPluginManager.class);
			buildPluginManager = container.lookup(BuildPluginManager.class);
		} catch (ComponentLookupException e) {
			e.printStackTrace();
		}
	}

	private MavenExecutionRequest initMavenRequest(PlexusContainer container, Object initializationOptions) throws Exception {
		JsonObject options = initializationOptions != null ? (JsonObject)initializationOptions : new JsonObject();
		if (options.has("xml")) {
			options = options.getAsJsonObject("xml");
		}
		MavenExecutionRequest mavenRequest = new DefaultMavenExecutionRequest();
		SettingsReader reader = container.lookup(SettingsReader.class);
		MavenExecutionRequestPopulator requestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
		{
			final File globalSettingsFile = getFileFromOptons(options.get("maven.globalSettings"), SettingsXmlConfigurationProcessor.DEFAULT_GLOBAL_SETTINGS_FILE);
			if (globalSettingsFile.canRead()) {
				mavenRequest.setGlobalSettingsFile(globalSettingsFile);
				Settings globalSettings = reader.read(globalSettingsFile, null);
				requestPopulator.populateFromSettings(mavenRequest, globalSettings);
			}
		}
		{
			final File localSettingsFile = getFileFromOptons(options.get("maven.userSettings"), SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE);
			if (localSettingsFile.canRead()) {
				mavenRequest.setUserSettingsFile(localSettingsFile);
				Settings userSettings = reader.read(localSettingsFile, null);
				requestPopulator.populateFromSettings(mavenRequest, userSettings);
			}
		}
		mavenRequest.setLocalRepositoryPath(RepositorySystem.defaultUserLocalRepository);
		String localRepoProperty = System.getProperty("maven.repo.local");
		if (localRepoProperty != null) {
			mavenRequest.setLocalRepositoryPath(new File(localRepoProperty));
		}
		JsonElement localRepoElement = options.get("maven.repo.local");
		if (localRepoElement != null && localRepoElement.isJsonPrimitive()) {
			String localRepoOption = localRepoElement.getAsString();
			if (localRepoOption != null && !localRepoOption.trim().isEmpty()) {
				File candidate = new File(localRepoOption);
				if (candidate.isFile() && candidate.canRead()) {
					mavenRequest.setLocalRepositoryPath(candidate);
				}
			}
		}

		RepositorySystem repositorySystem = container.lookup(RepositorySystem.class);
		ArtifactRepository localRepo = repositorySystem.createLocalRepository(mavenRequest.getLocalRepositoryPath());
		mavenRequest.setLocalRepository(localRepo);
		return mavenRequest;
	}

	private File getFileFromOptons(JsonElement element, File defaults) {
		if (element == null || !element.isJsonPrimitive()) {
			return defaults;
		}
		String stringValue = element.getAsString();
		if (stringValue != null && !stringValue.trim().isEmpty()) {
			File globalSettingsCandidate = new File(stringValue);
			if (globalSettingsCandidate.canRead()) {
				return globalSettingsCandidate;
			}
		}
		return defaults;
	}

	/* Copied from m2e */
	private DefaultPlexusContainer newPlexusContainer() throws PlexusContainerException {
		final ClassWorld classWorld = new ClassWorld(MAVEN_XMLLS_EXTENSION_REALM_ID, ClassWorld.class.getClassLoader());
		final ClassRealm realm;
		try {
			realm = classWorld.getRealm(MAVEN_XMLLS_EXTENSION_REALM_ID);
		} catch (NoSuchRealmException e) {
			throw new PlexusContainerException("Could not lookup required class realm", e);
		}
		final ContainerConfiguration mavenCoreCC = new DefaultContainerConfiguration() //
				.setClassWorld(classWorld) //
				.setRealm(realm) //
				.setClassPathScanning(PlexusConstants.SCANNING_INDEX) //
				.setAutoWiring(true) //
				.setName("mavenCore"); //$NON-NLS-1$

		// final Module logginModule = new AbstractModule() {
		// protected void configure() {
		// bind(ILoggerFactory.class).toInstance(LoggerFactory.getILoggerFactory());
		// }
		// };
		// final Module coreExportsModule = new AbstractModule() {
		// protected void configure() {
		// ClassRealm realm = mavenCoreCC.getRealm();
		// CoreExtensionEntry entry = CoreExtensionEntry.discoverFrom(realm);
		// CoreExports exports = new CoreExports(entry);
		// bind(CoreExports.class).toInstance(exports);
		// }
		// };
		return new DefaultPlexusContainer(mavenCoreCC);
	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
		registry.unregisterCompletionParticipant(completionParticipant);
		registry.unregisterDiagnosticsParticipant(diagnosticParticipant);
		registry.unregisterHoverParticipant(hoverParticipant);
		registry.unregisterDefinitionParticipant(definitionParticipant);
		localRepositorySearcher.stop();
		indexSearcher.closeContext();
		indexSearcher = null;
		cache = null;
		container.dispose();
		container = null;
		this.currentRegistry = null;
	}

	public static boolean match(DOMDocument document) {
		File file = new File(URI.create(document.getDocumentURI()));
		return (file.getName().startsWith("pom") && file.getName().endsWith(".xml"))
				|| file.getName().endsWith(Maven.POMv4);
	}

	public MavenProjectCache getProjectCache() {
		return this.cache;
	}
}
