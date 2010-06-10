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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
	 * Check if we have the required permissions for nexus staging.
	 * 
	 * @return
	 * @throws IOException 
	 * @throws XPathExpressionException 
	 * @throws ParserConfigurationException 
	 * @throws SAXException 
	 */
	public void checkAuthentication() throws IOException, XPathExpressionException, ParserConfigurationException, SAXException {
		// ${nexusURL}/service/local/status
		URL url = new URL(nexusURL.toString() + "/service/local/status");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		addAuthHeader(conn);
		int status = conn.getResponseCode();
		if (status == 200) {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(conn.getInputStream());
			/*
			 * check for the following permissions:
			 */
			String[] requiredPerms = new String[] { "nexus:stagingprofiles",
			                                        "nexus:stagingfinish",
			                                        "nexus:stagingpromote",
			                                        "nexus:stagingdrop" };		
			
			XPath xpath = XPathFactory.newInstance().newXPath();
			for (String perm : requiredPerms) {
				String expression = "//clientPermissions/permissions/permission[id=\""+perm+"\"]/value";
				Node node = (Node) xpath.evaluate(expression, doc, XPathConstants.NODE);
				if (node == null) {
					throw new IOException("Invalid reponse from server - is the URL a nexus server?");
				}
				int val = Integer.parseInt(node.getTextContent());
				if (val == 0) {
					throw new IOException("user has insufficient privs");
				}
			}
		}
		else if (status == 401) {
			throw new IOException("Incorrect Crediantials for " + url.toString());
		}
		else {
			throw new IOException("Server returned error code " + status + " for " + url.toString());
		}
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
