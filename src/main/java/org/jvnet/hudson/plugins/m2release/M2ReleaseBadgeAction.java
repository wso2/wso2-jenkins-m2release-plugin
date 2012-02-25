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
 * The M2ReleaseBadgeAction displays a small icon next to any release builds in the build history.
 *
 * <p>
 * This object also remembers the release in a machine readable form so that
 * other plugins can introspect that the release had happened.
 * 
 * @author domi
 * @author teilo
 */
public class M2ReleaseBadgeAction implements BuildBadgeAction {

	/** The tooltip text displayed to the user with the badge. */
	private transient String tooltipText;
	
	private boolean isDryRun;

	/**
	 * Version number that was released.
	 */
	private String versionNumber;

	/**
	 * Construct a new BadgeIcon to a Maven release build.
	 * 
	 * @param tooltipText
	 *        the tool tip text that should be displayed with the badge.
	 */
	public M2ReleaseBadgeAction(String versionNumber, boolean isDryRun) {
		this.versionNumber = versionNumber;
		this.isDryRun = isDryRun;
	}

	public Object readResolve() {
		// try to recover versionNumber from tooltipText
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
	 * Gets the tool tip text that should be displayed to the user.
	 */
	public String getTooltipText() {
		return isDryRun ?  "Release (dryRun) - " + versionNumber : "Release - " + versionNumber;
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
}
