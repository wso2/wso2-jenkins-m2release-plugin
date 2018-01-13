package org.jvnet.hudson.plugins.m2release;

import org.apache.maven.shared.release.versions.VersionParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ForkedRepoVersionInfoTest {

    private String version = "2.2.3-wso2v120-SNAPSHOT";
    private ForkedRepoVersionInfo dvi = null;

    @Before
    public void setUp() throws VersionParseException {
        dvi = new ForkedRepoVersionInfo(version);
    }

    @Test
    public void getSnapshotVersionStringTest() {
        Assert.assertEquals(version, dvi.getSnapshotVersionString());
    }

    @Test
    public void getReleaseVersionStringTest() {
        Assert.assertEquals("2.2.3-wso2v120", dvi.getReleaseVersionString());
    }

    @Test
    public void isSnapshotTest() {
        Assert.assertEquals(true, dvi.isSnapshot());
    }

    @Test
    public void getNextVersionTest() {
        Assert.assertEquals("2.2.3-wso2v121-SNAPSHOT", dvi.getNextVersion().getSnapshotVersionString());
        Assert.assertEquals("2.2.3-wso2v121", dvi.getNextVersion().getReleaseVersionString());
        Assert.assertEquals(true, dvi.getNextVersion().isSnapshot());
    }

    @Test(expected = VersionParseException.class)
    public void exception_0Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-wso2v-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_1Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-wso2v0-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_2Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-wso2v01-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_3Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-wso2v01");
    }

    @Test(expected = VersionParseException.class)
    public void exception_4Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-SNAPSHOT");
    }

}
