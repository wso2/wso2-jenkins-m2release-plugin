/*
 * The MIT License
 * 
 * Copyright (c) 2009, NDS Group Ltd., James Nord
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
package org.jvnet.hudson.plugins.m2release;

import hudson.maven.MavenModuleSet;
import hudson.model.Action;
import hudson.model.Cause;

import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * The action appears as the link in the side bar that users will click on in order to start the release
 * process.
 * 
 * @author James Nord
 */
public class M2ReleaseAction implements Action {

	private MavenModuleSet	project;


	public M2ReleaseAction(MavenModuleSet project) {
		this.project = project;
	}


	public String getDisplayName() {
		return "Perform Maven Release"; // TODO il8n
	}


	public String getIconFileName() {
		return "installer.gif"; //$NON-NLS-1$
	}


	public String getUrlName() {
		return "m2release"; //$NON-NLS-1$
	}


	public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException {
		// find our wrapper!
		M2ReleaseBuildWrapper m2Wrapper = project.getBuildWrappers().get(M2ReleaseBuildWrapper.class);

		// schedule release build
		synchronized (m2Wrapper) {
			if (project.scheduleBuild(0, new Cause.UserCause())) {
				m2Wrapper.enableRelease();
				// TODO enable configuration of release version
				// TODO enable embedding of SVN-revision as build number
			}
		}
		// redirect to status page
		resp.sendRedirect(project.getAbsoluteUrl());
	}

}
