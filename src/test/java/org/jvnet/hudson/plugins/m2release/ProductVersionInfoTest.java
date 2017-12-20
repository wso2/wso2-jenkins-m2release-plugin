package org.jvnet.hudson.plugins.m2release;

import org.apache.maven.shared.release.versions.VersionParseException;
import org.junit.Test;

public class ProductVersionInfoTest {

    @Test(expected = VersionParseException.class)
    public void exception_0Test() throws VersionParseException {
            new ProductVersionInfo("2.2.2-update-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_1Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-update0-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_2Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-update01-SNAPSHOT");
    }

    @Test(expected = VersionParseException.class)
    public void exception_3Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-update01");
    }

    @Test(expected = VersionParseException.class)
    public void exception_4Test() throws VersionParseException {
        new ProductVersionInfo("2.2.2-SNAPSHOT");
    }
}
