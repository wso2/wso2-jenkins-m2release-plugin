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

import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.Action;
import hudson.model.Cause;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * The action appears as the link in the side bar that users will click on in order to start the release
 * process.
 * 
 * @author James Nord
 * @version 0.2
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
		if (M2ReleaseBuildWrapper.hasReleasePermission(project)) {
			return "installer.gif"; //$NON-NLS-1$
		}
		// by returning null the link will not be shown. 
		return null;
	}


	public String getUrlName() {
		return "m2release"; //$NON-NLS-1$
	}

	public Collection<MavenModule> getModules() {
		return project.getModules();
	}
	
	public String computeReleaseVersion(String version) {
		return version.replace("-SNAPSHOT", "");
	}
	
	public String computeNextVersion(String version) {
		/// XXX would be nice to use maven to do this...
		String retVal = computeReleaseVersion(version);
		// get the integer after the last "."
		int dotIdx = retVal.lastIndexOf('.');
		if (dotIdx != -1) {
			dotIdx++;
			String ver = retVal.substring(dotIdx);
			int intVer = Integer.parseInt(ver);
			intVer += 1;
			retVal = retVal.substring(0, dotIdx);
			retVal = retVal + intVer;
		}
		else {
			int intVer = Integer.parseInt(retVal);
			intVer += 1;
			retVal = retVal.substring(0, dotIdx);
			retVal = Integer.toString(intVer);
		}
		return retVal + "-SNAPSHOT";
	}
	
	public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
		M2ReleaseBuildWrapper.checkReleasePermission(project);
		M2ReleaseBuildWrapper m2Wrapper = project.getBuildWrappers().get(M2ReleaseBuildWrapper.class);

		// JSON collapses everything in the dynamic specifyVersions section so we need to fall back to
		// good old http...
		Map<?,?> httpParams = req.getParameterMap();

		Map<String,String> versions = null;
		final boolean appendHudsonBuildNumber = httpParams.containsKey("appendHudsonBuildNumber");
		if (httpParams.containsKey("specifyVersions")) {
			versions = new HashMap<String,String>();
			for (Object key : httpParams.keySet()) {
				String keyStr = (String)key;
				if (keyStr.startsWith("-Dproject.")) {
					versions.put(keyStr, (String)(((Object[])httpParams.get(key))[0]));
				}
			}
		}
			
		// schedule release build
		synchronized (m2Wrapper) {
			if (project.scheduleBuild(0, new Cause.UserCause())) {
				m2Wrapper.enableRelease();
				m2Wrapper.setVersions(versions);
				m2Wrapper.setAppendHudsonBuildNumber(appendHudsonBuildNumber);
			}
		}
		// redirect to status page
		resp.sendRedirect(project.getAbsoluteUrl());
	}

}
