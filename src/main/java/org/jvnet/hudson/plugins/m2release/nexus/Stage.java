/*
 * The MIT License
 * 
 * Copyright (c) 2010, NDS Group Ltd., James Nord
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
package org.jvnet.hudson.plugins.m2release.nexus;

/**
 * A holder class to contain information about a Nexus Professional Staging repository.
 */
public class Stage {

	private String profileID;
	private String stageID;


	/**
	 * Construct a new Stage to represent a Nexus Professional Staging repository.
	 * 
	 * @param profileID the id of the staging profile that this stage is associated with.
	 * @param stageID the id for this stage repository.
	 */
	public Stage(String profileID, String stageID) {
		super();
		this.profileID = profileID;
		this.stageID = stageID;
	}


	/**
	 * @return the profileID that this stage is associated with.
	 */
	public String getProfileID() {
		return profileID;
	}


	/**
	 * Return the StageID that this stage represents. StageIDs are recycled by Nexus, so this is only valid for
	 * the lifetime of this stage repository.
	 * 
	 * @return the unique stageID for this stage.
	 */
	public String getStageID() {
		return stageID;
	}


	@Override
	public String toString() {
		return String.format("Stage[profileId=%s, stageId=%s]", profileID, stageID);
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((profileID == null) ? 0 : profileID.hashCode());
		result = prime * result + ((stageID == null) ? 0 : stageID.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Stage other = (Stage) obj;
		if (profileID == null) {
			if (other.profileID != null)
				return false;
		}
		else if (!profileID.equals(other.profileID))
			return false;
		if (stageID == null) {
			if (other.stageID != null)
				return false;
		}
		else if (!stageID.equals(other.stageID))
			return false;
		return true;
	}

}
