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
import hudson.Util;
import hudson.maven.AbstractMavenProject;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.Permission;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.sonatype.nexus.restlight.common.RESTLightClientException;
import org.sonatype.nexus.restlight.stage.StageClient;
import org.sonatype.nexus.restlight.stage.StageRepository;

/**
 * Wraps a {@link MavenBuild} to be able to run the <a
 * href="http://maven.apache.org/plugins/maven-release-plugin/">maven release plugin</a> on demand, with the
 * ability to auto close a Nexus Pro Staging Repo
 * 
 * @author James Nord
 * @version 0.3
 * @since 0.1
 */
public class M2ReleaseBuildWrapper extends BuildWrapper {

	private transient boolean             doRelease           = false;
	private transient boolean             closeNexusStage     = true;
	private transient Map<String, String> versions;
	private transient boolean             appendHudsonBuildNumber;
	private transient String              repoDescription;
	
	public String                         releaseGoals        = DescriptorImpl.DEFAULT_RELEASE_GOALS;
	

	@DataBoundConstructor
	public M2ReleaseBuildWrapper(String releaseGoals) {
		super();
		this.releaseGoals = releaseGoals;
	}


	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener)
	                                                                                              throws IOException,
	                                                                                              InterruptedException {
		final String originalGoals;
		MavenModuleSet mmSet;
		final String mavenOpts;
		
		synchronized (getModuleSet(build)) {
			if (!doRelease) {
				// we are not performing a release so don't need a custom tearDown.
				return new Environment() {
					/** intentionally blank */
				};
			}
			// reset for the next build.
			doRelease = false;

			mmSet = getModuleSet(build);
			if (mmSet != null) {
				originalGoals = mmSet.getGoals();
				mmSet.setGoals(releaseGoals);

				if (versions != null) {
					mmSet.setGoals(generateVersionString(build.getNumber()) + releaseGoals);
				}
				else {
					mmSet.setGoals(releaseGoals);
				}
			}
			else {
				// can this be so?
				originalGoals = null;
			}
			mavenOpts = mmSet.getMavenOpts();
		}
		
		return new Environment() {

			@Override
			public void buildEnvVars(java.util.Map<String, String> env) {
				if (mavenOpts != null && !env.containsKey("MAVEN_OPTS")) {
					env.put("MAVEN_OPTS", mavenOpts);
				}
			};


			@Override
			public boolean tearDown(AbstractBuild bld, BuildListener lstnr) throws IOException,
			                                                               InterruptedException {
				boolean retVal = true;
				// TODO only re-set the build goals if they are still releaseGoals to avoid mid-air collisions.
				final MavenModuleSet mmSet = getModuleSet(bld);
				final boolean localcloseStage;
				synchronized (mmSet) {
					mmSet.setGoals(originalGoals);
					// get a local variable so we don't have to synchronise on mmSet any more than we have to.
					localcloseStage = closeNexusStage;
					versions = null;
				}

				if (localcloseStage) {
					// nexus client tries to load the vocab using the contextClassLoader.
					ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
					ClassLoader newLoader = StageClient.class.getClassLoader();
					Thread.currentThread().setContextClassLoader(newLoader);
					try {
						StageClient client = new StageClient(getDescriptor().getNexusURL(),
						                                     getDescriptor().getNexusUser(),
						                                     getDescriptor().getNexusPassword());
						MavenModule rootModule = mmSet.getRootModule();
						// TODO add support for a user supplied comment.
						StageRepository repo = client.getOpenStageRepositoryForUser(rootModule.getModuleName().groupId,
						                                                            rootModule.getModuleName().artifactId,
						                                                            repoDescription);
						if (repo != null) {
							lstnr.getLogger().append("[M2Release] Closing repository " + repo.getRepositoryId() + " at "
							                             + repo.getUrl());
							client.finishRepository(repo, "Stage for: " + rootModule.getDisplayName());
							lstnr.getLogger().append("[M2Release] Closed staging repository.");
						}
						else {
							retVal = false;
							lstnr.fatalError("[M2Release] Could not find nexus stage repository for project.\n");
						}
					}
					catch (RESTLightClientException ex) {
						lstnr.fatalError("[M2Release] Could not close repository , %s\n", ex.toString());
						retVal = false;
					}
					finally {
						Thread.currentThread().setContextClassLoader(originalLoader);
					}
				}
				return retVal;
			}
		};
	}


	void enableRelease() {
		doRelease = true;
	}


	void setVersions(Map<String, String> versions) {
		// expects a map of key="-Dproject.rel.${m.moduleName}" value="version"
		this.versions = versions;
	}


	public void setAppendHudsonBuildNumber(boolean appendHudsonBuildNumber) {
		this.appendHudsonBuildNumber = appendHudsonBuildNumber;
	}

	public void setCloseNexusStage(boolean closeNexusStage) {
		this.closeNexusStage = closeNexusStage;
	}

	public void setRepoDescription(String repoDescription) {
		this.repoDescription = repoDescription;
	}


	private String generateVersionString(int buildNumber) {
		// -Dproject.rel.org.mycompany.group.project=version ....
		StringBuilder sb = new StringBuilder();
		for (String key : versions.keySet()) {
			sb.append(key);
			sb.append('=');
			sb.append(versions.get(key));
			if (appendHudsonBuildNumber && key.startsWith("-Dproject.rel")) { //$NON-NLS-1$
				sb.append('-');
				sb.append(buildNumber);
			}
			sb.append(' ');
		}
		return sb.toString();
	}


	private String getReleaseVersion(MavenModule moduleName) {
		String retVal = null;
		String key = "-Dproject.rel." + moduleName.getModuleName().toString();
		retVal = versions.get(key);
		if (retVal == null) {
			// we are auto versioning...
			retVal = moduleName.getVersion().replace("-SNAPSHOT", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return retVal;
	}


	private MavenModuleSet getModuleSet(AbstractBuild build) {
		if (build instanceof MavenBuild) {
			MavenBuild m2Build = (MavenBuild) build;
			MavenModule mm = m2Build.getProject();
			MavenModuleSet mmSet = mm.getParent();
			return mmSet;
		}
		else if (build instanceof MavenModuleSetBuild) {
			MavenModuleSetBuild m2moduleSetBuild = (MavenModuleSetBuild) build;
			MavenModuleSet mmSet = m2moduleSetBuild.getProject();
			return mmSet;
		}
		else {
			return null;
		}
	}


	@Override
	public Action getProjectAction(AbstractProject job) {
		return new M2ReleaseAction((MavenModuleSet) job);
	}

	public static boolean hasReleasePermission(AbstractProject job) {
		return job.hasPermission(DescriptorImpl.CREATE_RELEASE);
	}


	public static void checkReleasePermission(AbstractProject job) {
		job.checkPermission(DescriptorImpl.CREATE_RELEASE);
	}

  /**
	 * Hudson defines a method {@link Builder#getDescriptor()}, which returns the corresponding
	 * {@link Descriptor} object. Since we know that it's actually {@link DescriptorImpl}, override the method
	 * and give a better return type, so that we can access {@link DescriptorImpl} methods more easily. This is
	 * not necessary, but just a coding style preference.
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		// see Descriptor javadoc for more about what a descriptor is.
		return (DescriptorImpl) super.getDescriptor();
	}


	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {

		public static final String     DEFAULT_RELEASE_GOALS = "-Dresume=false release:prepare release:perform"; //$NON-NLS-1$
		public static final Permission CREATE_RELEASE        = new Permission(Item.PERMISSIONS,
		                                                                      "Release", //$NON-NLS-1$
		                                                                      Messages._CreateReleasePermission_Description(),
		                                                                      Hudson.ADMINISTER); 

		private boolean nexusSupport  = false;
		private String  nexusURL      = null;
		private String  nexusUser     = "deployment";                                    //$NON-NLS-1$
		private String  nexusPassword = "deployment123";                                 //$NON-NLS-1$
		


		public DescriptorImpl() {
			super(M2ReleaseBuildWrapper.class);
			load();
		}


		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return (item instanceof AbstractMavenProject);
		}

		@Override
		public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException {
			nexusSupport = json.containsKey("nexusSupport"); //$NON-NLS-1$
			if (nexusSupport) {
				JSONObject nexusParams = json.getJSONObject("nexusSupport"); //$NON-NLS-1$
				nexusURL = Util.fixEmpty(nexusParams.getString("nexusURL")); //$NON-NLS-1$
				if (nexusURL != null && nexusURL.endsWith("/")) { //$NON-NLS-1$
					nexusURL = nexusURL.substring(0, nexusURL.length() - 1);
				}
				nexusUser = Util.fixEmpty(nexusParams.getString("nexusUser")); //$NON-NLS-1$
				nexusPassword = nexusParams.getString("nexusPassword"); //$NON-NLS-1$
			}
			save();
			return true; // indicate that everything is good so far
		}

		@Override
		public String getDisplayName() {
			return Messages.Wrapper_DisplayName();
		}


		public String getNexusURL() {
			return nexusURL;
		}


		public String getNexusUser() {
			return nexusUser;
		}


		public String getNexusPassword() {
			return nexusPassword;
		}


		public boolean isNexusSupport() {
			return nexusSupport;
		}

		/**
		 * Checks if the Nexus URL exists and we can authenticate against it.
		 */
		public FormValidation doUrlCheck(@QueryParameter String urlValue, 
		                                  @QueryParameter String usernameValue,
		                                  @QueryParameter String passwordValue) throws IOException,
		                                                                      ServletException {
			// this method can be used to check if a file exists anywhere in the file system,
			// so it should be protected.
			if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
				return FormValidation.ok();
			}
			
			urlValue = Util.fixEmptyAndTrim(urlValue);
			if (urlValue == null) {
				return FormValidation.ok();
			}
			final String testURL;
			if (urlValue.endsWith("/")) {
				testURL = urlValue.substring(0, urlValue.length() - 1);
			}
			else {
				testURL = urlValue;
			}

			FormValidation httpCheck = new URLCheck() {
				@Override
				protected FormValidation check() throws IOException, ServletException{
					String v = testURL;
					URL url= null;
					try {
						url = new URL(v);
						if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https"))) {
							return FormValidation.error("protocol must be http or https");
						}
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						int status = con.getResponseCode();
						con.disconnect();
						if (status != 200) {
							return FormValidation.error("Error communicating with server (web server returned " + status + ")");
						}
						if (findText(open(url), "Sonatype Nexus&trade; Professional Edition")) { //$NON-NLS-1$
							return FormValidation.ok();
						}
						else if (findText(open(url), "Sonatype Nexus")) { //$NON-NLS-1$
							return FormValidation.error("This is a valid Nexus URL but it looks like Nexus OSS Edition");
						}
						else {
							return FormValidation.error("This is a valid URL but it doesn't look like Nexus Professional");
						}
					}
					catch (MalformedURLException ex) {
						return FormValidation.error(v + " is not a valid URL");
					}
					catch (UnknownHostException ex) {
						return FormValidation.error("Could not resolve host \"" + url.getHost() + '\"');
					}
					catch (IOException e) {
						return handleIOException(v, e);
					}
				}
			}.check();
			switch (httpCheck.kind) {
				case ERROR:
				case WARNING:
					return httpCheck;
				case OK:
					// keep checking
					break;
			}
			try {
				StageClient client = new StageClient(testURL, usernameValue, passwordValue);
				client.getOpenStageRepositoriesForUser();
			}
			catch (RESTLightClientException e) {
				FormValidation stageError = FormValidation.error(e.getMessage());
				stageError.initCause(e);
				return stageError; 
			}
			return FormValidation.ok();
		}
		

	}

}
