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

import hudson.model.Result;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import jenkins.model.PeepholePermalink;

/**
 * Resolves to the last release.
 *
 * @author Kohsuke Kawaguchi
 */
public class LastReleasePermalink extends PeepholePermalink {
    public static final Permalink INSTANCE = new LastReleasePermalink();

    @Override
    public String getDisplayName() {
        return "Last Release";
    }

    @Override
    public String getId() {
        return "lastRelease";
    }

   @Override
   public boolean apply(Run<?, ?> run) {
      boolean retVal = false;
      M2ReleaseBadgeAction a = run.getAction(M2ReleaseBadgeAction.class);
      if (a != null) {
          if (!run.isBuilding()) {
              if (!a.isDryRun() && run.getResult() == Result.SUCCESS) {
                  retVal = true;
              }
          }
      }
      return retVal;
    }
}
