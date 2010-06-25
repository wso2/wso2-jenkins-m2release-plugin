package org.jvnet.hudson.plugins.m2release.nexus;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;
@Ignore("requires infrastructure.")
public class StageTest {

	// TODO start an embedded server instead to server these files so we also test http auth access?
	private static final URL NEXUS_URL;
	
	static {
		try {
	    //NEXUS_URL = new URL("http://localhost:8081/nexus");
			NEXUS_URL = new URL("http://192.168.1.65:8081/nexus");
	    //NEXUS_URL = StageTest.class.getResource("stageTest");
    }
    catch (MalformedURLException e) {
    	throw new RuntimeException("Impossible Condition", e);
    }
	}
	
	
	@Test
	public void testValidAuth() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		client.checkAuthentication();
	}

	@Test(expected=Exception.class)
	public void testInvalidAuth() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "bob", "jones");
		client.checkAuthentication();
	}

	
	@Test
	public void testStage() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		List<Stage> stages = client.getOpenStageIDs();
		Assert.assertEquals("incorrect number of stages returned", 2, stages.size());
		Assert.assertEquals("Incorrect stage returned", "3e1e1bad64f", stages.get(0).getProfileID());
		Assert.assertEquals("Incorrect stage returned", "test-001", stages.get(0).getStageID());
		Assert.assertEquals("Incorrect stage returned", "3e1e1bad64f", stages.get(1).getProfileID());
		Assert.assertEquals("Incorrect stage returned", "test-005", stages.get(1).getStageID());
	}
	
	@Test
	public void testSpecificStage() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		
		// group and artifact don't exist
		Stage stage = client.getOpenStageID("invalid", "bogus", "1.2.3-4");
		Assert.assertNull("Stage returned but we should not have one", stage);
		
		// group and artifact exist but at different version
		stage = client.getOpenStageID("com.test.testone", "test", "1.0.2");
		Assert.assertNull("Stage returned but we should not have one", stage);
		
		// full gav match
		stage = client.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertEquals("Incorrect stage returned", "test-005", stage.getStageID());
		
		// match group and artifact for any version
		stage = client.getOpenStageID("com.test.testone", "test", null);
		Assert.assertEquals("Incorrect stage returned", "test-005", stage.getStageID());		
	}
	
	@Test
	public void testCloseStage() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		Stage stage = client.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertNotNull("Stage is null", stage);
		client.closeStage(stage, "Test stage closing from StageClient");		
	}

	@Test
	public void testDropStage() throws Exception {
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		Stage stage = client.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertNotNull("Stage is null", stage);
		client.dropStage(stage);
	}

}
