/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.validation.DefaultModelValidator; 

public class ModelValidatorMNG7170 extends DefaultModelValidator {

	@Override
	public void validateRawModel(Model m, ModelBuildingRequest request, ModelProblemCollector problems) {
		if (m.getPomFile() == null && request.getModelSource() instanceof FileModelSource) {
			m.setPomFile(((FileModelSource)request.getModelSource()).getFile());
		}
		super.validateRawModel(m, request, problems);
	}
}
