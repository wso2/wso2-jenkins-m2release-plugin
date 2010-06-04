package org.jvnet.hudson.plugins.m2release.nexus;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import junit.framework.Assert;

import org.junit.Test;

public class StageTest {

	@Test
	public void testStage() throws XPathExpressionException, IOException {
		URL testURL = StageTest.class.getResource("stageTest");
		StageClient client = new StageClient(testURL, null, null);
		List<String> stages = client.getOpenStageIDs();
		Assert.assertEquals("incorrect number of stages returned", 2, stages.size());
		Assert.assertEquals("Incorrect stage returned", "test-001", stages.get(0));
		Assert.assertEquals("Incorrect stage returned", "test-005", stages.get(1));
	}
}
