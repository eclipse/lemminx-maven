/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.lemminx.services.extensions.diagnostics.IXMLErrorCode;

public enum MavenSyntaxErrorCode implements IXMLErrorCode {
	DuplicationOfParentGroupId,
	DuplicationOfParentVersion,
	OverridingOfManagedDependency;

	private final String code;

	private MavenSyntaxErrorCode() {
		this(null);
	}

	private MavenSyntaxErrorCode(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		if (code == null) {
			return name();
		}
		return code;
	}
	
	private final static Map<String, MavenSyntaxErrorCode> codes;

	static {
		codes = new HashMap<>();
		for (MavenSyntaxErrorCode errorCode : values()) {
			codes.put(errorCode.getCode(), errorCode);
		}
	}

	public static MavenSyntaxErrorCode get(String name) {
		return codes.get(name);
	}
}
