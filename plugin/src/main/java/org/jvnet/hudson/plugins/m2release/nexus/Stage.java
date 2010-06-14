package org.jvnet.hudson.plugins.m2release.nexus;

/**
 * A holder class to contain information about a Nexus Professional Staging repository.
 */
public class Stage {
	
	private String profileID;
	private String stageID;
	
	/**
	 * Construct a new Stage to represent a Nexus Professional Staging repository.
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
   * @return the unique stageID for this stage
   */
  public String getStageID() {
  	return stageID;
  }
	
	@Override
	public String toString() {
	  return String.format("Stage[profileId=%s, stageId=%s", profileID, stageID);
	}
}
