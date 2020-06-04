/*******************************************************************************
 * Copyright (c) 2015 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Anton Tanasenko - initial API and implementation
 *      Andrew Obuchowicz - copy and modification from m2e
 *******************************************************************************/
package org.eclipse.lemminx.maven;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

/**
 * Mirrors logic implemented in default maven mojo configurator with regards to
 * discovering how a PlexusConfiguration can be applied to an arbitrary object
 * tree. This class was copied from m2e's
 * org.eclipse.m2e.editor.mojo.PlexusConfigHelper
 * 
 * @see org.codehaus.plexus.component.configurator.BasicComponentConfigurator
 * @see org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup
 * @see org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter
 */
public class PlexusConfigHelper {
	// TODO: Rename this class maybe?

	// TODO: Maybe using a static variable is bug prone? Although this variable is
	// the only "state" held by this class
	private static Map<Class<?>, List<MojoParameter>> processedClasses = new HashMap<>();

	public static Map<String, Type> getClassProperties(Class<?> clazz) {
		Map<String, Type> props = new HashMap<>();
		for (Method m : clazz.getMethods()) {
			if ((m.getModifiers() & Modifier.STATIC) != 0) {
				continue;
			}

			String name = m.getName();
			if ((name.startsWith("add") || name.startsWith("set")) && m.getParameterTypes().length == 1) { //$NON-NLS-1$ //$NON-NLS-2$
				String prop = name.substring(3);
				if (!prop.isEmpty()) {
					prop = Character.toLowerCase(prop.charAt(0)) + prop.substring(1);
					if (!props.containsKey(prop)) {
						props.put(prop, m.getGenericParameterTypes()[0]);
					}
				}
			}
		}

		Class<?> pClazz = clazz;
		while (pClazz != null && !pClazz.equals(Object.class)) {
			for (Field f : pClazz.getDeclaredFields()) {
				if ((f.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) != 0) {
					continue;
				}

				String prop = f.getName();
				if (!props.containsKey(prop)) {
					props.put(prop, f.getGenericType());
				}
			}
			pClazz = pClazz.getSuperclass();
		}
		return props;
	}

