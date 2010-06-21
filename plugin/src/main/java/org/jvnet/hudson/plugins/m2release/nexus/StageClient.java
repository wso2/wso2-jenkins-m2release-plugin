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

import hudson.util.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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
import org.xml.sax.SAXException;

/**
 * The Stage client acts as the interface to Nexus Pro staging via the Nexus
 * REST APIs.
 * 
 * @author James Nord
 * @version 0.5
 */
public class StageClient {

	private Logger log = Logger.getLogger(StageClient.class.getName());

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
	 *            groupID to search for.
	 * @param artifactId
	 *            artifactID to search for.
	 * @param version
	 *            version of the group/artifact to search for
	 *            <em>[currently ignored]</em>.
	 * @return the stageID or null if no machine stage was found.
	 * @throws IOException
	 * @throws XPathExpressionException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	public Stage getOpenStageID(String group, String artifact, String version)
			throws XPathExpressionException, IOException, SAXException,
			ParserConfigurationException {
		// to get stage URLs:
		// ${nexusURL}/service/local/staging/profiles
		// to check contents:
		List<Stage> stages = getOpenStageIDs();
		Stage stage = null;
		for (Stage testStage : stages) {
			if (checkStageForGAV(testStage, group, artifact, version)) {
				if (stage == null) {
					stage = testStage;
				} else {
					// warn that multiple stages match!
					System.err.println("Found a matching stage (" + testStage
							+ ") for " + group + ':' + artifact
							+ " but already found a matching one (" + stage
							+ ").");
				}

			}
		}
		return stage;
	}

	/**
	 * Close the stage id with the specified ID.
	 * 
	 * @param stageID
	 * @throws IOException
	 */
	public void closeStage(Stage stage, String description) throws IOException {
		// url to close is
		// ${nexusURL}/service/local/staging/profiles/${stageID}/finish
		// what is the return - just a 200 OK?
		URL url = new URL(nexusURL.toString()
				+ "/service/local/staging/profiles/" + stage.getProfileID()
				+ "/finish");

		// JSON looks like
		// {"data":{"stagedRepositoryId":"abc-004","description":"close description"}}
		String payload = String
				.format(
						"<?xml version=\"1.0\" encoding=\"UTF-8\"?><promoteRequest><data><stagedRepositoryId>%s</stagedRepositoryId><description><![CDATA[%s]]></description></data></promoteRequest>",
						stage.getStageID(), description);
		byte[] payloadBytes = payload.getBytes("UTF-8");
		int contentLen = payloadBytes.length;

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		addAuthHeader(conn);
		conn.setRequestProperty("Content-Length", Integer.toString(contentLen));
		conn.setRequestProperty("Content-Type",
				"application/xml; charset=UTF-8");

		conn.setRequestMethod("POST");
		conn.setDoOutput(true);

		OutputStream out = conn.getOutputStream();
		out.write(payloadBytes);
		out.flush();

		int status = conn.getResponseCode();
		if (status == 201) {
			// everything ok.
			IOUtils.skip(conn.getInputStream(), conn.getContentLength());
		} else {
			System.err.println("Server returned HTTP Status " + status);
			throw new IOException(
					String
							.format(
									"Failed to close nexus stage(%s) server responded with status:%s",
									stage.toString(), Integer.toString(status)));
		}
		// needs content length
		// and also content-type (application/json)?
		// response is http 201 created.
	}

