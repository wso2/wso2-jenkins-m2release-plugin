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
public class M2ReleaseBadgeAction implements BuildBadgeAction {

	@Deprecated
	private transient String tooltipText; // kept for backwards compatibility
	
	private boolean isDryRun;

	/**
	 * Version number that was released.
	 */
	private String versionNumber;
	
	private boolean failedBuild;

	/**
	 * Construct a new BadgeIcon to a Maven release build. The build is set as successful.
	 */
	public M2ReleaseBadgeAction(String versionNumber, boolean isDryRun) {
		this.versionNumber = versionNumber;
		this.isDryRun = isDryRun;
		this.failedBuild = false;
	}

	public Object readResolve() {
		// try to recover versionNumber from tooltipText (for builds by old versions of the plugin)
		if (versionNumber == null && tooltipText.startsWith("Release - ")) {
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
	 * 
	 * @return Can be <code>null</code> if we are dealing with very legacy data
	 *         that doesn't contain this information.
	 */
	public String getVersionNumber() {
		return versionNumber;
	}

	public boolean isDryRun() {
		return isDryRun;
	}

	/**
	 * Marks the build as failed.
	 */
	public void setFailedBuild(boolean isFailedBuild) {
        this.failedBuild = isFailedBuild;
    }

    public boolean isFailedBuild() {
        return failedBuild;
    }
}
