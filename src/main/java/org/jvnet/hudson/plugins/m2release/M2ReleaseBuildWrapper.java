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

import hudson.EnvVars;
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
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.util.GitUtils;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.FormValidation;
import hudson.util.RunList;
import hudson.util.TextFile;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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

//import jenkins.triggers.SCMTriggerItem.SCMTriggerItems;

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
	public static final String LAST_RELEASE_REVISION_NUMBER = "lastReleaseRevisionNumber";
	public static final String NOT_A_NUMBER = "NaN";
	public static final String RELEASE_BUILD_ABORTED_MESSAGE = "ABORTING the release build "
			+ "and falling back to a SNAPSHOT build...";
	public static final String ERROR_READING_VERSION_FROM_POM_XML = "[M2Release] Error reading version from pom.xml. ";
	public static final String ENV_POM_VERSION = "POM_VERSION";
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

	class DefaultEnvironment extends Environment {
		/** intentionally blank */
		@Override
		public void buildEnvVars(Map<String, String> env) {
			if (StringUtils.isNotBlank(releaseEnvVar)) {
				// inform others that we are NOT doing a release build
				env.put(releaseEnvVar, "false");
			}
		}
	}

	public static final String DEFAULT_SCM_TAG_SUFFIX = "[WSO2 Release] ";
	public static final String DEFAULT_SCM_RELEASE_BRANCH_PREFIX = "release-";
	public static final String DEFAULT_REF = "HEAD";

	@Override
	public Environment setUp(@SuppressWarnings("rawtypes") final AbstractBuild build, final Launcher launcher, final BuildListener listener)
	                                                                                              throws IOException,
	                                                                                              InterruptedException {

		if (!isReleaseBuild(build) && !isTriggeredByGitPush(build)) {
			log.debug("Build trigger causes: {} ", build.getCauses());
			// we are not performing a release so don't need a custom tearDown.
			return new DefaultEnvironment();
		}
		/* START WSO2 changes */
		File pollingLog = new SCMTrigger.BuildAction(build).getPollingLogFile();
		RemoteScmInfo remoteScmInfo = getRemoteScmInfo(pollingLog);
		final String remoteBranch = remoteScmInfo.getRemoteBranch();
		final String remoteRevision = remoteScmInfo.getRemoteRevision();
		final Boolean changesFound = remoteScmInfo.getChangesFound();

		listener.getLogger().println("[WSO2 Maven Release] Remote branch: " + remoteBranch);
		listener.getLogger().println("[WSO2 Maven Release] Remote revision: " + remoteRevision);
		listener.getLogger().println("[WSO2 Maven Release] Changes found? " + changesFound);

		String lastReleaseRevision = NOT_A_NUMBER;
		try {
			lastReleaseRevision = getLastReleaseRevisionNumber(build.getProject()).trim();
		} catch (IOException e) {
			listener.getLogger().println("[WSO2 Maven Release] last release revision number not found locally.");
		}

		if (lastReleaseRevision.equalsIgnoreCase(remoteRevision)) {
			listener.getLogger().println("[WSO2 Maven Release] remote revision and last released revisions match. "
					+ "Not triggering a release...");
			printSeparator(listener);
			return new DefaultEnvironment();
		}

		// we are a release build
		listener.getLogger().println("[WSO2 Maven Release] Triggering a release build. Cause : " + build.getCauses());

		M2ReleaseArgumentsAction args = build.getAction(M2ReleaseArgumentsAction.class);
		args = populateMissingArguments(args, build, launcher, listener);

		//validate
		if (!validateRelease(build, launcher, listener, args)) {
			return new DefaultEnvironment();
		}
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

		/* START WSO2 changes */
		String releaseBranch = checkoutReleaseBranch(args, build, launcher, listener);
		/* END WSO2 changes */

		return new ReleaseEnvironment(releaseBranch, remoteBranch, remoteRevision, launcher);
	}

	private String checkoutReleaseBranch(M2ReleaseArgumentsAction args, AbstractBuild build, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		//checkout a new release branch
		int randomNumber = (int) (new Random().nextDouble() * 1000);
		String randomString = "-" + String.valueOf((char)(randomNumber % 26 + 97)) + randomNumber; // -g242
		String releaseBranch =
				DEFAULT_SCM_RELEASE_BRANCH_PREFIX + args.getReleaseVersion() + randomString;
		if (build.getProject().getScm() instanceof GitSCM) {
			GitSCM gitSCM = (GitSCM) build.getProject().getScm();
			AbstractProject project = build.getProject();

			final EnvVars environment = GitUtils.getPollEnvironment(project, build.getWorkspace(), launcher, listener);
			GitClient gitClient = gitSCM.createClient(listener, environment, build, build.getWorkspace());

			gitClient.checkoutBranch(releaseBranch, DEFAULT_REF);
			listener.getLogger().println("[WSO2 Maven Release] Checked out the branch : " + releaseBranch);
		}

		return releaseBranch;
	}



	private boolean validateRelease(AbstractBuild build, Launcher launcher, BuildListener listener,
			M2ReleaseArgumentsAction args) throws IOException, InterruptedException {
		if (args == null) {
			printSeparator(listener);
			printInfoIntoBuildLog(RELEASE_BUILD_ABORTED_MESSAGE, listener);
			printSeparator(listener);
			return false;
		}

		if (build.getProject().getScm() instanceof GitSCM) {
			final String releaseBranch = DEFAULT_SCM_RELEASE_BRANCH_PREFIX + args.getReleaseVersion();
			GitSCM gitSCM = (GitSCM) build.getProject().getScm();
			AbstractProject project = build.getProject();

			final EnvVars environment = GitUtils.getPollEnvironment(project, build.getWorkspace(), launcher, listener);
			GitClient gitClient = gitSCM.createClient(listener, environment, build, build.getWorkspace());

			Set<Branch> remoteBranches = gitClient.getRemoteBranches();
			for (Branch aRemoteBranch : remoteBranches) {
				if (aRemoteBranch.getName().equalsIgnoreCase(releaseBranch)) {
					printSeparator(listener);
					printInfoIntoBuildLog("[ERROR] Release branch " + releaseBranch +
							" already exists. " + RELEASE_BUILD_ABORTED_MESSAGE, listener);
					printSeparator(listener);
					return false;
				}
			}

			//validate the tag does not exist
			if (gitClient.tagExists(args.getScmTagName())) {
				printSeparator(listener);
				printInfoIntoBuildLog("[ERROR] Release Tag " + args.getScmTagName() +
						"already exists. " + RELEASE_BUILD_ABORTED_MESSAGE, listener);
				printSeparator(listener);
				return false;
			}
		}

		if (NOT_A_NUMBER.equalsIgnoreCase(args.getReleaseVersion())) {
			printSeparator(listener);
			printInfoIntoBuildLog("[ERROR] Version could not be inferred automatically. "
					+ "Check whether the very first build of this job is a release build.", listener);
			printInfoIntoBuildLog(RELEASE_BUILD_ABORTED_MESSAGE, listener);
			printSeparator(listener);
			return false;
		}
		return true;
	}

	public TextFile getLastReleaseRevisionNumberFile(AbstractProject project) {
		return new TextFile(new File(project.getRootDir(), LAST_RELEASE_REVISION_NUMBER));
	}

	public String getLastReleaseRevisionNumber(AbstractProject project) throws IOException {
		return getLastReleaseRevisionNumberFile(project).read();
	}


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

		String rootPomVersion = getRootPomVersion(mms, build, listener);
		if (rootPomVersion == null) {
			return null;
		}

		M2ReleaseAction m2ReleaseAction = new M2ReleaseAction(mms, false, false, false);
		nextDevelopmentVersion = m2ReleaseAction.computeNextVersion(rootPomVersion);
		String releaseVersion = m2ReleaseAction.computeReleaseVersion(rootPomVersion);
		String scmTag = m2ReleaseAction.computeScmTag(rootPomVersion);

		String scmCommentPrefix = DEFAULT_SCM_TAG_SUFFIX + "[Jenkins #" + build.getId() + "] " + "[Release " + releaseVersion + "] ";

		if (m2ReleaseAction.isNexusSupportEnabled()) {
			String nexusStagingDescription = m2ReleaseAction.computeRepoDescription(build, releaseVersion, scmTag);
			arguments.setCloseNexusStage(m2ReleaseAction.isNexusSupportEnabled());
			arguments.setRepoDescription(nexusStagingDescription);

			//todo release nexus staging config - kasung
		}

		listener.getLogger().println("[WSO2 Maven Release] Release Metadata: ");
		listener.getLogger().println("[WSO2 Maven Release]  Current POM Version: " + rootPomVersion);
		listener.getLogger().println("[WSO2 Maven Release]  Release Version: " + releaseVersion);
		listener.getLogger().println("[WSO2 Maven Release]  SCM Tag Name: " + scmTag);
		listener.getLogger().println("[WSO2 Maven Release]  Next Development Version: " + nextDevelopmentVersion);
		listener.getLogger().println("[WSO2 Maven Release]  Close Nexus Staging? " + m2ReleaseAction
				.isNexusSupportEnabled()); //global config

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

	private String getRootPomVersion(MavenModuleSet mms, AbstractBuild build, TaskListener listener) {
		String pomVersion = null;

		try {
			FilePath scmWorkspace = null;
			AbstractProject project = build.getProject();
			// if Git checkout to a sub-directory is set, consider that to infer scm workspace
			if (project.getScm() instanceof GitSCM) {
				GitSCM gitSCM = (GitSCM) project.getScm();
				EnvVars environment = build.getEnvironment(listener);

				scmWorkspace = gitSCM.getExtensions().get(RelativeTargetDirectory.class).
						getWorkingDirectory(gitSCM, project, build.getWorkspace(), environment, listener);
			}

			if (scmWorkspace == null) {
				scmWorkspace = build.getWorkspace();
			}

			pomVersion = scmWorkspace.child("pom.xml").act(new PomVersionReader());
		} catch (IOException e) {
			printExceptionIntoBuildLog(ERROR_READING_VERSION_FROM_POM_XML, e, listener);
		} catch (InterruptedException e) {
			printExceptionIntoBuildLog(ERROR_READING_VERSION_FROM_POM_XML, e, listener);
		}

		if (pomVersion == null && (mms != null && mms.getRootModule() != null)) {
			listener.getLogger().println("[M2Release] Version could not be calculated by reading the pom.xml. ");

			MavenModule rootModule = mms.getRootModule();
			pomVersion = rootModule.getVersion();
		}

		if (pomVersion == null) {
			//try to get it from environment
			listener.getLogger().println("[M2Release] Getting the pom version by reading the environment variable "
					+ ENV_POM_VERSION);
			try {
				pomVersion = build.getEnvironment(listener).get(ENV_POM_VERSION);
			} catch (IOException e) {
				printExceptionIntoBuildLog("Error reading Pom version from environment. ", e, listener);
			} catch (InterruptedException e) {
				printExceptionIntoBuildLog("Error reading Pom version from environment. ", e, listener);
			}
		}

		return pomVersion;
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
			String version = NOT_A_NUMBER;
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

	private void printInfoIntoBuildLog(String message, TaskListener listener) {
		listener.getLogger().println("[WSO2 Maven Release] " + message);
	}

	private void printSeparator(TaskListener listener) {
		listener.getLogger().println("------------------------------------------------------------------------");
		listener.getLogger().println("");
	}

	private void printExceptionIntoBuildLog(String customMessage, Exception exception, TaskListener taskListener) {
		taskListener.getLogger().println(customMessage + exception.getMessage());
		exception.printStackTrace(taskListener.getLogger());
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

	public RemoteScmInfo getRemoteScmInfo(String pollingLog) {
		Iterator it = Arrays.asList(pollingLog.split("\n")).iterator();
		return getRemoteScmInfo(it);
	}

	public RemoteScmInfo getRemoteScmInfo(File pollingLogFile) throws IOException {
		Iterator iterator = FileUtils.lineIterator(pollingLogFile);
		return getRemoteScmInfo(iterator);
	}

	private RemoteScmInfo getRemoteScmInfo(Iterator pollingLogIterator) {
		String remoteRevision = null;
		String remoteBranch = null;
		Boolean changesFound = null;
		while (pollingLogIterator.hasNext()) {
			String line = (String) pollingLogIterator.next();
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
		return new RemoteScmInfo(remoteRevision, remoteBranch, changesFound);
	}

	private class RemoteScmInfo {
		private String remoteRevision;
		private String remoteBranch;
		private Boolean changesFound;

		public RemoteScmInfo(String remoteRevision, String remoteBranch, Boolean changesFound) {
			this.remoteRevision = remoteRevision;
			this.remoteBranch = remoteBranch;
			this.changesFound = changesFound;
		}

		public String getRemoteRevision() {
			return remoteRevision;
		}

		public String getRemoteBranch() {
			return remoteBranch;
		}

		public Boolean getChangesFound() {
			return changesFound;
		}

	}

	class ReleaseEnvironment extends Environment {

		private final String releaseBranch;
		private final String remoteBranch;
		private final String remoteRevision;
		private Launcher launcher;

		public ReleaseEnvironment(String releaseBranch, String remoteBranch, String remoteRevision, Launcher launcher) {
			this.releaseBranch = releaseBranch;
			this.remoteBranch = remoteBranch;
			this.remoteRevision = remoteRevision;
			this.launcher = launcher;
		}

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

			writeLatestReleaseRevisionNumber(bld, lstnr);

			if (args.isCloseNexusStage() && !args.isDryRun() && getDescriptor().getNexusURL() != null) {

				StageClient client = new StageClient(new URL(getDescriptor().getNexusURL()), getDescriptor()
						.getNexusUser(), getDescriptor().getNexusPassword());
				try {
					MavenModule rootModule = mmSet.getRootModule();
					// TODO grab the version that we have just released...
					Stage stage = client.getOpenStageID(rootModule.getModuleName().groupId,
							rootModule.getModuleName().artifactId, args.getReleaseVersion());
					if (stage != null) {
						if (bld.getResult() != null && bld.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
							lstnr.getLogger().println("[M2Release] Closing Nexus staging repository " + stage);
							client.closeStage(stage, args.getRepoDescription());
							lstnr.getLogger().println("[M2Release] Closed Nexus staging repository.");

							/* START WSO2 changes */
							//release the nexus staging repository
							if (args.isReleaseNexusStage()) {
								lstnr.getLogger().println("[WSO2 Maven Release] Releasing Nexus repository " + stage);
								client.releaseStage(stage);
								lstnr.getLogger().println("[WSO2 Maven Release] Released Nexus repository.");
								printSeparator(lstnr);
							}

							//merge the release branch into master
							if (bld.getProject().getScm() instanceof GitSCM) {
								GitSCM gitSCM = (GitSCM) bld.getProject().getScm();
								AbstractProject project = bld.getProject();

								final EnvVars environment = GitUtils
										.getPollEnvironment(project, bld.getWorkspace(), launcher, lstnr);
								GitClient gitClient = gitSCM
										.createClient(lstnr, environment, bld, bld.getWorkspace());

								//merge release commits into master
								List<UserRemoteConfig> userRemoteConfigs = gitSCM.getUserRemoteConfigs();
								log.info("User remote Configs " + userRemoteConfigs);
								if (userRemoteConfigs.isEmpty()) {
									lstnr.fatalError("[WSO2 Maven Release] "
											+ "Could not find the git remote URL for project. \n");
								} else if (userRemoteConfigs.get(0).getCredentialsId() == null) {
									printInfoIntoBuildLog("Credentials are not present in the git configuration", lstnr);
								}

								//get release branch head commit
								String remoteUrl = userRemoteConfigs.get(0).getUrl();
								String releaseBranchHeadCommit = gitClient.revParse(DEFAULT_REF).name();

								//local branch name that will be pushed to #remoteBranch
								String localBranchToPush = UUID.randomUUID().toString();
								printInfoIntoBuildLog("Checking out a temp local branch named " + localBranchToPush +
										" at revision " + remoteRevision, lstnr);
								gitClient.checkoutBranch(localBranchToPush, remoteRevision);
								printSeparator(lstnr);
								try {
									//get latest commits from the remote branch before pushing to avoid outdated wc error
									String localFetchBranch = "refs/remotes/origin/" + localBranchToPush;
									String fetchRefspec = remoteBranch + ":" + localFetchBranch;
									printInfoIntoBuildLog("Fetching latest changes with refspec: " + fetchRefspec +
													" before merging the release commits into " + localBranchToPush,
											lstnr);

									gitClient.fetch_().from(new URIish(remoteUrl), Collections
											.singletonList(new RefSpec(fetchRefspec))).execute();
									String latestRemoteCommit = gitClient.revParse(localFetchBranch).getName();
									ObjectId latestRemoteCommitObject = ObjectId.fromString(latestRemoteCommit);
									printInfoIntoBuildLog("Merging fetched upstream changes into " + localBranchToPush,
											lstnr);
									gitClient.merge().setRevisionToMerge(latestRemoteCommitObject).execute();
									printSeparator(lstnr);
								} catch (URISyntaxException e) {
									lstnr.fatalError("[WSO2 Maven Release] "
											+ "Could not parse the git remote URL for project: " + remoteUrl);
								} catch (GitException e) {
									printExceptionIntoBuildLog("[ERROR] [WSO2 Maven Release] merging the changes. ", e,
											lstnr);
									gitClient.checkoutBranch(localBranchToPush, remoteRevision);
									//todo handle kasung
								}

								try {
									//merg release commits
									printInfoIntoBuildLog(
											"Merging release branch HEAD commit, " + releaseBranchHeadCommit + ", into "
													+ localBranchToPush, lstnr);
									gitClient.merge().
											setRevisionToMerge(ObjectId.fromString(releaseBranchHeadCommit)).execute();

									//push the whole thing into original remote branch
									String refspec = localBranchToPush + ":" + remoteBranch;
									printInfoIntoBuildLog("Pushing the whole thing into remote.", lstnr);
									gitClient.push().to(new URIish(remoteUrl)).ref(refspec).execute();

									//if no exceptions, then remove the remote release branch
									refspec = ":" + releaseBranch;
									lstnr.getLogger().println();
									printInfoIntoBuildLog("Deleting release branch from remote.", lstnr);
									gitClient.push().to(new URIish(remoteUrl)).ref(refspec).execute();

									String headCommitHashAfterMerge = writeLatestReleaseRevisionNumber(bld, lstnr);
									log.debug("[WSO2 Maven Release] {}-{} : Written the revision {} ", bld.getProject(),
											bld.getDisplayName(), headCommitHashAfterMerge);
									printInfoIntoBuildLog(
											"Stored last release commit hash : " + headCommitHashAfterMerge, lstnr);

									printSeparator(lstnr);


								} catch (URISyntaxException e) {
									lstnr.fatalError("[WSO2 Maven Release] "
											+ "Could not parse the git remote URL for project: " + remoteUrl);
								} catch (GitException e) {
									//this could fail if merging the release commits lead to a conflict.
									printExceptionIntoBuildLog("[ERROR] [WSO2 Maven Release] merging the changes. ", e,
											lstnr);
									bld.keepLog();
									//todo handle kasung
								}
							}
							/* END WSO2 changes */
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
						log.info("[WSO2 Maven Release] {} release build #{} was successful.",
								bld.getProject().getName(), run.getNumber());
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
						log.info("[WSO2 Maven Release] {} release build #{} was NOT successful.",
								bld.getProject().getName(), run.getNumber());
					}
				}
			}

			return retVal;
		}

		/**
		 * Writes the current head commit hash into jenkins-home/jobs/job-name/lastReleaseRevisionNumber
		 */
		private String writeLatestReleaseRevisionNumber(AbstractBuild bld, TaskListener lstnr) {
			try {
				//write the latest release revision number
				SCM scm = bld.getProject().getScm();
				if (scm instanceof GitSCM) {
					GitSCM gitSCM = (GitSCM) scm;
					AbstractProject project = bld.getProject();
					final EnvVars environment = GitUtils
							.getPollEnvironment(project, bld.getWorkspace(), launcher, lstnr);
					GitClient gitClient = gitSCM.createClient(lstnr, environment, bld, bld.getWorkspace());
					ObjectId objectId = gitClient.revParse(DEFAULT_REF);
					StringWriter writer = new StringWriter();
					objectId.copyTo(writer);
					String headHash = writer.toString();

					TextFile file = getLastReleaseRevisionNumberFile(project);
					file.write(headHash);
					return headHash;
				}
			} catch (IOException e) {
				StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); //todo
				lstnr.getLogger().println("[WSO2 Maven Release] Error " + e.getMessage() + " " + sw.toString());
			} catch (Throwable e) {
				StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); //todo
				lstnr.getLogger().println("[WSO2 Maven Release] Error " + e.getMessage() + " " + sw.toString());
			}

			return "-";
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

	}

}
