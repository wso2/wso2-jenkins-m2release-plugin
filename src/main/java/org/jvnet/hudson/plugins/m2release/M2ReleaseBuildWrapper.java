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
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.RunList;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.plugins.m2release.nexus.Stage;
import org.jvnet.hudson.plugins.m2release.nexus.StageClient;
import org.jvnet.hudson.plugins.m2release.nexus.StageException;
import org.jvnet.localizer.Localizable;
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
 * @author Dominik Bartholdi
 * @version 0.3
 * @since 0.1
 */
public class M2ReleaseBuildWrapper extends BuildWrapper {
	
	
	private transient Logger log = LoggerFactory.getLogger(M2ReleaseBuildWrapper.class);
	
	private transient boolean             doRelease           = false;
	private transient boolean             closeNexusStage     = true;
	private transient boolean             isDryRun            = false;

    private transient String              releaseVersion;
	private transient String              developmentVersion;
	
	private transient boolean             appendHudsonBuildNumber;
	private transient String              repoDescription;
	private transient String              scmUsername;
	private transient String              scmPassword;
	private transient String              scmCommentPrefix;
	private transient String              scmTag;
	
	private transient boolean             appendHusonUserName;
	private transient String              hudsonUserName;

	/** For backwards compatibility with older configurations. */
	public transient boolean              defaultVersioningMode;
	
	private String                        scmUserEnvVar                = "";
	private String                        scmPasswordEnvVar            = "";
	private String                        releaseEnvVar                = DescriptorImpl.DEFAULT_RELEASE_ENVVAR;
	private String                        releaseGoals                 = DescriptorImpl.DEFAULT_RELEASE_GOALS;
	private String                        dryRunGoals                  = DescriptorImpl.DEFAULT_DRYRUN_GOALS;
	public boolean                        selectCustomScmCommentPrefix = DescriptorImpl.DEFAULT_SELECT_CUSTOM_SCM_COMMENT_PREFIX;
	public boolean                        selectAppendHudsonUsername   = DescriptorImpl.DEFAULT_SELECT_APPEND_HUDSON_USERNAME;
	public boolean                        selectScmCredentials         = DescriptorImpl.DEFAULT_SELECT_SCM_CREDENTIALS;
	
	@DataBoundConstructor
	public M2ReleaseBuildWrapper(String releaseGoals, String dryRunGoals, boolean selectCustomScmCommentPrefix, boolean selectAppendHudsonUsername, boolean selectScmCredentials, String releaseEnvVar, String scmUserEnvVar, String scmPasswordEnvVar) {
		super();
		this.releaseGoals = releaseGoals;
		this.dryRunGoals = dryRunGoals;
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
		this.selectScmCredentials = selectScmCredentials;
		this.releaseEnvVar = releaseEnvVar;
		this.scmUserEnvVar = scmUserEnvVar;
		this.scmPasswordEnvVar = scmPasswordEnvVar;
	}


