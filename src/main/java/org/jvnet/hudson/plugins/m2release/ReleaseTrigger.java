package org.jvnet.hudson.plugins.m2release;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.Cause;
import hudson.triggers.TimerTrigger;
import org.kohsuke.stapler.DataBoundConstructor;

public class ReleaseTrigger extends TimerTrigger {
    @DataBoundConstructor
    public ReleaseTrigger(String spec) throws ANTLRException {
        super(spec);
    }

    @Override
    public void run() {
        job.scheduleBuild(0, new ReleaseTrigger.ReleaseTriggerCause());
    }

    @Extension
    public static class DescriptorImpl extends TimerTrigger.DescriptorImpl{
        @Override
        public String getDisplayName() {
            return "Release periodically";
        }
    }

    public static class ReleaseTriggerCause extends Cause {
        @Override
        public String getShortDescription() {
            return "Release Trigger Cause";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ReleaseTrigger.ReleaseTriggerCause;
        }

        @Override
        public int hashCode() {
            return 6;
        }
    }

}
