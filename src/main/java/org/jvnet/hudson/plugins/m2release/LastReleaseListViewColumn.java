/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.Extension;
import hudson.maven.AbstractMavenProject;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ListViewColumn} that shows the last released version and date.
 *
 * @author Kohsuke Kawaguchi
 */
public class LastReleaseListViewColumn extends ListViewColumn {
    @DataBoundConstructor
    public LastReleaseListViewColumn() {
    }

    /**
     * Finds the last release information of the given project.
     */
    public Info getLastReleaseInfoOf(AbstractMavenProject<?,?> project) {
        Run<?,?> r = LastReleasePermalink.INSTANCE.resolve(project);
        if (r!=null)
            return new Info((AbstractBuild)r);
        return null;
    }
    
    public static class Info {
        public final AbstractBuild build;
        public final M2ReleaseBadgeAction action;

        Info(AbstractBuild build) {
            this.build = build;
            this.action = build.getAction(M2ReleaseBadgeAction.class);
            assert action!=null;
        }
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        @Override
        public String getDisplayName() {
            return "Last Release Info from the M2 Release plugin";
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }
    }
}