	@Override
	public Environment setUp(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, final BuildListener listener)
	                                                                                              throws IOException,
	                                                                                              InterruptedException {
		
		synchronized (getModuleSet(build)) {
			if (!doRelease) {
				// we are not performing a release so don't need a custom tearDown.
				return new Environment() {
					/** intentionally blank */
					@Override
					public void buildEnvVars(Map<String, String> env) {
						if(StringUtils.isNotBlank(releaseEnvVar)){
							// inform others that we are NOT doing a release build
							env.put(releaseEnvVar, "false");
						}
					}
				};
			}
			// reset for the next build.
			doRelease = false;
			
			StringBuilder buildGoals = new StringBuilder();

			buildGoals.append("-DdevelopmentVersion=").append(developmentVersion).append(' ');
			buildGoals.append("-DreleaseVersion=").append(releaseVersion).append(' ');
			
			if (scmUsername != null) {
				buildGoals.append("-Dusername=").append(scmUsername).append(' ');
			}
			
			if (scmPassword != null) {
				buildGoals.append("-Dpassword=").append(scmPassword).append(' ');					
			}
			
			if (scmCommentPrefix != null) {
				buildGoals.append("\"-DscmCommentPrefix=");
				buildGoals.append(scmCommentPrefix);
				if(appendHusonUserName) {
				    buildGoals.append(String.format("(%s)", hudsonUserName));
				}
				buildGoals.append("\" ");
			}
			
			if (scmTag != null) {
			    buildGoals.append("-Dtag=").append(scmTag).append(' ');
			}
			
			if(isDryRun){
			    buildGoals.append(getDryRunGoals());
			}else{
			    buildGoals.append(getReleaseGoals());    
			}
			
			build.addAction(new M2ReleaseArgumentInterceptorAction(buildGoals.toString()));
			build.addAction(new M2ReleaseBadgeAction(releaseVersion, isDryRun));
		}
		
		return new Environment() {
			
			@Override
			public void buildEnvVars(Map<String, String> env) {
				if(StringUtils.isNotBlank(releaseEnvVar)){
					// inform others that we are doing a release build
					env.put(releaseEnvVar, "true");
				}
			}

			@Override
			public boolean tearDown(@SuppressWarnings("rawtypes") AbstractBuild bld, BuildListener lstnr) throws IOException,
			                                                               InterruptedException {
				boolean retVal = true;
				final MavenModuleSet mmSet = getModuleSet(bld);
				final boolean localcloseStage;
				synchronized (mmSet) {
					// get a local variable so we don't have to synchronize on mmSet any more than we have to.
					localcloseStage = closeNexusStage;
				}
				
				if(isDryRun){
					lstnr.getLogger().println("[M2Release] its only a dryRun, no need to mark it for keep");
				}

				if (bld.getResult() != null && bld.getResult().isBetterOrEqualTo(Result.SUCCESS) && !isDryRun) {
				    // keep this build.
				    lstnr.getLogger().println("[M2Release] marking build to keep until the next release build");
                    bld.keepLog();

				    for (Run run: (RunList<? extends Run>) (bld.getProject().getBuilds())) {
				        M2ReleaseBadgeAction a = run.getAction(M2ReleaseBadgeAction.class);
			            if (a!=null && run.getResult()== Result.SUCCESS && !a.isDryRun()) {
			                if (bld.getNumber() != run.getNumber()) {
			                    lstnr.getLogger().println("[M2Release] removing keep build from build " + run.getNumber());
			                    run.keepLog(false);
			                    break;
			                }
			            }
			        }
				}
				
				if (localcloseStage && !isDryRun) {
					StageClient client = new StageClient(new URL(getDescriptor().getNexusURL()), getDescriptor().getNexusUser(), getDescriptor().getNexusPassword()); 
					try {
						MavenModule rootModule = mmSet.getRootModule();
						// TODO grab the version that we have just released...
						Stage stage = client.getOpenStageID(rootModule.getModuleName().groupId, rootModule.getModuleName().artifactId, releaseVersion);
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
						log.error("[M2Release] Could not close repository", ex);
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
	
	void markAsDryRun(boolean isDryRun){
		this.isDryRun = isDryRun;
	}

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }


    public void setDevelopmentVersion(String developmentVersion) {
        this.developmentVersion = developmentVersion;
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
	
	/**
     * @param scmTag the scmTag to set
     */
    public void setScmTag(String scmTag) {
        this.scmTag = scmTag;
    }


    public void setAppendHusonUserName(boolean appendHusonUserName) {
		this.appendHusonUserName = appendHusonUserName;
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
	public Action getProjectAction(@SuppressWarnings("rawtypes") AbstractProject job) {
		return new M2ReleaseAction((MavenModuleSet) job, selectCustomScmCommentPrefix, selectAppendHudsonUsername, selectScmCredentials);
	}

	public static boolean hasReleasePermission(@SuppressWarnings("rawtypes") AbstractProject job) {
		return job.hasPermission(DescriptorImpl.CREATE_RELEASE);
	}


	public static void checkReleasePermission(@SuppressWarnings("rawtypes") AbstractProject job) {
		job.checkPermission(DescriptorImpl.CREATE_RELEASE);
	}

	public String getReleaseEnvVar() {
		return releaseEnvVar;
	}

	public String getScmUserEnvVar() {
		return scmUserEnvVar;
	}
	
	public String getScmPasswordEnvVar() {
		return scmPasswordEnvVar;
	}
	
	public String getReleaseGoals() {
		return StringUtils.isBlank(releaseGoals) ? DescriptorImpl.DEFAULT_RELEASE_GOALS : releaseGoals;
	}
	
	public String getDryRunGoals() {
        return StringUtils.isBlank(dryRunGoals) ? DescriptorImpl.DEFAULT_DRYRUN_GOALS : dryRunGoals;
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
		
		//public static final PermissionGroup PERMISSIONS = new PermissionGroup(M2ReleaseBuildWrapper.class, Messages._PermissionGroup_Title());
		public static final Permission CREATE_RELEASE;
		
		static {
			Permission tmpPerm = null;
			try {
				// Jenkins change the security model in a non backward compatible way :-(
				// JENKINS-10661
				Class<?> permissionScopeClass = Class.forName("hudson.security.PermissionScope");
				Object psArr = Array.newInstance(permissionScopeClass, 2);
				Field f;
				f = permissionScopeClass.getDeclaredField("JENKINS");
				Array.set(psArr, 0, f.get(null));
				f = permissionScopeClass.getDeclaredField("ITEM");
				Array.set(psArr, 1, f.get(null));
				
				Object[] permissionScopes = (Object[]) psArr;
				
				Constructor<Permission> ctor = Permission.class.getConstructor(PermissionGroup.class, 
						String.class, 
						Localizable.class, 
						Permission.class, 
//						boolean.class,
						permissionScopeClass);
						//permissionScopes.getClass());
				tmpPerm = ctor.newInstance(Item.PERMISSIONS, 
				                           "Release",
				                            Messages._CreateReleasePermission_Description(),
				                            Hudson.ADMINISTER,
//				                            true,
				                            f.get(null));
			}
			// all these exceptions are Jenkins < 1.421 or Hudson
			// wouldn#t multicatch be nice!
			catch (NoSuchMethodException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (InvocationTargetException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (IllegalArgumentException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (IllegalAccessException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (InstantiationException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (NoSuchFieldException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			catch (ClassNotFoundException ex) {
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new PermissionScope not detected. {}", ex.getMessage());
			}
			if (tmpPerm == null) {
				tmpPerm = new Permission(Item.PERMISSIONS,
				                         "Release", //$NON-NLS-1$
				                          Messages._CreateReleasePermission_Description(),
				                          Hudson.ADMINISTER);
			}
			CREATE_RELEASE = tmpPerm;
		}

		public static final String     DEFAULT_RELEASE_GOALS = "-Dresume=false release:prepare release:perform"; //$NON-NLS-1$
		public static final String     DEFAULT_DRYRUN_GOALS = "-Dresume=false -DdryRun=true release:prepare"; //$NON-NLS-1$
		public static final String     DEFAULT_RELEASE_ENVVAR = "IS_M2RELEASEBUILD"; //$NON-NLS-1$
		public static final String     DEFAULT_RELEASE_VERSION_ENVVAR = "MVN_RELEASE_VERSION"; //$NON-NLS-1$
		public static final String     DEFAULT_DEV_VERSION_ENVVAR = "MVN_DEV_VERSION"; //$NON-NLS-1$
		public static final String     DEFAULT_DRYRUN_ENVVAR = "MVN_ISDRYRUN"; //$NON-NLS-1$

		public static final boolean    DEFAULT_SELECT_CUSTOM_SCM_COMMENT_PREFIX = false;
		public static final boolean    DEFAULT_SELECT_APPEND_HUDSON_USERNAME    = false;
		public static final boolean    DEFAULT_SELECT_SCM_CREDENTIALS           = false;
		
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
