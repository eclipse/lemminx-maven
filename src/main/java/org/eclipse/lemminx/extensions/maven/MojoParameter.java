/*******************************************************************************
 * Copyright (c) 2015, 2021 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Anton Tanasenko. - initial API and implementation
 *      Andrew Obuchowicz - Copy & modification from m2e
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.maven.plugin.descriptor.Parameter;
import org.eclipse.lemminx.extensions.maven.utils.PlexusConfigHelper;

// TODO: Make Maven bug about moving this upstream
/**
 * This Mojo Parameter class differs from the {@link Parameter} class as it supports structured
 * elements.
 */
public class MojoParameter {

	public final String name;
	public final String alias;
	public final String type;

	private boolean required;
	private String description;
	private String expression;
	private String defaultValue;
	private List<MojoParameter> nested;
	private boolean multiple;
	private boolean map;
	private Type paramType;
	private String deprecated;
	public MojoParameter(String name, String alias, String type, List<MojoParameter> parameters) {
		this.name = name;
		this.type = type;
		nested = parameters;
		this.alias = alias;
	}
	public MojoParameter(String name, String alias, Type paramType, List<MojoParameter> parameters) {
		this(name, alias, PlexusConfigHelper.getTypeDisplayName(paramType), parameters);
		this.setParamType(paramType);
	}

	public MojoParameter(String name, String alias, Type paramType, MojoParameter parameter) {
		this(name, alias, PlexusConfigHelper.getTypeDisplayName(paramType), Collections.singletonList(parameter));
		this.setParamType(paramType);
	}

	public MojoParameter(String name, String alias, String type) {
		this(name, alias, type, Collections.<MojoParameter>emptyList());
	}

	public MojoParameter(String name, String alias, Type paramType) {
		this(name, alias, PlexusConfigHelper.getTypeDisplayName(paramType));
		this.setParamType(paramType);
	}

	public MojoParameter(Parameter parameter, Type paramType) {
		this(parameter.getName(), parameter.getAlias(), paramType);
		this.defaultValue = parameter.getDefaultValue();
		this.description = parameter.getDescription();
		this.deprecated = parameter.getDeprecated();
		this.expression = parameter.getExpression();
	}

	public MojoParameter multiple() {
		this.multiple = true;
		return this;
	}

	public MojoParameter map() {
		this.map = true;
		return this;
	}

	public boolean isMultiple() {
		return multiple;
	}

	public boolean isMap() {
		return this.map;
	}

	public List<MojoParameter> getNestedParameters() {
		return nested == null ? Collections.<MojoParameter>emptyList() : Collections.unmodifiableList(nested);
	}
	
	public List<MojoParameter> getFlattenedNestedParameters(){
		Deque<MojoParameter> parametersToCheck = new ArrayDeque<>();
		List<MojoParameter> nestedParameters = new ArrayList<>();
		for (MojoParameter node : getNestedParameters()) {
			parametersToCheck.push(node);
		}
		while (!parametersToCheck.isEmpty()) {
			MojoParameter parameter = parametersToCheck.pop();
			if (!parameter.getNestedParameters().isEmpty()) {
				for (MojoParameter nestedParameter : parameter.getNestedParameters()) {
					parametersToCheck.push(nestedParameter);
				}
			} 
			nestedParameters.add(parameter);
		}
		return nestedParameters;
	}

	public boolean isRequired() {
		return this.required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getExpression() {
		return this.expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public String getDefaultValue() {
		return this.defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String toString() {
		return name + "{" + type + "}"
				+ (getNestedParameters().isEmpty() ? ""
						: " nested: ("
								+ getNestedParameters().stream().map(Object::toString).collect(Collectors.joining(", "))
								+ ")");
	}

	public MojoParameter getNestedParameter(String name) {
		List<MojoParameter> params = getNestedParameters();
		if (params.size() == 1) {
			MojoParameter param = params.get(0);
			if (param.isMultiple()) {
				return param;
			}
		}

		for (MojoParameter p : params) {
			if (p.name.equals(name)) {
				return p;
			}
		}
		return null;
	}

	public MojoParameter getContainer(String[] path) {
		if (path == null || path.length == 0) {
			return this;
		}

		MojoParameter param = this;
		int i = 0;
		while (param != null && i < path.length) {
			param = param.getNestedParameter(path[i]);
			i++;
		}

		if (param == null) {
			return null;
		}

		return param;
	}
	
	public Type getParamType() {
		return paramType;
	}

	public void setParamType(Type paramType) {
		this.paramType = paramType;
	}

	public String getDeprecatedNotice() {
		return this.deprecated;
	}

	@Override
	public int hashCode() {
		return Objects.hash(required, map, multiple, type, getNestedParameters().size(), name, expression, description,
				defaultValue);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MojoParameter)) {
			return false;
		}
		MojoParameter otherMojo = (MojoParameter) obj;

		return this.isRequired() == otherMojo.isRequired() //
				&& this.isMap() == otherMojo.isMap() //
				&& this.isMultiple() == otherMojo.isMultiple() //
				&& Objects.equals(this.type, otherMojo.type) //
				&& Objects.equals(this.getNestedParameters(), otherMojo.getNestedParameters()) //
				&& Objects.equals(this.name, otherMojo.name) //
				&& Objects.equals(this.getExpression(), otherMojo.getExpression()) //
				&& Objects.equals(this.getDescription(), otherMojo.getDescription()) //
				&& Objects.equals(this.getDefaultValue(), otherMojo.getDefaultValue()) //
				&& Objects.equals(this.getDeprecatedNotice(), otherMojo.getDeprecatedNotice());
	}

}
