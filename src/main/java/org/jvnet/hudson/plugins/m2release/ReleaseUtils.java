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

import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.util.TextFile;

import java.io.File;
import java.io.IOException;

public class ReleaseUtils {

    public static MavenModuleSet getModuleSet(AbstractBuild<?,?> build) {
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

    /**
     * Show a info log in the Jenkins build job's console log
     *
     */
    public static void printInfoIntoBuildLog(String message, TaskListener listener) {
        listener.getLogger().println("[WSO2 Maven Release] " + message);
    }

    public static void printSeparator(TaskListener listener) {
        listener.getLogger().println("------------------------------------------------------------------------");
        listener.getLogger().println("");
    }

    public static void printExceptionIntoBuildLog(String customMessage, Exception exception, TaskListener taskListener) {
        taskListener.getLogger().println(customMessage + exception.getMessage());
        exception.printStackTrace(taskListener.getLogger());
    }

    /**
     * Get the file that stores the last release revision for this job.
     */
    public static TextFile getLastReleaseRevisionNumberFile(AbstractProject project) {
        return new TextFile(new File(project.getRootDir(), Constants.LAST_RELEASE_REVISION_NUMBER));
    }

    public static String getLastReleaseRevisionNumber(AbstractProject project) throws IOException {
        return getLastReleaseRevisionNumberFile(project).read();
    }
}
