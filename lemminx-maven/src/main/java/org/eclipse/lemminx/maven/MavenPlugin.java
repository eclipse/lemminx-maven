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

import org.apache.maven.Maven;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.repository.RepositorySystem;
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
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.maven.searcher.LocalRepositorySearcher;
import org.eclipse.lemminx.maven.searcher.RemoteRepositoryIndexSearcher;
import org.eclipse.lemminx.services.extensions.ICompletionParticipant;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lsp4j.InitializeParams;

/**
 * Extension for pom.xml.
 *
 */
public class MavenPlugin implements IXMLExtension {

	private static final String MAVEN_XMLLS_EXTENSION_REALM_ID = MavenPlugin.class.getName();

	public static final File LOCAL_REPOSITORY;
	static {
		String property = System.getProperty("maven.repo.local");
		LOCAL_REPOSITORY = property != null && !property.trim().isEmpty() ? new File(property) : RepositorySystem.defaultUserLocalRepository;
	}
	
	private ICompletionParticipant completionParticipant;
	private IDiagnosticsParticipant diagnosticParticipant;
	private IHoverParticipant hoverParticipant;
	private PlexusContainer container;
	private MavenProjectCache cache;

	private RemoteRepositoryIndexSearcher indexSearcher;

	private LocalRepositorySearcher localRepositorySearcher;

	private MavenDefinitionParticipant definitionParticipant;

	public MavenPlugin() {
	}

	@Override
	public void doSave(ISaveContext context) {

	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
		try {
			container = newPlexusContainer();
			cache = new MavenProjectCache(container);
		} catch (PlexusContainerException e) {
			e.printStackTrace();
		}
		localRepositorySearcher = new LocalRepositorySearcher(LOCAL_REPOSITORY);
		indexSearcher = new RemoteRepositoryIndexSearcher(container);
		cache.addProjectParsedListener(indexSearcher::updateKnownRepositories);
		try {
			completionParticipant = new MavenCompletionParticipant(cache, localRepositorySearcher, indexSearcher, container.lookup(MavenPluginManager.class));
		} catch (ComponentLookupException e) {
			e.printStackTrace();
		}
		registry.registerCompletionParticipant(completionParticipant);
		diagnosticParticipant = new MavenDiagnosticParticipant(cache);
		registry.registerDiagnosticsParticipant(diagnosticParticipant);
		try {
			hoverParticipant = new MavenHoverParticipant(cache, indexSearcher, (MavenPluginManager) container.lookup(MavenPluginManager.class));
			registry.registerHoverParticipant(hoverParticipant);
		} catch (ComponentLookupException e) {
			e.printStackTrace();
		}
		definitionParticipant = new MavenDefinitionParticipant(cache, localRepositorySearcher);
		registry.registerDefinitionParticipant(definitionParticipant);
	}

	/* Copied from m2e */
	public static DefaultPlexusContainer newPlexusContainer() throws PlexusContainerException {
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
	}

	public static boolean match(DOMDocument document) {
		String uri = document.getDocumentURI();
		String fileName = uri.substring(uri.lastIndexOf(File.separatorChar) + 1);
		return (fileName.startsWith("pom") && fileName.endsWith(".xml")) || fileName.endsWith(Maven.POMv4);
	}
}
