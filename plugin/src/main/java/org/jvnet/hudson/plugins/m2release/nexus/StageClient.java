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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The Stage client acts as the interface to Nexus Pro staging via the Nexus REST APIs.
 * 
 * @author James Nord
 * @version 0.5
 */
public class StageClient {

	private URL nexusURL;
	private String username;
	private String password;
	
	public StageClient(URL nexusURL, String username, String password) {
		this.nexusURL = nexusURL;
		this.username = username;
		this.password = password;
	}
	
	public void testConnection() throws Exception {
		// test the connection and authorisation.
	}
	
	/**
	 * Get the ID for the Staging repository that holds the specified GAV.
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @return
	 */
	public String getOpenStageID(String groupId, String artifactId, String version) {
		// to get stage URLs:
		// ${nexusURL}/service/local/staging/profiles
		// to check contents:
		
		return null;
	}
	
	/**
	 * Close the stage id with the specified ID.
	 * @param stageID
	 */
	public void closeStage(String stageID) {
		// url to close is ${nexusURL}/service/local/staging/profiles/${stageID}/finish
	}
	
	/**
	 * Drop the stage with the specified ID.
	 * @param stageID
	 */
	public void dropStage(String stageID) {
		//  ${nexusURL}/service/local/staging/profiles/${stageID}/drop
	}
	
	/**
	 * Gets an authentication token from Nexus.
	 * @return
	 */
	private String authenticate() {
		return null;
	}
	
	public List<String> getOpenStageIDs() throws IOException, XPathExpressionException {
		List<String> openStages = new ArrayList<String>();
		URL url = new URL(nexusURL.toString() + "/service/local/staging/profiles");
		String expression = "/stagingProfiles/data/stagingProfile/stagingRepositoryIds/string/text()";
		//String expression = "string";
		XPath xpath = XPathFactory.newInstance().newXPath();
		InputSource inputSource = new InputSource(url.toString());
		NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
		for (int i=0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			openStages.add(node.getNodeValue());
		}
		
		return openStages;
	}
	
	public boolean checkStageForGAV(String stageID, String group, String artifact, String version) {
		// do we always know the version???
		// to browse an open repo  /service/local/repositories/${stageID}/content/...
		return false;
	}
	
}