	/**
	 * Drop the stage with the specified ID.
	 * 
	 * @param stageID
	 */
	public void dropStage(Stage stage) {
		// ${nexusURL}/service/local/staging/profiles/${stageID}/drop
		// POST /nexus/service/local/staging/profiles/{profileID}/drop?undefined
		// payload is
		// {"data":{"stagedRepositoryId":"abc-003","description":"drop descr"}}
		// {"data":{"stagedRepositoryId":"abc-003","description":"drop descr"}}
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
	public void checkAuthentication() throws IOException,
			XPathExpressionException, ParserConfigurationException,
			SAXException {
		// ${nexusURL}/service/local/status
		URL url = new URL(nexusURL.toString() + "/service/local/status");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		addAuthHeader(conn);
		int status = conn.getResponseCode();
		if (status == 200) {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			Document doc = builder.parse(conn.getInputStream());
			/*
			 * check for the following permissions:
			 */
			String[] requiredPerms = new String[] { "nexus:stagingprofiles",
					"nexus:stagingfinish",
					// "nexus:stagingpromote",
					"nexus:stagingdrop" };

			XPath xpath = XPathFactory.newInstance().newXPath();
			for (String perm : requiredPerms) {
				String expression = "//clientPermissions/permissions/permission[id=\""
						+ perm + "\"]/value";
				Node node = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				if (node == null) {
					throw new IOException(
							"Invalid reponse from server - is the URL a nexus server?");
				}
				int val = Integer.parseInt(node.getTextContent());
				if (val == 0) {
					throw new IOException("user has insufficient privs");
				}
			}
		} else {
			// drain the output to be nice.
			IOUtils.skip(conn.getInputStream(), conn.getContentLength());
			if (status == 401) {
				throw new IOException("Incorrect Crediantials for "
						+ url.toString());
			} else {
				throw new IOException("Server returned error code " + status
						+ " for " + url.toString());
			}
		}
	}

	public List<Stage> getOpenStageIDs() throws IOException,
			XPathExpressionException, SAXException,
			ParserConfigurationException {
		List<Stage> openStages = new ArrayList<Stage>();
		URL url = new URL(nexusURL.toString()
				+ "/service/local/staging/profiles");

		Document doc = getDocument(url);

		String profileExpression = "//stagingProfile/id";
		XPath xpathProfile = XPathFactory.newInstance().newXPath();
		NodeList profileNodes = (NodeList) xpathProfile.evaluate(
				profileExpression, doc, XPathConstants.NODESET);
		for (int i = 0; i < profileNodes.getLength(); i++) {
			Node profileNode = profileNodes.item(i);
			String profileID = profileNode.getTextContent();

			String statgeExpression = "../stagingRepositoryIds/string";
			XPath xpathStage = XPathFactory.newInstance().newXPath();
			NodeList stageNodes = (NodeList) xpathStage.evaluate(
					statgeExpression, profileNode, XPathConstants.NODESET);
			for (int j = 0; j < stageNodes.getLength(); j++) {
				Node stageNode = stageNodes.item(j);
				// XXX need to also get the stage profile
				openStages
						.add(new Stage(profileID, stageNode.getTextContent()));
			}

		}
		return openStages;
	}

	@SuppressWarnings("restriction")
	private void addAuthHeader(URLConnection conn) {
		String auth = username + ":" + password;
		// this is bad - should look at using some other BASE64 encoder
		// and perhaps a different HTTPClient?
		String encodedAuth = new sun.misc.BASE64Encoder().encode(auth
				.getBytes());
		conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
	}

	public boolean checkStageForGAV(Stage stage, String group, String artifact,
			String version) throws MalformedURLException {
		// do we always know the version???
		// to browse an open repo
		// /service/local/repositories/${stageID}/content/...
		// the stage repos are not listed via a call to
		// /service/local/repositories/ but are in existence!
		boolean found = false;
		URL url;
		if (version == null) {
			url = new URL(nexusURL.toString() + "/service/local/repositories/"
					+ stage.getStageID() + "/content/"
					+ group.replace('.', '/') + '/' + artifact + "/?isLocal");
		} else {
			url = new URL(nexusURL.toString() + "/service/local/repositories/"
					+ stage.getStageID() + "/content/"
					+ group.replace('.', '/') + '/' + artifact + '/' + version
					+ "/?isLocal");
		}
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			addAuthHeader(conn);
			conn.setRequestMethod("HEAD");
			int response = conn.getResponseCode();
			if (response == HttpURLConnection.HTTP_OK) {
				// we found our baby - may be a different version but we don't
				// always have that to hand (if Maven did the auto numbering)
				found = true;
			} else if (response == HttpURLConnection.HTTP_NOT_FOUND) {
				// not this repo
			} else {
				System.err.println("Server returned HTTP Status " + response);
			}
			conn.disconnect();
		} catch (IOException ex) {
			// some debug message - but likely to happen for a 404.
		}
		return found;
	}

	private Document getDocument(URL url) throws IOException, SAXException,
			ParserConfigurationException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		addAuthHeader(conn);
		int status = conn.getResponseCode();
		if (status == 200) {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			Document doc = builder.parse(conn.getInputStream());
			return doc;
		} else {
			// drain the output to be nice.
			IOUtils.skip(conn.getInputStream(), conn.getContentLength());
			if (status == 401) {
				throw new IOException("Incorrect Crediantials for "
						+ url.toString());
			} else {
				throw new IOException("Server returned error code " + status
						+ " for " + url.toString());
			}
		}
	}
}
