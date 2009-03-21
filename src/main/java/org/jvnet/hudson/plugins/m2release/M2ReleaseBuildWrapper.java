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

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.AbstractMavenProject;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Wraps a {@link MavenBuild} to be able to run the 
 * <a href="http://maven.apache.org/plugins/maven-release-plugin/">maven release plugin</a> on demand.
 * 
 * @author James Nord
 * @version 0.2
 * @since 0.1
 */
public class M2ReleaseBuildWrapper extends BuildWrapper {

	private transient boolean doRelease = false;
	private transient Map<String,String> versions;
	private transient boolean appendHudsonBuildNumber;

	public String releaseGoals = DescriptorImpl.DEFAULT_RELEASE_GOALS;

	@DataBoundConstructor
	public M2ReleaseBuildWrapper(String releaseGoals) {
		super();
		this.releaseGoals = releaseGoals;
	}


	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
	                                                                                        throws IOException,
	                                                                                        InterruptedException {
		if (!doRelease) {
			// we are not performing a release so don't need a custom tearDown.
			return new Environment() {
				/** intentionally blank */
			};
		}
		// reset for the next build.
		doRelease = false;

		// TODO do we need to set the goals back?
		final String originalGoals;
		if (build instanceof MavenBuild) {
			MavenBuild m2Build = (MavenBuild) build;
			MavenModule mm = m2Build.getProject();
			MavenModuleSet mmSet = mm.getParent();
			originalGoals = mmSet.getGoals();
			mmSet.setGoals(releaseGoals);
			if (versions != null) {				
				mmSet.setGoals(generateVersionString(build.getNumber()) + releaseGoals);
			}
			else {
				mmSet.setGoals(releaseGoals);
			}
		}
		else if (build instanceof MavenModuleSetBuild) {
			MavenModuleSetBuild m2moduleSetBuild = (MavenModuleSetBuild) build;
			MavenModuleSet mmSet = m2moduleSetBuild.getProject();
			originalGoals = mmSet.getGoals();
			if (versions != null) {
				mmSet.setGoals(generateVersionString(build.getNumber()) + releaseGoals);
			}
			else {
				mmSet.setGoals(releaseGoals);
			}
		}
		else {
			originalGoals = null;
		}

		return new Environment() {

			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException,
			                                                                    InterruptedException {
				// TODO only re-set the build goals if they are still releaseGoals to avoid mid-air collisions.
				if (build instanceof MavenBuild) {
					MavenBuild m2Build = (MavenBuild) build;
					MavenModule mm = m2Build.getProject();
					MavenModuleSet mmSet = mm.getParent();
					mmSet.setGoals(originalGoals);
				}
				else if (build instanceof MavenModuleSetBuild) {
					MavenModuleSetBuild m2moduleSetBuild = (MavenModuleSetBuild) build;
					MavenModuleSet mmSet = m2moduleSetBuild.getProject();
					mmSet.setGoals(originalGoals);
				}
				versions = null;
				return true;
			}
		};
	}


	void enableRelease() {
		doRelease = true;
	}

	void setVersions(Map<String,String> versions) {
		// expects a map of key="-Dproject.rel.${m.moduleName}" value="version"
		this.versions = versions;
	}

	public void setAppendHudsonBuildNumber(boolean appendHudsonBuildNumber) {
		this.appendHudsonBuildNumber = appendHudsonBuildNumber;
	}

	private String generateVersionString(int buildNumber) {
		// -Dproject.rel.org.mycompany.group.project=version ....
		StringBuilder sb = new StringBuilder();
		for (String key : versions.keySet()) {
			sb.append(key);
			sb.append('=');
			sb.append(versions.get(key));
			if (appendHudsonBuildNumber && key.startsWith("-Dproject.rel")) {
				sb.append('-');
				sb.append(buildNumber);
			}
			sb.append(' ');
		}
		return sb.toString();
	}

	@Override
	public Action getProjectAction(AbstractProject job) {
		return new M2ReleaseAction((MavenModuleSet) job);
	}

	public static boolean hasReleasePermission(AbstractProject job) {
		return job.hasPermission(Item.BUILD);
	}

	public static void checkReleasePermission(AbstractProject job) {
		job.checkPermission(Item.BUILD);
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		
		public static final String DEFAULT_RELEASE_GOALS = "-Dresume=false release:prepare release:perform"; //$NON-NLS-1$

		public DescriptorImpl() {
			super(M2ReleaseBuildWrapper.class);
			// load();
		}


		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return (item instanceof AbstractMavenProject);
		}


		@Override
		public String getDisplayName() {
			return "Maven release build";
		}

	}

}
