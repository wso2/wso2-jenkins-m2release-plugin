package org.jvnet.hudson.plugins.m2release.nexus;

import java.net.URL;

import junit.framework.Assert;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.core.Is.is;

public class StageTest {

	// TODO start an embedded server instead to server these files so we also
	// test http auth access?
	private static final URL NEXUS_URL;

	static {
		NEXUS_URL = StageTest.class.getResource("stageTest/");
		/*
		try { 
			// NEXUS_URL = new URL("http://localhost:8081/nexus"); //
			NEXUS_URL = new URL("http://192.168.1.65:8081/nexus");
		} catch (java.net.MalformedURLException e) {
			throw new RuntimeException("Impossible Condition", e);
		}
		*/
	}

	@Test
	@Ignore("requres test setup")
	public void testSpecificStage() throws Exception {
		Assume.assumeThat(NEXUS_URL.getProtocol(), is("file"));

		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");

		// group and artifact don't exist
		Stage stage = client.getOpenStageID("invalid", "bogus", "1.2.3-4");
		Assert.assertNull("Stage returned but we should not have one", stage);

		// group and artifact exist but at different version
		stage = client.getOpenStageID("com.test.testone", "test", "1.0.2");
		Assert.assertNull("Stage returned but we should not have one", stage);

		// full gav match
		stage = client.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertEquals("Incorrect stage returned", "test-005",
				stage.getStageID());

		// match group and artifact for any version
		stage = client.getOpenStageID("com.test.testone", "test", null);
		Assert.assertEquals("Incorrect stage returned", "test-005",
				stage.getStageID());
	}

	@Test
	@Ignore("requres test setup")
	public void testCloseStage() throws Exception {
		Assume.assumeThat(NEXUS_URL.getProtocol(), is("http"));
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		Stage stage = client
				.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertNotNull("Stage is null", stage);
		client.closeStage(stage, "Test stage closing from StageClient");
	}

	@Test
	@Ignore("requres test setup")
	public void testDropStage() throws Exception {
		Assume.assumeThat(NEXUS_URL.getProtocol(), is("http"));
		StageClient client = new StageClient(NEXUS_URL, "admin", "admin123");
		Stage stage = client
				.getOpenStageID("com.test.testone", "test", "1.0.0");
		Assert.assertNotNull("Stage is null", stage);
		client.dropStage(stage);
	}

}
