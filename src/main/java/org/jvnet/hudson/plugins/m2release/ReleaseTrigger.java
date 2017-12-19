/*
 * The MIT License
 *
 * Copyright (c) 2009, NDS Group Ltd., James Nord, CloudBees, Inc.
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
