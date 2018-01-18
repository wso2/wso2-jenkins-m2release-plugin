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

import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper.DescriptorImpl;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.IOException;

public class M2ReleaseBadgeActionTest extends HudsonTestCase {

	public void testBadgeForSuccessfulDryRunRelease() throws Exception {
		MavenInstallation mavenInstallation = configureDefaultMaven();
		final MavenModuleSetBuild build =
				runDryRunRelease("maven2-project.zip", "pom.xml", mavenInstallation, Result.SUCCESS);
		M2ReleaseBadgeAction badge = build.getAction(M2ReleaseBadgeAction.class);
		assertTrue("Badge is not marked as dryRun", badge.isDryRun());
		assertFalse("Badge should not have been marked as failed release", badge.isFailedBuild());
		assertEquals("1.0", badge.getVersionNumber());
	}

	public void testBadgeForFailedDryRunRelease() throws Exception {
		MavenInstallation mavenInstallation = configureMaven3();
		final MavenModuleSetBuild build =
				runDryRunRelease("maven3-failing-project.zip", "pom.xml", mavenInstallation, Result.FAILURE);
		M2ReleaseBadgeAction badge = build.getAction(M2ReleaseBadgeAction.class);
		assertTrue("Badge is not marked as dryRun", badge.isDryRun());
		assertTrue("Badge should have been marked as failed release", badge.isFailedBuild());
		assertEquals("1.0", badge.getVersionNumber());
	}

	public void testBadgeForFailedPostBuildStepRelease() throws Exception {
		MavenInstallation mavenInstallation = configureMaven3();
		final MavenModuleSetBuild build =
				runDryRunReleaseWithFailingPostStep("maven3-failing-project.zip", "pom.xml", mavenInstallation, Result.FAILURE);
		M2ReleaseBadgeAction badge = build.getAction(M2ReleaseBadgeAction.class);
		assertTrue("Badge should have been marked as failed release", badge.isFailedBuild());
        assertEquals("1.0", badge.getVersionNumber());
	}

	private MavenModuleSetBuild runDryRunRelease(String projectZip, String unpackedPom,
					MavenInstallation mavenInstallation, Result expectedResult)
			throws Exception {
		return runDryRunRelease(projectZip, unpackedPom, mavenInstallation, expectedResult, null);
	}

	private MavenModuleSetBuild runDryRunReleaseWithFailingPostStep(String projectZip, String unpackedPom,
					MavenInstallation mavenInstallation, Result expectedResult)
			throws Exception {
		Builder failingPostStep = new FailingBuilder();
		return runDryRunRelease(projectZip, unpackedPom, mavenInstallation, expectedResult, failingPostStep);
	}

	private MavenModuleSetBuild runDryRunRelease(String projectZip, String unpackedPom,
					MavenInstallation mavenInstallation, Result expectedResult, Builder postStepBuilder)
			throws Exception {
		MavenModuleSet m = createMavenProject();
		m.setRootPOM(unpackedPom);
		m.setMaven(mavenInstallation.getName());
		m.setScm(new ExtractResourceSCM(getClass().getResource(projectZip)));
		m.setGoals("dummygoal"); // non-dryRun build would fail with this goal

		final M2ReleaseBuildWrapper wrapper =
				new M2ReleaseBuildWrapper(DescriptorImpl.DEFAULT_RELEASE_GOALS, DescriptorImpl.DEFAULT_DRYRUN_GOALS,
						false, false, false, "ENV", "USERENV", "PWDENV",
						DescriptorImpl.DEFAULT_NUMBER_OF_RELEASE_BUILDS_TO_KEEP, false, false);

		M2ReleaseArgumentsAction args = new M2ReleaseArgumentsAction();
		args.setReleaseVersion("1.0");
		args.setDevelopmentVersion("1.1-SNAPSHOT");
		args.setDryRun(true);
		m.getBuildWrappersList().add(wrapper);
		
		if (postStepBuilder != null) {
			m.getPostbuilders().add(postStepBuilder);
		}
		
		return assertBuildStatus(expectedResult, m.scheduleBuild2(0, new ReleaseCause(), args).get());
	}
	
	private static class FailingBuilder extends Builder {
		@Override
		public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
				throws InterruptedException, IOException {
			return false; // failing build
		}
	}
}