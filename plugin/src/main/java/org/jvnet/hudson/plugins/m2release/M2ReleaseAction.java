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
import hudson.model.Hudson;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper.DescriptorImpl;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * The action appears as the link in the side bar that users will click on in order to start the release
 * process.
 * 
 * @author James Nord
 * @version 0.3
 */
public class M2ReleaseAction implements Action {

	private MavenModuleSet project;
	private String versioningMode;
	private boolean selectCustomScmCommentPrefix;
	private boolean selectAppendHudsonUsername;
	
	public M2ReleaseAction(MavenModuleSet project, String versioningMode, boolean selectCustomScmCommentPrefix, boolean selectAppendHudsonUsername) {
		this.project = project;
		this.versioningMode = versioningMode;
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
	}


	public String getDisplayName() {
		return Messages.ReleaseAction_perform_release_name();
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

	public String getVersioningMode() {
		return versioningMode; 
	}
	
	public void setVersioningMode(String versioningMode) {
		this.versioningMode = versioningMode;
	}
	
	public boolean isSelectCustomScmCommentPrefix() {
		return selectCustomScmCommentPrefix;
	}

	public void setSelectCustomScmCommentPrefix(boolean selectCustomScmCommentPrefix) {
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
	}

	public boolean isSelectAppendHudsonUsername() {
		return selectAppendHudsonUsername;
	}

	public void setSelectAppendHudsonUsername(boolean selectAppendHudsonUsername) {
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
	}

	public Collection<MavenModule> getModules() {
		return project.getModules();
	}
	
	public MavenModule getRootModule() {
		return project.getRootModule();
	}
	
	public String computeReleaseVersion(String version) {
		return version.replace("-SNAPSHOT", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public String computeRepoDescription() {
		return "Release " + computeReleaseVersion(project.getRootModule().getVersion()) + " of " + project.getRootModule().getName();
	}

	public String computeNextVersion(String version) {
		/// XXX would be nice to use maven to do this...
		/// tip: see DefaultVersionInfo.getNextVersion() in org.apache.maven.release:maven-release-manager
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
			//just a major version...
			try {
				int intVer = Integer.parseInt(retVal);
				intVer += 1;
				retVal = Integer.toString(intVer);
			}
			catch (NumberFormatException nfEx) {
				// not a major version - just a qualifier!
				Logger logger = Logger.getLogger(this.getClass().getName());
				logger.log(Level.WARNING, "{0} is not a number, so I can't work out the next version.",
				           new Object[] {retVal});
				retVal = "NaN";
			}
		}
		return retVal + "-SNAPSHOT"; //$NON-NLS-1$
	}
	
	public boolean isNexusSupportEnabled() {
		return project.getBuildWrappersList().get(M2ReleaseBuildWrapper.class).getDescriptor().isNexusSupport();
	}
	
	public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
		M2ReleaseBuildWrapper.checkReleasePermission(project);
		M2ReleaseBuildWrapper m2Wrapper = project.getBuildWrappersList().get(M2ReleaseBuildWrapper.class);

		// JSON collapses everything in the dynamic specifyVersions section so we need to fall back to
		// good old http...
		Map<?,?> httpParams = req.getParameterMap();

		Map<String,String> versions = null;
		
		final boolean appendHudsonBuildNumber = httpParams.containsKey("appendHudsonBuildNumber"); //$NON-NLS-1$
		final boolean closeNexusStage = httpParams.containsKey("closeNexusStage"); //$NON-NLS-1$
		final String repoDescription = closeNexusStage ? getString("repoDescription", httpParams) : ""; //$NON-NLS-1$
		final boolean specifyScmCredentials = httpParams.containsKey("specifyScmCredentials"); //$NON-NLS-1$
		final String scmUsername = specifyScmCredentials ? getString("scmUsername", httpParams) : null; //$NON-NLS-1$
		final String scmPassword = specifyScmCredentials ? getString("scmPassword", httpParams) : null; //$NON-NLS-1$
		final boolean specifyScmCommentPrefix = httpParams.containsKey("specifyScmCommentPrefix"); //$NON-NLS-1$
		final String scmCommentPrefix = specifyScmCommentPrefix ? getString("scmCommentPrefix", httpParams) : null; //$NON-NLS-1$
		final boolean appendHusonUserName = specifyScmCommentPrefix && httpParams.containsKey("appendHudsonUserName"); //$NON-NLS-1$
		
		final String versioningMode = getString("versioningMode", httpParams);
		
		if (DescriptorImpl.VERSIONING_SPECIFY_VERSIONS.equals(versioningMode)) {
			versions = new HashMap<String,String>();
			for (Object key : httpParams.keySet()) {
				String keyStr = (String)key;
				if (keyStr.startsWith("-Dproject.")) {
					versions.put(keyStr, getString(keyStr, httpParams));
				}
			}
		} else if (DescriptorImpl.VERSIONING_SPECIFY_VERSION.equals(versioningMode)) {
			versions = new HashMap<String, String>();

			final String releaseVersion = getString("releaseVersion", httpParams); //$NON-NLS-1$
			final String developmentVersion = getString("developmentVersion", httpParams); //$NON-NLS-1$

			for(MavenModule mavenModule : getModules()) {
				final String name = mavenModule.getModuleName().toString();
				
				versions.put(String.format("-Dproject.dev.%s", name), developmentVersion); //$NON-NLS-1$
				versions.put(String.format("-Dproject.rel.%s", name), releaseVersion); //$NON-NLS-1$
			}
		}
		
		// schedule release build
		synchronized (project) {
			if (project.scheduleBuild(0, new ReleaseCause())) {
				m2Wrapper.enableRelease();
				m2Wrapper.setVersions(versions);
				m2Wrapper.setAppendHudsonBuildNumber(appendHudsonBuildNumber);
				m2Wrapper.setCloseNexusStage(closeNexusStage);
				m2Wrapper.setRepoDescription(repoDescription);
				m2Wrapper.setScmUsername(scmUsername);
				m2Wrapper.setScmPassword(scmPassword);
				m2Wrapper.setScmCommentPrefix(scmCommentPrefix);
				m2Wrapper.setAppendHusonUserName(appendHusonUserName);
				m2Wrapper.setHudsonUserName(Hudson.getAuthentication().getName());
			}
		}
		// redirect to status page
		resp.sendRedirect(project.getAbsoluteUrl());
	}

	/**
	 * returns the value of the key as a String. if multiple values
	 * have been submitted, the first one will be returned.
	 * @param key
	 * @param httpParams
	 * @return
	 */
	private String getString(String key, Map<?,?> httpParams) {
		return (String)(((Object[])httpParams.get(key))[0]);
	}
}