	public static Class<?> getRawType(Type type) {
		if (type instanceof Class) {
			return (Class<?>) type;
		}
		if (type instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) type).getRawType();
		}
		return null;
	}

	public static boolean isInline(Class<?> paramClass) {
		return INLINE_TYPES.contains(paramClass.getName()) || paramClass.isEnum();
	}

	public static Type getItemType(Type paramType) {
		Class<?> paramClass = getRawType(paramType);
		if (paramClass != null && paramClass.isArray()) {
			return paramClass.getComponentType();
		}
		if (!Collection.class.isAssignableFrom(paramClass)) {
			return null;
		}

		if (paramType instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramType;

			Type[] args = pt.getActualTypeArguments();
			if (args.length > 0) {
				return args[0];
			}
		}
		return null;
	}

	public static MojoParameter configure(MojoParameter p, boolean required, String expression, String description,
			String defaultValue) {
		p.setRequired(required);
		p.setExpression(expression);
		p.setDescription(description);
		p.setDefaultValue(defaultValue);
		return p;
	}

	public static String getTypeDisplayName(Type type) {
		Class<?> clazz = getRawType(type);

		if (clazz == null) {
			return type.toString();
		}

		if (clazz.isArray()) {
			return getTypeDisplayName(clazz.getComponentType()) + "[]"; //$NON-NLS-1$
		}

		if (type instanceof ParameterizedType) {
			ParameterizedType ptype = (ParameterizedType) type;
			StringBuilder sb = new StringBuilder();
			sb.append(getTypeDisplayName(clazz)).append("&lt;"); //$NON-NLS-1$

			boolean first = true;
			for (Type arg : ptype.getActualTypeArguments()) {
				if (first)
					first = false;
				else
					sb.append(", "); //$NON-NLS-1$
				sb.append(getTypeDisplayName(arg));
			}

			return sb.append("&gt;").toString(); //$NON-NLS-1$
		}

		String name = clazz.getName();
		int idx = name.lastIndexOf('.');
		if (idx == -1) {
			return name;
		}
		// remove common package names
		String pkg = name.substring(0, idx);
		if (pkg.equals("java.lang") || pkg.equals("java.util") || pkg.equals("java.io")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return clazz.getSimpleName();
		}
		return name;
	}

	public static String toSingularName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return name;
		}
		if (name.endsWith("ies")) {
			return name.substring(0, name.length() - 3) + "y";
		} else if (name.endsWith("ches")) {
			return name.substring(0, name.length() - 2);
		} else if (name.endsWith("xes")) {
			return name.substring(0, name.length() - 2);
		} else if (name.endsWith("s") && (name.length() != 1)) {
			return name.substring(0, name.length() - 1);
		}
		return name;
	}

	public static List<Class<?>> getCandidateClasses(ClassRealm realm, Class<?> enclosingClass, Class<?> paramClass) {
		String name = enclosingClass.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			String pkg = name.substring(0, dot);

			List<Class<?>> candidateClasses = null;

			ClassPath cp;
			try {
				cp = ClassPath.from(realm);
			} catch (IOException e) {
				e.printStackTrace();
				return Collections.singletonList(enclosingClass);
			}

			for (ClassInfo ci : cp.getTopLevelClasses(pkg)) {
				Class<?> clazz;
				try {
					clazz = realm.loadClass(ci.getName());
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					continue;
				}

				if ((clazz.getModifiers() & (Modifier.ABSTRACT)) != 0) {
					continue;
				}

				if (!paramClass.isAssignableFrom(clazz)) {
					continue;
				}

				// skip classes without no-arg constructors
				try {
					clazz.getConstructor();
				} catch (NoSuchMethodException ex) {
					continue;
				}

				if (candidateClasses == null) {
					candidateClasses = new ArrayList<>();
				}
				candidateClasses.add(clazz);
			}

			if (candidateClasses != null) {
				return candidateClasses;
			}
		}
		return Collections.singletonList(paramClass);
	}

	public static List<MojoParameter> loadParameters(ClassRealm realm, Class<?> paramClass) {
		List<MojoParameter> parameters = processedClasses.get(paramClass);
		if (parameters == null) {
			parameters = new ArrayList<>();
			processedClasses.put(paramClass, parameters);
			
			Map<String, Type> properties = getClassProperties(paramClass);
			for (Map.Entry<String, Type> e : properties.entrySet()) {
				addParameter(realm, paramClass, e.getValue(), e.getKey(), null, parameters, false, null, null, null);
			}
		}

		return parameters;
	}

	public static List<MojoParameter> getItemParameters(ClassRealm realm, Class<?> enclosingClass, String name,
			Type paramType) {

		Class<?> paramClass = getRawType(paramType);

		if (paramClass == null || isInline(paramClass)) {
			MojoParameter container = new MojoParameter(toSingularName(name), paramType).multiple();
			return Collections.singletonList(container);
		}

		if (Map.class.isAssignableFrom(paramClass) || Properties.class.isAssignableFrom(paramClass)) {
			MojoParameter container = new MojoParameter(toSingularName(name), paramType).multiple()
					.map();
			return Collections.singletonList(container);
		}

		Type itemType = getItemType(paramType);

		if (itemType != null) {
			List<MojoParameter> nested = getItemParameters(realm, enclosingClass, name, itemType);
			MojoParameter container = new MojoParameter(toSingularName(name), getTypeDisplayName(paramType), nested)
					.multiple();
			return Collections.singletonList(container);
		}

		List<Class<?>> parameterClasses = getCandidateClasses(realm, enclosingClass, paramClass);

		List<MojoParameter> parameters = new ArrayList<>();
		for (Class<?> clazz : parameterClasses) {

			String paramName;
			if (clazz.equals(paramClass)) {
				paramName = toSingularName(name);
			} else {
				paramName = clazz.getSimpleName();
				paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);
			}

			List<MojoParameter> nested = loadParameters(realm, paramClass);
			MojoParameter container = new MojoParameter(paramName, getTypeDisplayName(clazz), nested).multiple();
			parameters.add(container);
		}

		return parameters;
	}

	public static void addParameter(ClassRealm realm, Class<?> enclosingClass, Type paramType, String name,
			String alias, List<MojoParameter> parameters, boolean required, String expression, String description,
			String defaultValue) {

		Class<?> paramClass = getRawType(paramType);
		if (paramClass == null) {
			return;
		}

		// inline
		if (isInline(paramClass)) {
			parameters.add(configure(new MojoParameter(name, paramType), required, expression,
					description, defaultValue));
			if (alias != null) {
				parameters.add(configure(new MojoParameter(alias, paramType), required, expression,
						description, defaultValue));
			}
			return;
		}

		// map
		if (Map.class.isAssignableFrom(paramClass)) {
			// we can't do anything with maps, unfortunately
			parameters.add(configure(new MojoParameter(name, paramType).map(), required, expression,
					description, defaultValue));
			if (alias != null) {
				parameters.add(configure(new MojoParameter(alias, paramType).map(), required,
						expression, description, defaultValue));
			}
			return;
		}

		// properties
		if (Properties.class.isAssignableFrom(paramClass)) {

			MojoParameter nested = new MojoParameter("property", "property",
					Arrays.asList(new MojoParameter("name", "String"), new MojoParameter("value", "String")));

			parameters.add(configure(new MojoParameter(name, paramType, nested), required,
					expression, description, defaultValue));
			if (alias != null) {
				parameters.add(configure(new MojoParameter(alias, paramType, nested), required,
						expression, description, defaultValue));
			}
		}

		// collection/array
		Type itemType = getItemType(paramType);
		if (itemType != null) {
			List<MojoParameter> nested = getItemParameters(realm, enclosingClass, name, itemType);

			parameters.add(configure(new MojoParameter(name, paramType, nested), required,
					expression, description, defaultValue));

			if (alias != null) {
				nested = getItemParameters(realm, enclosingClass, alias, itemType);
				parameters.add(configure(new MojoParameter(alias, getTypeDisplayName(paramType), nested), required,
						expression, description, defaultValue));
			}
			return;
		}

		// pojo
		// skip classes without no-arg constructors
		try {
			paramClass.getConstructor();
		} catch (NoSuchMethodException ex) {
			return;
		}

		List<MojoParameter> nested = loadParameters(realm, paramClass);
		parameters.add(configure(new MojoParameter(name, getTypeDisplayName(paramType), nested), required, expression,
				description, defaultValue));
		if (alias != null) {
			parameters.add(configure(new MojoParameter(alias, getTypeDisplayName(paramType), nested), required,
					expression, description, defaultValue));
		}
	}

	public static List<MojoParameter> loadMojoParameters(PluginDescriptor descriptor, MojoDescriptor mojo,
			MavenSession mavenSession, BuildPluginManager buildPluginManager) {
		Class<?> clazz;
		try {
			clazz = mojo.getImplementationClass();
			if (clazz == null) {
				if (descriptor.getClassRealm() == null) {
					// TODO: Maybe this should occur in MavenPluginUtils.collectPluginConfigurationParameters()?
					descriptor.setIsolatedRealm(true);
					buildPluginManager.getPluginRealm(mavenSession, descriptor);
				}
				clazz = descriptor.getClassRealm().loadClass(mojo.getImplementation());
			}
		} catch (ClassNotFoundException | TypeNotPresentException | PluginResolutionException
				| PluginManagerException ex) {
			ex.printStackTrace();
			return Collections.emptyList();
		}

		List<Parameter> ps = mojo.getParameters();
		Map<String, Type> properties = getClassProperties(clazz);

		List<MojoParameter> parameters = new ArrayList<>();

		if (ps != null) {
			for (Parameter p : ps) {
				if (!p.isEditable()) {
					continue;
				}

				Type type = properties.get(p.getName());
				if (type == null) {
					continue;
				}

				// TODO: This shouldn't use side effects on parameters...
				addParameter(descriptor.getClassRealm(), clazz, type, p.getName(), p.getAlias(), parameters,
						p.isRequired(), p.getExpression(), p.getDescription(), p.getDefaultValue());
			}
		}
		return parameters;
	}

	private static final Set<String> INLINE_TYPES;

	static {
		INLINE_TYPES = ImmutableSet.<String>of(byte.class.getName(), Byte.class.getName(), short.class.getName(),
				Short.class.getName(), int.class.getName(), Integer.class.getName(), long.class.getName(),
				Long.class.getName(), float.class.getName(), Float.class.getName(), double.class.getName(),
				Double.class.getName(), boolean.class.getName(), Boolean.class.getName(), char.class.getName(),
				Character.class.getName(), String.class.getName(), StringBuilder.class.getName(),
				StringBuffer.class.getName(), File.class.getName(), URI.class.getName(), URL.class.getName(),
				Date.class.getName(), "org.codehaus.plexus.configuration.PlexusConfiguration");
	}

}
