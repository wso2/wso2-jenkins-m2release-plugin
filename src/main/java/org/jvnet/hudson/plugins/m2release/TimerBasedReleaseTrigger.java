/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.jvnet.hudson.plugins.m2release;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.Cause;
import hudson.triggers.TimerTrigger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This class holds the configuration of periodical release and creating a cron job
 */
public class TimerBasedReleaseTrigger extends TimerTrigger {
    @DataBoundConstructor
    public TimerBasedReleaseTrigger(String spec) throws ANTLRException {
        super(spec);
    }

    @Override
    public void run() {
        job.scheduleBuild(0, new TimerBasedReleaseTriggerCause());
    }

    @Extension
    public static class DescriptorImpl extends TimerTrigger.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Release periodically";
        }
    }

    public static class TimerBasedReleaseTriggerCause extends Cause {
        @Override
        public String getShortDescription() {
            return "Release Trigger Cause";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TimerBasedReleaseTriggerCause;
        }

        @Override
        public int hashCode() {
            return 20;
        }
    }

}