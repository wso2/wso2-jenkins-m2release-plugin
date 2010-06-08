package org.jvnet.hudson.plugins.m2release.nexus;

import java.net.URL;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

public class StageTest {

	@Test
	public void testValidAuth() throws Exception {
		// TODO start an embedded server instead to server these files so we also test http auth access?
		//URL testURL = StageTest.class.getResource("stageTest");
		URL testURL = new URL("http://localhost:8081/nexus");
		StageClient client = new StageClient(testURL, "admin", "admin123");
		client.checkAuthentication();
	}

	@Test(expected=Exception.class)
	public void testInvalidAuth() throws Exception {
		// TODO start an embedded server instead to server these files so we also test http auth access?
		//URL testURL = StageTest.class.getResource("stageTest");
		URL testURL = new URL("http://localhost:8081/nexus");
		StageClient client = new StageClient(testURL, "bob", "jones");
		client.checkAuthentication();
	}

	
	@Test
	public void testStage() throws Exception {
		// TODO start an embedded server instead to server these files so we also test http auth access?
		//URL testURL = StageTest.class.getResource("stageTest");
		URL testURL = new URL("http://localhost:8081/nexus");
		StageClient client = new StageClient(testURL, "admin", "admin123");
		List<String> stages = client.getOpenStageIDs();
		Assert.assertEquals("incorrect number of stages returned", 2, stages.size());
		Assert.assertEquals("Incorrect stage returned", "test-001", stages.get(0));
		Assert.assertEquals("Incorrect stage returned", "test-005", stages.get(1));
	}
}
