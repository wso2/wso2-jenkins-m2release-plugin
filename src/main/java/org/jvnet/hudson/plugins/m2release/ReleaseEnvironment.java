/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.jvnet.hudson.plugins.m2release;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.GitUtils;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.util.RunList;
import hudson.util.TextFile;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.hudson.plugins.m2release.nexus.Stage;
import org.jvnet.hudson.plugins.m2release.nexus.StageClient;
import org.jvnet.hudson.plugins.m2release.nexus.StageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReleaseEnvironment extends BuildWrapper.Environment {

    private transient Logger log = LoggerFactory.getLogger(ReleaseEnvironment.class);

    private M2ReleaseBuildWrapper m2ReleaseBuildWrapper;
    private final String releaseBranch;
    private final String remoteBranch;
    private final String remoteRevision;
    private Launcher launcher;

    public ReleaseEnvironment(BuildWrapper enclosing, String releaseBranch, String remoteBranch,
		    String remoteRevision, Launcher launcher) {
        enclosing.super();
        this.m2ReleaseBuildWrapper = (M2ReleaseBuildWrapper) enclosing;
        this.releaseBranch = releaseBranch;
        this.remoteBranch = remoteBranch;
        this.remoteRevision = remoteRevision;
        this.launcher = launcher;
    }

    @Override
    public void buildEnvVars(Map<String, String> env) {
        if (StringUtils.isNotBlank(m2ReleaseBuildWrapper.getReleaseEnvVar())) {
            // inform others that we are doing a release build
            env.put(m2ReleaseBuildWrapper.getReleaseEnvVar(), "true");
        }
    }
    /** Do Post-Build actions
     */@Override
    public boolean tearDown(@SuppressWarnings("rawtypes") AbstractBuild bld, BuildListener lstnr)
            throws IOException, InterruptedException {
        boolean retVal;

        M2ReleaseArgumentsAction args = bld.getAction(M2ReleaseArgumentsAction.class);

        if (args.isDryRun()) {
            lstnr.getLogger().println("[M2Release] its only a dryRun, we should not do any writable operation.");
            return true;
        }

        // 1) write latest commit hash
        writeLatestReleaseRevisionNumber(bld, lstnr);

        // 2) merge release branch into main branch and finalize the git repo
        finalizeSCMRepo(bld, lstnr);

        // 3) close and release nexus staging repo
        retVal = closeNexusStagingRepo(bld, lstnr);

        // 4) keep this build for later reference?
        keepThisBuild(bld, lstnr);

        return retVal;
    }

    private boolean closeNexusStagingRepo(AbstractBuild bld, BuildListener lstnr) {
        final MavenModuleSet mmSet = ReleaseUtils.getModuleSet(bld);
        M2ReleaseArgumentsAction args = bld.getAction(M2ReleaseArgumentsAction.class);

        String buildGoals = m2ReleaseBuildWrapper.getReleaseGoals();
        if (!isNexusReleasePerform(buildGoals)) {
            lstnr.getLogger().println("[M2Release] Not performing a Nexus release.");
            return true;
        }
        try {
            StageClient client = new StageClient(new URL(
                m2ReleaseBuildWrapper.getDescriptor().getNexusURL()),
                m2ReleaseBuildWrapper.getDescriptor().getNexusUser(),
                m2ReleaseBuildWrapper.getDescriptor().getNexusPassword());
            MavenModule rootModule = mmSet.getRootModule();
            Stage stage = client.getOpenStageID(rootModule.getModuleName().groupId,
                    rootModule.getModuleName().artifactId, args.getReleaseVersion());

            if (stage == null) {
                lstnr.fatalError("[M2Release] Could not find nexus stage repository for project.\n");
                return false;
            }
            if (bld.getResult() == null || !bld.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                lstnr.getLogger().println("[M2Release] Dropping repository " + stage
                        + ". Reason: " + bld.getResult() + " build.");
                client.dropStage(stage);
                lstnr.getLogger().println("[M2Release] Dropped staging repository.");

                return false;
            }

            //close the nexus repo.. with retrying.
            if (args.isCloseNexusStage()) {
                boolean isSuccess = false;
                for (int tries = 5; tries > 0; tries--) {
                    try {
                        lstnr.getLogger().println("[M2Release] Closing Nexus staging repository " + stage);
                        client.closeStage(stage, args.getRepoDescription());
                        lstnr.getLogger().println("[M2Release] Closed Nexus staging repository.");
                        isSuccess = true;
                        break;
                    } catch (StageException ex) {
                        lstnr.fatalError("[M2Release] Could not close repository , %1$s\n", ex.getMessage());
                        ex.printStackTrace(lstnr.getLogger());
                        log.error("[M2Release] Could not close repository " + stage, ex);

                        try { Thread.sleep(15000);} catch (InterruptedException e) { /* ignore */ }
                        lstnr.getLogger().println("Retrying...");
                    }
                }
                if (!isSuccess) {
                    return false;
                }

            }

            //release the nexus staging repository
            if (args.isReleaseNexusStage()) {
                lstnr.getLogger().println("[WSO2 Maven Release] Releasing Nexus repository " + stage);
                client.releaseStage(stage, args.getRepoDescription());
                lstnr.getLogger().println("[WSO2 Maven Release] Released Nexus repository.");
                ReleaseUtils.printSeparator(lstnr);
            }

        } catch (StageException ex) {
            lstnr.fatalError("[M2Release] Could not close/release repository , %1$s\n", ex.getMessage());
            ex.printStackTrace(lstnr.getLogger());
            log.error("[M2Release] Could not close repository ", ex);
            return false;
        } catch (MalformedURLException ex) {
            ex.printStackTrace(lstnr.getLogger());
            return false;
        }
        return true;
    }

    private void finalizeSCMRepo(AbstractBuild bld, BuildListener buildListener) throws IOException, InterruptedException {
        //merge the release branch into master
        if (bld.getProject().getScm() instanceof GitSCM) {
            GitSCM gitSCM = (GitSCM) bld.getProject().getScm();
            AbstractProject project = bld.getProject();

            final EnvVars environment = GitUtils.getPollEnvironment(project, bld.getWorkspace(), launcher, buildListener);
            GitClient gitClient = gitSCM.createClient(buildListener, environment, bld, bld.getWorkspace());
            M2ReleaseArgumentsAction args = bld.getAction(M2ReleaseArgumentsAction.class);

            List<UserRemoteConfig> userRemoteConfigs = gitSCM.getUserRemoteConfigs();
            if (userRemoteConfigs.isEmpty()) {
                buildListener.fatalError("[WSO2 Maven Release] " + "Could not find the git remote URL for the project. \n");
            } else if (userRemoteConfigs.get(0).getCredentialsId() == null) {
                ReleaseUtils.printInfoIntoBuildLog("Credentials are not present in the git configuration", buildListener.getLogger());
            }

            //get release branch head commit
            String remoteUrl = userRemoteConfigs.get(0).getUrl();
            String releaseBranchHeadCommit = gitClient.revParse(M2ReleaseBuildWrapper.DEFAULT_REF).name();
            //local branch name that will be pushed to #remoteBranch
            String localBranchToPush = UUID.randomUUID().toString();

            try {
                // 1) handle build failures
                //we delete the git tag, but keep the release branch as it is in case it needs to be reviewed later
                ReleaseUtils.printSeparator(buildListener);
                ReleaseUtils.printInfoIntoBuildLog("[WSO2 Maven Release] Build Result: " + bld.getResult(),
                        buildListener.getLogger());
                if (bld.getResult() == null || !bld.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                    String scmTag = args.getScmTagName();
                    buildListener.getLogger().println("[WSO2 Maven Release] Dropping Git Tag: " + scmTag
                            + ". Reason: " + bld.getResult() + " build.");

                    //if exceptions, then remove the remote release tag
                    String refspec = ":" + "refs/tags/" + scmTag; // :refs/tags/v4.4.10
                    buildListener.getLogger().println();
                    ReleaseUtils.printInfoIntoBuildLog("Deleting release tag from remote.", buildListener.getLogger());
                    gitClient.push().to(new URIish(remoteUrl)).ref(refspec).execute();

                    buildListener.getLogger().println("[WSO2 Maven Release] Dropped git tag - " + scmTag);
                    return;
                }
            } catch (URISyntaxException e) {
                buildListener.fatalError(
                        "[WSO2 Maven Release] " + "Could not parse the git remote URL for project: " + remoteUrl);
                bld.keepLog();
                throw new IllegalArgumentException(e.getMessage(), e);
            } catch (GitException e) {
                //this could fail if merging the release commits lead to a conflict.
                ReleaseUtils.printExceptionIntoBuildLog(
                        "[ERROR] [WSO2 Maven Release] merging the changes. ", e, buildListener);
                bld.keepLog();
                throw e;
            }

            try {
                // 2) get latest commits from remote to a local branch

                // 2.1) checkout a temporary release branch for git push to remote
                ReleaseUtils.printInfoIntoBuildLog("Checking out a temp local branch named " + localBranchToPush +
                        " at revision " + remoteRevision, buildListener.getLogger());
                gitClient.checkoutBranch(localBranchToPush, remoteRevision);
                ReleaseUtils.printSeparator(buildListener);

                String localFetchBranch = "refs/remotes/origin/" + localBranchToPush;
                String fetchRefspec = remoteBranch + ":" + localFetchBranch;
                ReleaseUtils.printInfoIntoBuildLog(
                        "Fetching latest changes with refspec: " + fetchRefspec +
                                " before merging the release commits into " + localBranchToPush,
                        buildListener.getLogger());

                // 2.2) get latest commits from the remote branch before pushing to avoid outdated wc error
                    gitClient.fetch_().from(new URIish(remoteUrl), Collections.singletonList(new RefSpec(fetchRefspec))).execute();
                String latestRemoteCommit = gitClient.revParse(localFetchBranch).getName();
                ObjectId latestRemoteCommitObject = ObjectId.fromString(latestRemoteCommit);
                ReleaseUtils.printInfoIntoBuildLog(
                        "Merging fetched upstream changes into " + localBranchToPush, buildListener.getLogger());
                gitClient.merge().setRevisionToMerge(latestRemoteCommitObject).execute();
                ReleaseUtils.printSeparator(buildListener);
            } catch (URISyntaxException e) {
                buildListener.fatalError(
                        "[WSO2 Maven Release] " + "Could not parse the git remote URL for project: " + remoteUrl);
                throw new IllegalArgumentException(e);
            } catch (GitException e) {
                ReleaseUtils.printExceptionIntoBuildLog(
                        "[ERROR] [WSO2 Maven Release] merging the changes. ", e, buildListener);
                gitClient.checkoutBranch(localBranchToPush, remoteRevision);
                //todo kasung does this work?
            }

            try {
                // 3) merge release commits into that local branch
                ReleaseUtils.printInfoIntoBuildLog(
                        "Merging release branch HEAD commit, " + releaseBranchHeadCommit + ", into branch " +
                                localBranchToPush, buildListener.getLogger());
                gitClient.merge().
                        setRevisionToMerge(ObjectId.fromString(releaseBranchHeadCommit)).execute();

                // 3.1) push the whole thing into the original remote branch
                String refspec = localBranchToPush + ":" + remoteBranch;
                ReleaseUtils.printInfoIntoBuildLog("Pushing the whole thing into remote.", buildListener.getLogger());
                gitClient.push().to(new URIish(remoteUrl)).ref(refspec).execute();

                // 3.2) if no exceptions, then remove the remote release branch
                refspec = ":" + releaseBranch;
                buildListener.getLogger().println();
                ReleaseUtils.printInfoIntoBuildLog("Deleting release branch from remote.", buildListener.getLogger());
                gitClient.push().to(new URIish(remoteUrl)).ref(refspec).execute();

                String headCommitHashAfterMerge = writeLatestReleaseRevisionNumber(bld, buildListener);
                log.debug("[WSO2 Maven Release] {}-{} : Written the revision {} ", bld.getProject(),
                        bld.getDisplayName(), headCommitHashAfterMerge);
                ReleaseUtils.printInfoIntoBuildLog("Stored last release commit hash : " + headCommitHashAfterMerge,
                        buildListener.getLogger());

                ReleaseUtils.printSeparator(buildListener);

            } catch (URISyntaxException e) {
                buildListener.fatalError(
                        "[WSO2 Maven Release] " + "Could not parse the git remote URL for project: " + remoteUrl);
                throw new IllegalArgumentException(e.getMessage(), e);
            } catch (GitException e) {
                //this could fail if merging the release commits lead to a conflict.
                ReleaseUtils.printExceptionIntoBuildLog(
                        "[ERROR] [WSO2 Maven Release] merging the changes. ", e, buildListener);
                bld.keepLog();
                throw e;
                //todo handle kasung
            }
        }
    }

    private void keepThisBuild(AbstractBuild bld, BuildListener lstnr) throws IOException {
        int buildsKept = 0;
        if (bld.getResult() != null && bld.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            if (m2ReleaseBuildWrapper.numberOfReleaseBuildsToKeep > 0 || m2ReleaseBuildWrapper.numberOfReleaseBuildsToKeep == -1) {
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
                        if (shouldKeepBuildNumber(m2ReleaseBuildWrapper.numberOfReleaseBuildsToKeep, buildsKept)) {
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
                    log.debug(
                            "[WSO2 Maven Release] {} build #{} was either a snapshot build or it was not successful.",
                            bld.getProject().getName(), run.getNumber());
                }
            }
        }
    }

    /**
     * Writes the current head commit hash into jenkins-home/jobs/job-name/lastReleaseRevisionNumber
     *
     * @return the commit hash that was written
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
                ObjectId objectId = gitClient.revParse(M2ReleaseBuildWrapper.DEFAULT_REF);
                StringWriter writer = new StringWriter();
                objectId.copyTo(writer);
                String headHash = writer.toString();

                TextFile file = ReleaseUtils.getLastReleaseRevisionNumberFile(project);
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
        if (a != null && !run.isBuilding() && run.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
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

    /**
     * Identify whether the "release:perform" goal is defined or not
     *
     * @param buildGoals
     * @return
     */
    private boolean isNexusReleasePerform(String buildGoals) {
        if (buildGoals == null) {
            return false;
        }
        return buildGoals.contains("release:perform");
    }
}
