package org.jvnet.hudson.plugins.m2release;

import org.apache.maven.shared.release.versions.VersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForkedRepoVersionInfo implements VersionInfo {

    public final static Pattern FORKED_REPO_NEXT_DEVELOPMENT_VERSION_PATTERN = Pattern.compile("(.*)(((-wso2v)(([1-9])(\\d*)))(-SNAPSHOT))$");
    private final static int MAJOR_MINOR_PATCH_VERSION_GROUP_NUM = 1;
    private final static int SNAPSHOT_STR_GROUP_NUM = 8;
    private final static int WSO2_STR_GROUP_NUM = 4;
    private final static int FORKED_REPO_WSO2_VERSION = 5;

    private String rootVersion;
    private Matcher matcher;

    public ForkedRepoVersionInfo(String version) throws VersionParseException {
        rootVersion = version;
        matcher = FORKED_REPO_NEXT_DEVELOPMENT_VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new VersionParseException(String.format(Locale.ENGLISH, "Next Development Version (%s) is not a valid version (it must end with \"%s\")",
                    version, matcher.pattern()));
        }
    }


    public String getSnapshotVersionString() {
        // Always in Snapshot state
        return rootVersion;
    }

    public String getReleaseVersionString() {
        String majorMinorPatchVersion = matcher.group(MAJOR_MINOR_PATCH_VERSION_GROUP_NUM);
        String wso2Str = matcher.group(WSO2_STR_GROUP_NUM);
        String forkedRepoWso2Version = matcher.group(FORKED_REPO_WSO2_VERSION);

        // Increment version
        int numericalForkedRepoWso2Version = Integer.parseInt(forkedRepoWso2Version);
        numericalForkedRepoWso2Version++;

        // Building release version
        StringBuilder releaseVersion = new StringBuilder(majorMinorPatchVersion);
        releaseVersion.append(wso2Str);
        releaseVersion.append(numericalForkedRepoWso2Version);
        return releaseVersion.toString();
    }

    public VersionInfo getNextVersion() {
        String snapshotStr = matcher.group(SNAPSHOT_STR_GROUP_NUM);
        String nextReleaseVersion = this.getReleaseVersionString();
        StringBuilder nextDevelopmentVersion = new StringBuilder(nextReleaseVersion);
        nextDevelopmentVersion.append(snapshotStr);

        VersionInfo versionInfo = null;
        try {
            versionInfo = new ForkedRepoVersionInfo(nextDevelopmentVersion.toString());
        } catch (VersionParseException e) {
            e.printStackTrace();
        }
        return versionInfo;
    }

    public boolean isSnapshot() {
        //This is always true since it's validated in the Constructor
        return true;
    }

    //TODO
    public int compareTo(Object o) {
        return 0;
    }
}
