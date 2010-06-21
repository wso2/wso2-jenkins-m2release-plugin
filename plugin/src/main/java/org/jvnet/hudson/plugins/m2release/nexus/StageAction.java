/*
 * The MIT License
 * 
 * Copyright (c) 2010, NDS Group Ltd., James Nord
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.m2release.nexus;

import java.net.MalformedURLException;
import java.net.URL;

public enum StageAction {
	CLOSE("%1$s/service/local/staging/profiles/%2$s/finish"),
	//PROMOTE("%1$s/service/local/staging/profiles/%2$s/promote"),
	DROP("%1$s/service/local/staging/profiles/%2$s/drop");
	
	/** 
	 * Template for the URL for this action.
	 * %1$ is baseURL
	 * %2$ is stage.getProfileID()
	 * %3$ is stage.getStageID()
	 */
	private String urlTemplate;
	
	/**
	 * @param urlTemplate the template for the URL.
	 * @see #urlTemplate
	 */
	private StageAction(String urlTemplate) {
		this.urlTemplate = urlTemplate;
	}
	
	/**
	 * Get the URL for this action for the specified stage and Nexus Base URL.
	 * @param baseURL the URL to the base of the Nexus server
	 * @param stage the stage on which to perform the action.
	 * @return The URL for the action
	 * @throws MalformedURLException if the URL is invalid
	 */
	public URL getURL(URL baseURL, Stage stage) throws MalformedURLException {
		return new URL(String.format(urlTemplate, baseURL, stage.getProfileID(), stage.getStageID()));
	}
	
}