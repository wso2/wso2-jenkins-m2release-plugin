package org.jvnet.hudson.plugins.m2release;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.scheduler.CronTabList;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import static hudson.Util.fixNull;

public class ReleaseTrigger extends Trigger<BuildableItem> {
    @DataBoundConstructor
    public ReleaseTrigger(String spec) throws ANTLRException {
        super(spec);
    }

    @Override
    public void run() {
        job.scheduleBuild(0, new ReleaseTrigger.ReleaseTriggerCause());
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        public boolean isApplicable(Item item) {
            return item instanceof BuildableItem;
        }

        public String getDisplayName() {
            return "Release periodically";
        }

        // backward compatibility
        public FormValidation doCheck(@QueryParameter String value) {
            return doCheckSpec(value);
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheckSpec(@QueryParameter String value) {
            try {
                String msg = CronTabList.create(fixNull(value)).checkSanity();
                if(msg!=null)   return FormValidation.warning(msg);
                return FormValidation.ok();
            } catch (ANTLRException e) {
                if (value.trim().indexOf('\n')==-1 && value.contains("**"))
                    return FormValidation.error(hudson.triggers.Messages.TimerTrigger_MissingWhitespace());
                return FormValidation.error(e.getMessage());
            }
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
