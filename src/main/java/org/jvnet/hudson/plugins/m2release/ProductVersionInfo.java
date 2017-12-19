package org.jvnet.hudson.plugins.m2release;

import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProductVersionInfo extends DefaultVersionInfo{

    public final static Pattern PRODUCT_VERSION_PATTERN = Pattern.compile("(.*)((([-_]update)([1-9]+))([-_]SNAPSHOT))$");


    public ProductVersionInfo(String version) throws VersionParseException {
        super(version);
        Matcher matcher = PRODUCT_VERSION_PATTERN.matcher(version);
        if (!matcher.matches()){
            throw new VersionParseException("Product version is not compatible");
        }
    }
}
