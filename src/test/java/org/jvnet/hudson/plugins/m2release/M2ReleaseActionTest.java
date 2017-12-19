/*
 * The MIT License
 * 
 * Copyright (c) 2011, Dominik Bartholdi
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

import hudson.maven.MavenUtil;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.tasks.Maven.MavenInstallation;

import org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper.DescriptorImpl;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

public class M2ReleaseActionTest extends HudsonTestCase {

	public void testPrepareRelease_dryRun_m3() throws Exception {
		MavenInstallation mavenInstallation = configureMaven3();
		final MavenModuleSetBuild build = this.runPepareRelease_dryRun("maven3-project.zip", "maven3-project/pom.xml", mavenInstallation);
		assertTrue("should have been run with maven 3", MavenUtil.maven3orLater(build.getMavenVersionUsed()));
	}

	public void testPrepareRelease_dryRun_m2project_with_m3() throws Exception {
		MavenInstallation mavenInstallation = configureMaven3();
		final MavenModuleSetBuild build = this.runPepareRelease_dryRun("maven2-project.zip", "pom.xml", mavenInstallation);
		assertTrue("should have been run with maven 3", MavenUtil.maven3orLater(build.getMavenVersionUsed()));
	}

	public void testPrepareRelease_dryRun_m2project_with_m2() throws Exception {
		MavenInstallation mavenInstallation = configureDefaultMaven();
		final MavenModuleSetBuild build = this.runPepareRelease_dryRun("maven2-project.zip", "pom.xml", mavenInstallation);
		assertFalse("should have been run with maven 2", MavenUtil.maven3orLater(build.getMavenVersionUsed()));
	}

	public MavenModuleSetBuild runPepareRelease_dryRun(String projectZip, String unpackedPom, MavenInstallation mavenInstallation) throws Exception {
		MavenModuleSet m = createMavenProject();
		m.setRootPOM(unpackedPom);
		m.setMaven(mavenInstallation.getName());
		m.setScm(new ExtractResourceSCM(getClass().getResource(projectZip)));
		m.setGoals("dummygoal"); // build would fail with this goal

		final M2ReleaseBuildWrapper wrapper = new M2ReleaseBuildWrapper(DescriptorImpl.DEFAULT_RELEASE_GOALS, DescriptorImpl.DEFAULT_DRYRUN_GOALS, false,
				false, false, "ENV", "USERENV", "PWDENV", DescriptorImpl.DEFAULT_NUMBER_OF_RELEASE_BUILDS_TO_KEEP,false);
		M2ReleaseArgumentsAction args = new M2ReleaseArgumentsAction();
		args.setDevelopmentVersion("1.0-SNAPSHOT");
		args.setReleaseVersion("0.9");
		args.setDryRun(true);
		m.getBuildWrappersList().add(wrapper);
		
		return assertBuildStatusSuccess(m.scheduleBuild2(0, new ReleaseCause(), args));
	}

}
