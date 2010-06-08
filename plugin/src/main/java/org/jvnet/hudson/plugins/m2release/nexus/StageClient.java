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
import java.net.HttpURLConnection;
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

	/**
	 * Get the ID for the Staging repository that holds the specified GAV.
	 * 
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
	 * 
	 * @param stageID
	 */
	public void closeStage(String stageID) {
		// url to close is ${nexusURL}/service/local/staging/profiles/${stageID}/finish
		// what is the return - just a 200 OK?
	}

	/**
	 * Drop the stage with the specified ID.
	 * 
	 * @param stageID
	 */
	public void dropStage(String stageID) {
		// ${nexusURL}/service/local/staging/profiles/${stageID}/drop
		// what is the return - just a 200 OK?
	}

	/**
	 * Gets an authentication token from Nexus.
	 * 
	 * @return
	 * @throws IOException 
	 * @throws XPathExpressionException 
	 */
	public String checkAuthentication() throws IOException, XPathExpressionException {
		// ${nexusURL}/service/local/status
		URL url = new URL(nexusURL.toString() + "/service/local/status");
		URLConnection conn = url.openConnection();	
		//addAuthHeader(conn);
		InputSource inputSource = new InputSource(conn.getInputStream());
		/*
		 * check for the following permissions:
		 * nexus:stagingprofiles
		 * nexus:stagingfinish
		 * nexus:stagingpromote
		 * nexus:stagingdrop
		 */
		boolean profilesPerm, finishPerm, closePerm, dropPerm;
		
		XPath xpath = XPathFactory.newInstance().newXPath();
		String expression = "//clientPermissions/permissions/permission";
		NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			// permission has id and value.
			// check if id is one listed above then value != 0
			NodeList innerNode = node.getChildNodes();
			for (int j = 0; j < innerNode.getLength(); j++) {
				System.out.println(innerNode.item(j).getTextContent().trim());
			}
		}
		return null;
	}

	public List<String> getOpenStageIDs() throws IOException, XPathExpressionException {
		List<String> openStages = new ArrayList<String>();
		URL url = new URL(nexusURL.toString() + "/service/local/staging/profiles");
		String expression = "//stagingRepositoryIds/string/text()";
		// String expression = "string";
		XPath xpath = XPathFactory.newInstance().newXPath();
		URLConnection conn = url.openConnection();	
		addAuthHeader(conn);
		InputSource inputSource = new InputSource(conn.getInputStream());
		NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			openStages.add(node.getNodeValue());
		}
		return openStages;
	}

	@SuppressWarnings("restriction")
  private void addAuthHeader(URLConnection conn) {
		 String auth = username + ":" + password;
		 // this is bad - should look at using some other BASE64 encoder
		 // and perhaps a different HTTPClient?
		 String encodedAuth = new sun.misc.BASE64Encoder().encode (auth.getBytes());
		 conn.setRequestProperty ("Authorization", "Basic " + encodedAuth);
	}
	
	public boolean checkStageForGAV(String stageID, String group, String artifact, String version)
	    throws MalformedURLException {
		// do we always know the version???
		// to browse an open repo /service/local/repositories/${stageID}/content/...
		boolean found = false;
		URL url = new URL(nexusURL.toString() + "/service/local/repositories/" + stageID + group.replace('.', '/') + '/');
		try {
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			addAuthHeader(conn);
			conn.getResponseCode();
			url.getContent();
			found = true;
		}
		catch (IOException ex) {
			// some debug message - but likely to happen for a 404.
		}
		return found;
	}

}
