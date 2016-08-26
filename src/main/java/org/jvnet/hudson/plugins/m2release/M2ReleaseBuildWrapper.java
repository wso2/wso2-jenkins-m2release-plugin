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
import hudson.FilePath;
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
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.FormValidation;
import hudson.util.RunList;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

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

	private static final String GITHUB_PUSH_CAUSE = "com.cloudbees.jenkins.GitHubPushCause";
	private transient Logger log = LoggerFactory.getLogger(M2ReleaseBuildWrapper.class);

	/** For backwards compatibility with older configurations. @deprecated */
	@Deprecated
	public transient boolean              defaultVersioningMode;
	
	private String                        scmUserEnvVar                = "";
	private String                        scmPasswordEnvVar            = "";
	private String                        releaseEnvVar                = DescriptorImpl.DEFAULT_RELEASE_ENVVAR;
	private String                        releaseGoals                 = DescriptorImpl.DEFAULT_RELEASE_GOALS;
	private String                        dryRunGoals                  = DescriptorImpl.DEFAULT_DRYRUN_GOALS;
	public boolean                        selectCustomScmCommentPrefix = DescriptorImpl.DEFAULT_SELECT_CUSTOM_SCM_COMMENT_PREFIX;
	public boolean                        selectAppendHudsonUsername   = DescriptorImpl.DEFAULT_SELECT_APPEND_HUDSON_USERNAME;
	public boolean                        selectScmCredentials         = DescriptorImpl.DEFAULT_SELECT_SCM_CREDENTIALS;
	
	public int                            numberOfReleaseBuildsToKeep  = DescriptorImpl.DEFAULT_NUMBER_OF_RELEASE_BUILDS_TO_KEEP;
	
	@DataBoundConstructor
	public M2ReleaseBuildWrapper(String releaseGoals, String dryRunGoals, boolean selectCustomScmCommentPrefix, boolean selectAppendHudsonUsername, boolean selectScmCredentials, String releaseEnvVar, String scmUserEnvVar, String scmPasswordEnvVar, int numberOfReleaseBuildsToKeep) {
		super();
		this.releaseGoals = releaseGoals;
		this.dryRunGoals = dryRunGoals;
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
		this.selectScmCredentials = selectScmCredentials;
		this.releaseEnvVar = releaseEnvVar;
		this.scmUserEnvVar = scmUserEnvVar;
		this.scmPasswordEnvVar = scmPasswordEnvVar;
		this.numberOfReleaseBuildsToKeep = numberOfReleaseBuildsToKeep;
	}


	@Override
	public Environment setUp(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, final BuildListener listener)
	                                                                                              throws IOException,
	                                                                                              InterruptedException {

		if (!isReleaseBuild(build) && !isTriggeredByGitPush(build)) {
			log.debug("Build trigger causes: {} ", build.getCauses());
			// we are not performing a release so don't need a custom tearDown.
			return new Environment() {
				/** intentionally blank */
				@Override
				public void buildEnvVars(Map<String, String> env) {
					if (StringUtils.isNotBlank(releaseEnvVar)) {
						// inform others that we are NOT doing a release build
						env.put(releaseEnvVar, "false");
					}
				}
			};
		}
		/* START WSO2 changes */
		File pollingLog = new SCMTrigger.BuildAction(build).getPollingLogFile();
		Iterator iterator = FileUtils.lineIterator(pollingLog);

		String remoteRevision = null;
		String remoteBranch = null;
		Boolean changesFound = null;
		while (iterator.hasNext()) {
			String line = (String) iterator.next();
			String pollHeadRegex = "(\\[poll\\] Latest remote head revision on ([^\\s]*) is: ([0-9a-f]*))|(Changes found)";
			Pattern pattern = Pattern.compile(pollHeadRegex);
			Matcher matcher = pattern.matcher(line);

			if (matcher.matches()) {
				if (matcher.group(1) != null) {
					remoteBranch = matcher.group(2);
					remoteRevision = matcher.group(3);
				} else if (matcher.group(4) != null) {
					changesFound = true;
				}
			}

			if (remoteBranch != null && changesFound != null) {
				break;
			}
		}

		listener.getLogger().println("[WSO2 Maven Release] Remote branch: " + remoteBranch);
		listener.getLogger().println("[WSO2 Maven Release] Remote revision: " + remoteRevision);
		listener.getLogger().println("[WSO2 Maven Release] Changes Found? " + changesFound);

		// we are a release build
		listener.getLogger().println("[WSO2 Maven Release] Triggering a release build. Cause : " + build.getCauses());
		M2ReleaseArgumentsAction args = build.getAction(M2ReleaseArgumentsAction.class);
		args = populateMissingArguments(args, build, launcher, listener);
		/* END WSO2 changes */

		StringBuilder buildGoals = new StringBuilder();

		buildGoals.append("-DdevelopmentVersion=").append(args.getDevelopmentVersion()).append(' ');
		buildGoals.append("-DreleaseVersion=").append(args.getReleaseVersion()).append(' ');

		if (args.getScmUsername() != null) {
			buildGoals.append("-Dusername=").append(args.getScmUsername()).append(' ');
		}

		if (args.getScmCommentPrefix() != null) {
			buildGoals.append("\"-DscmCommentPrefix=");
			buildGoals.append(args.getScmCommentPrefix());
			if (args.isAppendHusonUserName()) {
				buildGoals.append(String.format("(%s)", args.getHudsonUserName()));
			}
			buildGoals.append("\" ");
		}

		if (args.getScmTagName() != null) {
			buildGoals.append("-Dtag=").append(args.getScmTagName()).append(' ');
		}

		if (args.isDryRun()) {
			buildGoals.append(getDryRunGoals());
		}
		else {
			buildGoals.append(getReleaseGoals());
		}

		listener.getLogger().println("[WSO2 Maven Release] Build Goals : " + buildGoals.toString());
		build.addAction(new M2ReleaseArgumentInterceptorAction(buildGoals.toString(), args.getScmPassword()));
		build.addAction(new M2ReleaseBadgeAction());

		return new Environment() {

			@Override
			public void buildEnvVars(Map<String, String> env) {
				if (StringUtils.isNotBlank(releaseEnvVar)) {
					// inform others that we are doing a release build
					env.put(releaseEnvVar, "true");
				}
			}

			@Override
			public boolean tearDown(@SuppressWarnings("rawtypes") AbstractBuild bld, BuildListener lstnr)
					throws IOException, InterruptedException {
				boolean retVal = true;
				
				final MavenModuleSet mmSet = getModuleSet(bld);
				M2ReleaseArgumentsAction args = bld.getAction(M2ReleaseArgumentsAction.class);
				if (args.isCloseNexusStage() && !args.isDryRun()) {
					StageClient client = new StageClient(new URL(getDescriptor().getNexusURL()), getDescriptor()
							.getNexusUser(), getDescriptor().getNexusPassword());
					try {
						MavenModule rootModule = mmSet.getRootModule();
						// TODO grab the version that we have just released...
						Stage stage = client.getOpenStageID(rootModule.getModuleName().groupId,
								rootModule.getModuleName().artifactId, args.getReleaseVersion());
						if (stage != null) {
							if (bld.getResult() != null && bld.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
								lstnr.getLogger().println("[M2Release] Closing repository " + stage);
								client.closeStage(stage, args.getRepoDescription());
								lstnr.getLogger().println("[M2Release] Closed staging repository.");
							}
							else {
								lstnr.getLogger().println("[M2Release] Dropping repository " + stage);
								client.dropStage(stage);
								lstnr.getLogger().println("[M2Release] Dropped staging repository.");
							}
						}
						else {
							retVal = false;
							lstnr.fatalError("[M2Release] Could not find nexus stage repository for project.\n");
						}
					}
					catch (StageException ex) {
						lstnr.fatalError("[M2Release] Could not close repository , %1$s\n", ex.getMessage());
						ex.printStackTrace(lstnr.getLogger());
						log.error("[M2Release] Could not close repository", ex);
						retVal = false;
					}
				}
				
				if (args.isDryRun()) {
					lstnr.getLogger().println("[M2Release] its only a dryRun, no need to mark it for keep");
				}
				int buildsKept = 0;
				if (bld.getResult() != null && bld.getResult().isBetterOrEqualTo(Result.SUCCESS) && !args.isDryRun()) {
					if (numberOfReleaseBuildsToKeep > 0 || numberOfReleaseBuildsToKeep == -1) {
						// keep this build.
						lstnr.getLogger().println("[M2Release] assigning keep build to current build.");
						bld.keepLog();
						buildsKept++;
					}

					// the value may have changed since a previous release so go searching...
					log.debug("looking for extra release builds to lock/unlock.");
					for (Run run : (RunList<? extends Run>) (bld.getProject().getBuilds())) {
						log.debug("checking build #{}", run.getNumber());
						if (isSuccessfulReleaseBuild(run)) {
							log.debug("build #{} was successful.", run.getNumber());
							if (bld.getNumber() != run.getNumber()) { // not sure we still need this check..
								if (shouldKeepBuildNumber(numberOfReleaseBuildsToKeep, buildsKept)) {
									buildsKept++;
									if (!run.isKeepLog()) {
										lstnr.getLogger().println(
												"[M2Release] assigning keep build to build " + run.getNumber());
										run.keepLog(true);
									}
								}
								else {
									if (run.isKeepLog()) {
										lstnr.getLogger().println(
												"[M2Release] removing keep build from build " + run.getNumber());
										run.keepLog(false);
									}
								}
							}
						}
						else {
							log.debug("build #{} was NOT successful release build.", run.getNumber());
						}
					}
				}

				return retVal;
			}

			/**
			 * evaluate if the specified build is a successful release build (not including dry runs)
			 * @param run the run to check
			 * @return <code>true</code> if this is a successful release build that is not a dry run.
			 */
			private boolean isSuccessfulReleaseBuild(Run run) {
				M2ReleaseBadgeAction a = run.getAction(M2ReleaseBadgeAction.class);
				if (a != null && !run.isBuilding() && run.getResult().isBetterOrEqualTo(Result.SUCCESS) && !a.isDryRun()) {
					return true;
				}
				return false;
			}
			
			private boolean shouldKeepBuildNumber(int numToKeep, int numKept) {
				if (numToKeep == -1) {
					return true;
				}
				return numKept < numToKeep;
			}
		};
	}

	public static final String DEFAULT_SCM_TAG_SUFFIX = "[WSO2 Release] ";

	/**
	 * If the release build is triggered via a time trigger, the arguments will
	 * not be populated.
	 *
	 * @param build
	 * @param arguments
	 * @param launcher
	 * @param listener
	 */
	private M2ReleaseArgumentsAction populateMissingArguments(M2ReleaseArgumentsAction arguments, AbstractBuild build,
			Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		if (arguments == null) {
			arguments = new M2ReleaseArgumentsAction();
			build.addAction(arguments);
		}

		String nextDevelopmentVersion = arguments.getDevelopmentVersion();
		if (nextDevelopmentVersion != null && !nextDevelopmentVersion.isEmpty()) {
			return arguments;
		}

		MavenModuleSet mms = getModuleSet(build);
//		MavenModule rootModule = mms.getRootModule();

		String rootPomVersion = null;
//		if (rootModule != null) {
//			rootPomVersion = rootModule.getVersion();
//		} else {
			try {
				listener.getLogger().println("[M2Release] Root Module information not found. "
						+ "Calculating the version by processing the root pom.xml");
				FilePath path = new FilePath(build.getWorkspace(), "pom.xml");
				rootPomVersion = path.act(new PomVersionReader());
			} catch (IOException e) {
				listener.getLogger().println("[M2Release] Error reading version from pom.xml. " + e.getMessage());
				e.printStackTrace(listener.getLogger());
				throw e;
			} catch (InterruptedException e) {
				listener.getLogger().println("[M2Release] Error reading version from pom.xml. " + e.getMessage());
				e.printStackTrace(listener.getLogger());
				throw e;
			}
//		}

		M2ReleaseAction m2ReleaseAction = new M2ReleaseAction(mms, false, false, false);
		nextDevelopmentVersion = m2ReleaseAction.computeNextVersion(rootPomVersion);
		String releaseVersion = m2ReleaseAction.computeReleaseVersion(rootPomVersion);
		String scmTag = m2ReleaseAction.computeScmTag(rootPomVersion);

		String scmCommentPrefix = DEFAULT_SCM_TAG_SUFFIX + "[Jenkins #" + build.getId() + "] ";

		//todo do version number Validations - the version format and already existing tags

		if (m2ReleaseAction.isNexusSupportEnabled()) {
			String nexusStagingDescription = m2ReleaseAction.computeRepoDescription(build, releaseVersion, scmTag);
			arguments.setCloseNexusStage(m2ReleaseAction.isNexusSupportEnabled());
			arguments.setRepoDescription(nexusStagingDescription);

			//todo release nexus staging config
		}

		listener.getLogger().println("[M2Release] Release Metadata: ");
		listener.getLogger().println("[M2Release]  Current POM Version: " + rootPomVersion);
		listener.getLogger().println("[M2Release]  Release Version: " + releaseVersion);
		listener.getLogger().println("[M2Release]  SCM Tag Name: " + scmTag);
		listener.getLogger().println("[M2Release]  Next Development Version: " + nextDevelopmentVersion);
		listener.getLogger().println(
				"[M2Release]  Close Nexus Staging? " + m2ReleaseAction.isNexusSupportEnabled()); //global config

		arguments.setReleaseVersion(releaseVersion);
		arguments.setDevelopmentVersion(nextDevelopmentVersion);
		// TODO - re-implement versions on specific modules.

		//arguments.setScmUsername(scmUsername);
		//arguments.setScmPassword(scmPassword);
		arguments.setScmTagName(scmTag);
		arguments.setScmCommentPrefix(scmCommentPrefix);
		arguments.setAppendHusonUserName(false);
		arguments.setHudsonUserName(Hudson.getAuthentication().getName());

		return arguments;
	}

	/**
	 * Manually read the pom version if the Jenkins rootModule is not available.
	 * This reads the pom file transparent from the actual remote slave.
	 */
	private static class PomVersionReader implements FilePath.FileCallable<String> {
		private static final long serialVersionUID = 1L;

		/**
		 * Manually read the pom version if the Jenkins rootModule is not available.
		 */
		public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
			String version = "NaN";
			try {
				if (file.isFile()) {
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					DocumentBuilder db = dbf.newDocumentBuilder();
					Document doc = db.parse(file);

					XPath xPath = XPathFactory.newInstance().newXPath();
					version = (String) xPath
							.evaluate("/project/version/text()", doc.getDocumentElement(), XPathConstants.STRING);

					if (version == null || version.isEmpty()) {
						version = (String) xPath.evaluate("/project/parent/version/text()", doc.getDocumentElement(),
								XPathConstants.STRING);
					}
					return version;

				}
			} catch (ParserConfigurationException e) {
				throw new IOException(e.getMessage(), e);
			} catch (SAXException e) {
				throw new IOException(e.getMessage(), e);
			} catch (XPathExpressionException e) {
				throw new IOException(e.getMessage(), e);
			}
			return version;
		}

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
	
	public int getNumberOfReleaseBuildsToKeep() {
		return numberOfReleaseBuildsToKeep;
	}
	
	public void setNumberOfReleaseBuildsToKeep(int numberOfReleaseBuildsToKeep) {
		this.numberOfReleaseBuildsToKeep = numberOfReleaseBuildsToKeep;
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
	 * Evaluate if the current build should be a release build.
	 * @return <code>true</code> if this build is a release build.
	 */
	private boolean isReleaseBuild(@SuppressWarnings("rawtypes") AbstractBuild build) {
		return (build.getCause(ReleaseCause.class) != null);
	}

	/**
	 * Releases should happen nightly
	 *
	 * @param build
	 * @return
	 */
	private boolean isTriggeredByTimer(AbstractBuild build) {
		return (build.getCause(TimerTrigger.TimerTriggerCause.class) != null);
	}

	/**
	 * Releases should happen by git push
	 *
	 * @param build
	 * @return
	 */
	private boolean isTriggeredByGitPush(AbstractBuild build) {
		for(Object cause : build.getCauses()) {
			boolean matches = GITHUB_PUSH_CAUSE.equals(cause.getClass().getName());
			if (matches) {
				return true;
			}
		}

		return false;
	}

	/** Recreate the logger on de-serialisation. */
	private Object readResolve() {
		log = LoggerFactory.getLogger(M2ReleaseBuildWrapper.class);
		return this;
	}

	@Override
	public Action getProjectAction(@SuppressWarnings("rawtypes") AbstractProject job) {
		return new M2ReleaseAction((MavenModuleSet) job, selectCustomScmCommentPrefix, selectAppendHudsonUsername, selectScmCredentials);
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
		
		public static final Permission CREATE_RELEASE;

		static {
			Permission tmpPerm = null;
			try {
				// Jenkins changed the security model in a non backward compatible way :-(
				// JENKINS-10661
				Class<?> permissionScopeClass = Class.forName("hudson.security.PermissionScope");
				Object psArr = Array.newInstance(permissionScopeClass, 2);
				Field f;
				f = permissionScopeClass.getDeclaredField("JENKINS");
				Array.set(psArr, 0, f.get(null));
				f = permissionScopeClass.getDeclaredField("ITEM");
				Array.set(psArr, 1, f.get(null));
				
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
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).info("Using new style Permission with PermissionScope");

			}
			// all these exceptions are Jenkins < 1.421 or Hudson
			// wouldn't multicatch be nice!
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
				LoggerFactory.getLogger(M2ReleaseBuildWrapper.class).warn("Using Legacy Permission as new style permission with PermissionScope failed");
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

		public static final int        DEFAULT_NUMBER_OF_RELEASE_BUILDS_TO_KEEP = 1;

		private boolean nexusSupport  = false;
		private String  nexusURL      = null;
		private String  nexusUser     = "deployment";                                    //$NON-NLS-1$
		private String  nexusPassword = "deployment123";                                 //$NON-NLS-1$
		


		public DescriptorImpl() {
			super(M2ReleaseBuildWrapper.class);
			load();
			if (nexusURL != null && !nexusURL.endsWith("/")) {
				nexusURL = nexusURL + "/";
			}
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
				if (nexusURL != null && !nexusURL.endsWith("/")) { //$NON-NLS-1$
					nexusURL = nexusURL + "/";
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
			if (!urlValue.endsWith("/")) {
				testURL = urlValue + "/";
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
