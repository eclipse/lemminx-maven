/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import java.util.concurrent.CancellationException;

/**
 * Exception thrown when Maven is initializing.
 * 
 * @author Angelo ZERR
 *
 */
public class MavenInitializationException extends CancellationException{

}
