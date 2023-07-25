/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lemminx.extensions.maven.searcher;

/**
 *  Maven Search API response is set to be of Json format, containing the "response" 
 *  root item, consisting of the following three main items:
 *  
 *  * numFound 	- number of items found
 *  * start 	- number of first item in response: the items can be transfered 
 *  			  partially, so start indicates a number of item in whole result
 *  * docs		- an array of response data items, the format of each data item
 *  			  depends on the type of request
 * 
 * For example: 
 *  
 *  {	
 *  	numFound=125, 
 *  	start=0, 
 *  	docs=
 *  		[
 *  			{
 *  				"id":"org.fujion:fujion-ckeditor",
 *  				"g":"org.fujion",
 *  				"a":"fujion-ckeditor",
 *  				"latestVersion":"3.1.0",
 *  				"repositoryId":"central",
 *  				"p":"jar",
 *  				"timestamp":1688564431000,
 *  				"versionCount":16,
 *  				"text":["org.fujion","fujion-ckeditor","-sources.jar",".pom","-javadoc.jar",".jar"],
 *  				"ec":["-sources.jar",".pom","-javadoc.jar",".jar"]
 *  			},
 *  			// ... other 124 data items ...
 *  		]
 *  }	
 */
public class JsonRemoteCentralRepositoryConstants {
	/**
	 * response - root item
	 */
	public static String RESPONSE = "response";
	
	/**
	 * numFound - number of items found
	 */
	public static String NUM_FOUND = "numFound";

	/**
	 * start - number of first item in response: the items can be transfered
	 * partially, so start indicates a number of item in whole result
	 */
	public static String START = "start";

	/**
	 * docs - an array of response data items, the format of each data item depends
	 * on the type of request
	 */
	public static String DOCS = "docs";
	
	/**
	 * Group ID of an artifact
	 */
	public static final String GROUP_ID = "g";

	/**
	 * Artifact ID of an artifact
	 */
	public static final String ARTIFACT_ID = "a";

	/**
	 * Version of an artifact
	 */
	public static final String VERSION = "v";

	/**
	 * The latest available version of an artifact
	 */
	public static final String LATEST_VERSION = "latestVersion";
}
