package org.jvnet.hudson.plugins.m2release;

import hudson.maven.MavenUtil;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.tasks.Maven.MavenInstallation;

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

		final M2ReleaseBuildWrapper wrapper = new M2ReleaseBuildWrapper("release:prepare", false, false, false, "ENV", "USERENV", "PWDENV");
		wrapper.setDevelopmentVersion("1.0-SNAPSHOT");
		wrapper.setReleaseVersion("0.9");
		wrapper.enableRelease();
		wrapper.markAsDryRun(true);
		m.getBuildWrappersList().add(wrapper);

		return buildAndAssertSuccess(m);
	}

}
