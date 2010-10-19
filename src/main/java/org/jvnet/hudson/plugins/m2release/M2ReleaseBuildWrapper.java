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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.jvnet.hudson.plugins.m2release.nexus.Stage;
import org.jvnet.hudson.plugins.m2release.nexus.StageClient;
import org.jvnet.hudson.plugins.m2release.nexus.StageException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	private transient Logger log = LoggerFactory.getLogger(M2ReleaseBuildWrapper.class);
	
	private transient boolean             doRelease           = false;
	private transient boolean             closeNexusStage     = true;
	private transient Map<String, String> versions;
	private transient boolean             appendHudsonBuildNumber;
	private transient String              repoDescription;
	private transient String              scmUsername;
	private transient String              scmPassword;
	private transient String              scmCommentPrefix;
	private transient boolean             appendHusonUserName;
	private transient String              hudsonUserName;

	public String                         releaseGoals                 = DescriptorImpl.DEFAULT_RELEASE_GOALS;
	public String                         defaultVersioningMode        = DescriptorImpl.DEFAULT_VERSIONING;
	public boolean                        selectCustomScmCommentPrefix = DescriptorImpl.DEFAULT_SELECT_CUSTOM_SCM_COMMENT_PREFIX;
	public boolean                        selectAppendHudsonUsername   = DescriptorImpl.DEFAULT_SELECT_APPEND_HUDSON_USERNAME;
	
	@DataBoundConstructor
	public M2ReleaseBuildWrapper(String releaseGoals, String defaultVersioningMode, boolean selectCustomScmCommentPrefix, boolean selectAppendHudsonUsername) {
		super();
		this.releaseGoals = releaseGoals;
		this.defaultVersioningMode = defaultVersioningMode;
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
	}


	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener)
	                                                                                              throws IOException,
	                                                                                              InterruptedException {
		final String originalGoals;
		final MavenModuleSet mmSet;
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
				
				String thisBuildGoals = releaseGoals;

				if (versions != null) {
					thisBuildGoals = generateVersionString(build.getNumber()) + thisBuildGoals;
				}
				
				if (scmUsername != null) {
					thisBuildGoals = "-Dusername=" + scmUsername + " " + thisBuildGoals;
				}
				
				if (scmPassword != null) {
					thisBuildGoals = "-Dpassword=" + scmPassword + " " + thisBuildGoals;
				}
				
				if (scmCommentPrefix != null) {
					final StringBuilder sb = new StringBuilder();
					sb.append("\"-DscmCommentPrefix=");
					sb.append(scmCommentPrefix);
					if(appendHusonUserName) {
						sb.append(String.format("(%s)", hudsonUserName));
					}
					sb.append("\" ");
					sb.append(thisBuildGoals);
					
					thisBuildGoals = sb.toString();
				}
				
				mmSet.setGoals(thisBuildGoals);
			}
			else {
				// can this be so?
				originalGoals = null;
			}
			mavenOpts = mmSet.getMavenOpts();
			
			build.addAction(new M2ReleaseBadgeAction("Release - " + getReleaseVersion(mmSet.getRootModule())));
		}
		
		return new Environment() {

			@Override
			public void buildEnvVars(java.util.Map<String, String> env) {
				// XXX these should all be handled by hudson!?
				if (mavenOpts != null && !env.containsKey("MAVEN_OPTS")) {
					env.put("MAVEN_OPTS", mavenOpts);
				}
				// maven.home is normally set by the mvn bat/shell script but Hudson doesn't use that..
				if (!env.containsKey("maven.home")) {
					env.put("maven.home", mmSet.getMaven().getHome());
				}
				/*
				//XXX are these already set?
				if (!env.containsKey("M2_HOME")) {
					env.put("M2_HOME", mmSet.getMaven().getHome());
				}
				if (!env.containsKey("java.home")) {
					env.put("java.home", mmSet.getJDK().getHome());
				}
				*/
			};


			@Override
			public boolean tearDown(AbstractBuild bld, BuildListener lstnr) throws IOException,
			                                                               InterruptedException {
				boolean retVal = true;
				// TODO only re-set the build goals if they are still releaseGoals to avoid mid-air collisions.
				final MavenModuleSet mmSet = getModuleSet(bld);
				final boolean localcloseStage;
				String version = null;
				synchronized (mmSet) {
					mmSet.setGoals(originalGoals);
					// get a local variable so we don't have to synchronise on mmSet any more than we have to.
					localcloseStage = closeNexusStage;
					version = getReleaseVersion(mmSet.getRootModule());
					versions = null;
				}

				if (localcloseStage) {
					StageClient client = new StageClient(new URL(getDescriptor().getNexusURL()), getDescriptor().getNexusUser(), getDescriptor().getNexusPassword()); 
					try {
						MavenModule rootModule = mmSet.getRootModule();
						// TODO grab the version that we have just released...
						Stage stage = client.getOpenStageID(rootModule.getModuleName().groupId, rootModule.getModuleName().artifactId, version);
						if (stage != null) {
							lstnr.getLogger().println("[M2Release] Closing repository " + stage);
							client.closeStage(stage, repoDescription);
							lstnr.getLogger().println("[M2Release] Closed staging repository.");
						}
						else {
							retVal = false;
							lstnr.fatalError("[M2Release] Could not find nexus stage repository for project.\n");
						}
					}
					catch (StageException ex) {
						lstnr.fatalError("[M2Release] Could not close repository , %s\n", ex.toString());
						retVal = false;
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
	
	public void setScmUsername(String scmUsername) {
		this.scmUsername = scmUsername;
	}
	
	public void setScmPassword(String scmPassword) {
		this.scmPassword = scmPassword;
	}

	public void setScmCommentPrefix(String scmCommentPrefix) {
		this.scmCommentPrefix = scmCommentPrefix;
	}
	
	public void setAppendHusonUserName(boolean appendHusonUserName) {
		this.appendHusonUserName = appendHusonUserName;
	}

	
  /**
   * @return the defaultVersioningMode
   */
  public String getDefaultVersioningMode() {
  	return defaultVersioningMode;
  }


	
  /**
   * @param defaultVersioningMode the defaultVersioningMode to set
   */
  public void setDefaultVersioningMode(String defaultVersioningMode) {
  	this.defaultVersioningMode = defaultVersioningMode;
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

	public void setHudsonUserName(String hudsonUserName) {
		this.hudsonUserName = hudsonUserName;
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


	private MavenModuleSet getModuleSet(AbstractBuild<?,?> build) {
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
		return new M2ReleaseAction((MavenModuleSet) job, defaultVersioningMode, selectCustomScmCommentPrefix, selectAppendHudsonUsername);
	}

	public static boolean hasReleasePermission(AbstractProject job) {
		return job.hasPermission(DescriptorImpl.CREATE_RELEASE);
	}


	public static void checkReleasePermission(AbstractProject job) {
		job.checkPermission(DescriptorImpl.CREATE_RELEASE);
	}

	private String getReleaseVersion(MavenModule moduleName) {
		String retVal = null;
		String key = "-Dproject.rel." + moduleName.getModuleName().toString();
		// versions is null if we let Maven work out the version
		if (versions != null) {
			retVal = versions.get(key);
				if (retVal == null) {
					// try autoVersionSubmodules
					retVal = versions.get("-DreleaseVersion"); //$NON-NLS-1$
				}
		}
		else {
			// we are auto versioning - so take a best guess and hope our last build was of the same version!
			retVal = moduleName.getVersion().replace("-SNAPSHOT", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return retVal;
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

		public static final String     VERSIONING_AUTO = "auto";                         //$NON-NLS-1$
		public static final String     VERSIONING_SPECIFY_VERSIONS = "specify_versions"; //$NON-NLS-1$
		public static final String     VERSIONING_SPECIFY_VERSION = "specify_version";   //$NON-NLS-1$
		public static final String     DEFAULT_VERSIONING = VERSIONING_AUTO;             //$NON-NLS-1$
		
		public static final boolean    DEFAULT_SELECT_CUSTOM_SCM_COMMENT_PREFIX = false;
		public static final boolean    DEFAULT_SELECT_APPEND_HUDSON_USERNAME    = false;
		
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
		                                 final @QueryParameter String usernameValue,
		                                 final @QueryParameter String passwordValue) throws IOException,
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
			URL url = null;
			try {
				url = new URL(testURL);
				if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https"))) {
					return FormValidation.error("protocol must be http or https");
				}
				StageClient client = new StageClient(new URL(testURL), usernameValue, passwordValue);
				client.checkAuthentication();
			}
			catch (MalformedURLException ex) {
				return FormValidation.error(url + " is not a valid URL");
			}
			catch (StageException ex) {
				FormValidation stageError = FormValidation.error(ex.getMessage());
				stageError.initCause(ex);
				return stageError; 
			}
			return FormValidation.ok();
		}
	}

}
