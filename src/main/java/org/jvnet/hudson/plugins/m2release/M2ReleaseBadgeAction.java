/*
 * The MIT License
 * 
 * Copyright (c) 2010, Domi
 * Copyright (c) 2010, James Nord
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

import hudson.model.BuildBadgeAction;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.RunAction2;

/**
 * The M2ReleaseBadgeAction displays a small icon next to any release build in the build history.
 * 
 * <p>
 * This object also remembers the release in a machine readable form so that
 * other plugins can introspect that the release has happened.
 * 
 * @author domi
 * @author teilo
 */
public class M2ReleaseBadgeAction implements BuildBadgeAction, RunAction2 {

	private transient Run<?, ?> run;

	@Deprecated
	private transient String tooltipText; // kept for backwards compatibility (very old versions of plugin)

	@Deprecated
	private transient Boolean isDryRun; // kept for backwards compatibility

	/**
	 * Version number that was released.
	 */
	@Deprecated
	private transient String versionNumber; // kept for backwards compatibility

	/**
	 * Construct a new BadgeIcon to a Maven release build.
	 */
	public M2ReleaseBadgeAction() {
	}

	public Object readResolve() {
		// try to recover versionNumber from tooltipText (for builds by very old versions of the plugin)
		if (versionNumber == null && tooltipText != null && tooltipText.startsWith("Release - ")) {
			versionNumber = tooltipText.substring("Release - ".length());
		}
		return this;
	}

	/**
	 * Gets the string to be displayed.
	 * 
	 * @return <code>null</code> as we don't display any text to the user.
	 */
	public String getDisplayName() {
		return null;
	}

	/**
	 * Gets the file name of the icon.
	 * 
	 * @return <code>null</code> as badges icons are rendered by the jelly.
	 */
	public String getIconFileName() {
		return null;
	}

	/**
	 * Gets the URL path name.
	 * 
	 * @return <code>null</code> as this action object doesn't need to be bound to web.
	 */
	public String getUrlName() {
		return null;
	}

	/**
	 * Gets the tooltip text that should be displayed to the user.
	 */
	public String getTooltipText() {
		StringBuilder str = new StringBuilder();

		if (isFailedBuild()) {
			str.append("Failed release");
		} else {
			str.append("Release");
		}
		if (isDryRun()) {
			str.append(" (dryRun)");
		}
		str.append(" - ");
		str.append(getVersionNumber());

		return str.toString();
	}

	/**
	 * Gets the version number that was released.
	 */
	public String getVersionNumber() {
		if (versionNumber != null) {
			return versionNumber;
		}
		M2ReleaseArgumentsAction args = run.getAction(M2ReleaseArgumentsAction.class);
		if (args != null) {
			return args.getReleaseVersion();
		} else { // builds by old versions of the plugin
			return null;
		}
	}

	/**
	 * Returns if the release was a dryRun or not.
	 */
	public boolean isDryRun() {
		if (isDryRun != null) {
			return isDryRun;
		}
		M2ReleaseArgumentsAction args = run.getAction(M2ReleaseArgumentsAction.class);
		if (args != null) {
			return args.isDryRun();
		} else {  // builds by old versions of the plugin
			return false; // we don't know
		}
	}

	/**
	 * Returns <code>true</code> if the release build job failed.
	 */
	public boolean isFailedBuild() {
		return !isSuccessfulBuild(run);
	}

	private boolean isSuccessfulBuild(Run<?, ?> run) {
		Result result = run.getResult();
		if (result != null) {
			return result.isBetterOrEqualTo(Result.SUCCESS);
		} else { // build is not yet initiated
			return true;
		}
	}

	public void onAttached(Run<?, ?> run) {
		this.run = run;
	}

	public void onLoad(Run<?, ?> run) {
		this.run = run;
	}
}
