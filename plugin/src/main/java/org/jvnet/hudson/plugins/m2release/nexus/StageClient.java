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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The Stage client acts as the interface to Nexus Pro staging via the Nexus REST APIs.
 * 
 * @author James Nord
 * @version 0.5
 */
public class StageClient {

	private Logger log = LoggerFactory.getLogger(StageClient.class);

	private URL nexusURL;
	private String username;
	private String password;

	/**
	 * Create a new StageClient to handle communicating to a Nexus Pro server Staging suite.
	 * @param nexusURL the base URL for the Nexus server.
	 * @param username user name to use with staging privileges.
	 * @param password password for the user.
	 */
	public StageClient(URL nexusURL, String username, String password) {
		this.nexusURL = nexusURL;
		this.username = username;
		this.password = password;
	}

	/**
	 * Get the ID for the Staging repository that holds the specified GAV.
	 * 
	 * @param groupId
	 *        groupID to search for.
	 * @param artifactId
	 *        artifactID to search for.
	 * @param version
	 *        version of the group/artifact to search for <em>[currently ignored]</em>.
	 * @return the stageID or null if no machine stage was found.
	 * @throws StageException if any issue occurred whilst locting the open stage.
	 */
	public Stage getOpenStageID(String group, String artifact, String version) throws StageException {
		List<Stage> stages = getOpenStageIDs();
		Stage stage = null;
		for (Stage testStage : stages) {
			if (checkStageForGAV(testStage, group, artifact, version)) {
				if (stage == null) {
					stage = testStage;
				}
				else {
					// multiple stages match!!!
					log.warn("Found a matching stage ({}) for {}:{} but already found a matchine one ({})", new Object[] {testStage, group, artifact, stage});
				}
			}
		}
		return stage;
	}

	/**
	 * Close the specified stage.
	 * 
	 * @param stage
	 *        the stage to close.
	 * @throws StageException
	 *         if any issue occurred whilst closing the stage.
	 */
	public void closeStage(Stage stage, String description) throws StageException {
		performStageAction(StageAction.CLOSE, stage, description);
	}

	/**
	 * Drop the stage from Nexus staging.
	 * 
	 * @param stage
	 *        the Stage to drop.
	 * @throws StageException
	 *         if any issue occurred whilst dropping the stage.
	 */
	public void dropStage(Stage stage) throws StageException {
		performStageAction(StageAction.DROP, stage, null);
	}

	/**
	 * Promote the stage from Nexus staging into the default repository for the stage.
	 * 
	 * @param stage
	 *        the Stage to promote.
	 * @throws StageException
	 *         if any issue occurred whilst promoting the stage.
	 */
	public void promoteStage(Stage stage) throws StageException {
		throw new UnsupportedOperationException("not implemented");
		// need to get the first repo target id for the stage...
		// performStageAction(StageAction.PROMOTE, stage, null);
	}

	/**
	 * Check if we have the required permissions for nexus staging.
	 * 
	 * @return
	 * @throws StageException if an exception occurred whilst checking the authorisation.
	 */
	public void checkAuthentication() throws StageException {
		try {
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
				String[] requiredPerms = new String[] {"nexus:stagingprofiles", "nexus:stagingfinish",
				// "nexus:stagingpromote",
				    "nexus:stagingdrop"};

				XPath xpath = XPathFactory.newInstance().newXPath();
				for (String perm : requiredPerms) {
					String expression = "//clientPermissions/permissions/permission[id=\"" + perm + "\"]/value";
					Node node = (Node) xpath.evaluate(expression, doc, XPathConstants.NODE);
					if (node == null) {
						throw new StageException("Invalid reponse from server - is the URL a Nexus Professional server?");
					}
					int val = Integer.parseInt(node.getTextContent());
					if (val == 0) {
						throw new StageException("User has insufficient privaledges to perform staging actions (" + perm
						                         + ")");
					}
				}
			}
			else {
				drainOutput(conn);
				if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
					throw new IOException("Incorrect username / password supplied.");
				}
				else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
					throw new IOException("Service not found - is this a Nexus server?");
				}
				else {
					throw new IOException("Server returned error code " + status + ".");
				}
			}
		}
		catch (IOException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
		catch (XPathException ex) {
			throw new StageException(ex);
		}
		catch (ParserConfigurationException ex) {
			throw new StageException(ex);
		}
		catch (SAXException ex) {
			throw new StageException(ex);
		}
	}


	public List<Stage> getOpenStageIDs() throws StageException {
		try {
			List<Stage> openStages = new ArrayList<Stage>();
			URL url = new URL(nexusURL.toString() + "/service/local/staging/profiles");

			Document doc = getDocument(url);

			String profileExpression = "//stagingProfile/id";
			XPath xpathProfile = XPathFactory.newInstance().newXPath();
			NodeList profileNodes = (NodeList) xpathProfile.evaluate(profileExpression, doc, XPathConstants.NODESET);
			for (int i = 0; i < profileNodes.getLength(); i++) {
				Node profileNode = profileNodes.item(i);
				String profileID = profileNode.getTextContent();

				String statgeExpression = "../stagingRepositoryIds/string";
				XPath xpathStage = XPathFactory.newInstance().newXPath();
				NodeList stageNodes = (NodeList) xpathStage.evaluate(statgeExpression, profileNode,
				                                                     XPathConstants.NODESET);
				for (int j = 0; j < stageNodes.getLength(); j++) {
					Node stageNode = stageNodes.item(j);
					// XXX need to also get the stage profile
					openStages.add(new Stage(profileID, stageNode.getTextContent()));
				}
			}
			return openStages;
		}
		catch (IOException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
		catch (XPathException ex) {
			throw new StageException(ex);
		}
	}

	public boolean checkStageForGAV(Stage stage, String group, String artifact, String version)
	    throws StageException {
		// do we always know the version???
		// to browse an open repo
		// /service/local/repositories/${stageID}/content/...
		// the stage repos are not listed via a call to
		// /service/local/repositories/ but are in existence!
		boolean found = false;
		try {
			URL url;
			if (version == null) {
				url = new URL(nexusURL.toString() + "/service/local/repositories/" + stage.getStageID() + "/content/"
				    + group.replace('.', '/') + '/' + artifact + "/?isLocal");
			}
			else {
				url = new URL(nexusURL.toString() + "/service/local/repositories/" + stage.getStageID() + "/content/"
				    + group.replace('.', '/') + '/' + artifact + '/' + version + "/?isLocal");
			}
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			addAuthHeader(conn);
			conn.setRequestMethod("HEAD");
			int response = conn.getResponseCode();
			if (response == HttpURLConnection.HTTP_OK) {
				// we found our baby - may be a different version but we don't
				// always have that to hand (if Maven did the auto numbering)
				found = true;
			}
			else if (response == HttpURLConnection.HTTP_NOT_FOUND) {
				// not this repo
			}
			else {
				log.warn("Server returned HTTP status {} when we only expected a 200 or 404.", Integer.toString(response));
			}
			conn.disconnect();
		}
		catch (IOException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
		return found;
	}


	private Document getDocument(URL url) throws StageException {
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			addAuthHeader(conn);
			int status = conn.getResponseCode();
			if (status == 200) {
				DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				Document doc = builder.parse(conn.getInputStream());
				conn.disconnect();
				return doc;
			}
			else {
				drainOutput(conn);
				if (status == 401) {
					throw new IOException("Incorrect Crediantials for " + url.toString());
				}
				else {
					throw new IOException("Server returned error code " + status + " for " + url.toString());
				}
			}
		}
		catch (IOException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
		catch (ParserConfigurationException ex) {
			throw new StageException(ex);
		}
		catch (SAXException ex) {
			throw new StageException(ex);
		}

	}

	/**
	 * Construct the XML message for a promoteRequest.
	 * 
	 * @param stage
	 *        The stage to target
	 * @param description
	 *        the description (used for promote - ignored otherwise)
	 * @return The XML for the promoteRequest.
	 */
	private String createPromoteRequestPayload(Stage stage, String description) {
		// TODO? this is missing the targetRepoID which is needed for promote...
		return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><promoteRequest><data><stagedRepositoryId>%s</stagedRepositoryId><description>ignored</description></data></promoteRequest>",
		                     stage.getStageID());
	}

	
	/**
	 * Perform a staging action.
	 * @param action the action to perform.
	 * @param stage the stage on which to perform the action.
	 * @param description description to pass to the server for the action (e.g. the description of the stage repo).
	 * @throws StageException if an exception occurs whilst performing the action.
	 */
	private void performStageAction(StageAction action, Stage stage, String description) throws StageException {
		try {
			URL url = action.getURL(nexusURL, stage);
			String payload = createPromoteRequestPayload(stage, description);

			byte[] payloadBytes = payload.getBytes("UTF-8");
			int contentLen = payloadBytes.length;

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			addAuthHeader(conn);
			conn.setRequestProperty("Content-Length", Integer.toString(contentLen));
			conn.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
			conn.setRequestProperty("Accept", "application/xml");

			conn.setRequestMethod("POST");
			conn.setDoOutput(true);

			OutputStream out = conn.getOutputStream();
			out.write(payloadBytes);
			out.flush();

			int status = conn.getResponseCode();
			log.debug("Server returned HTTP Status {} for {} stage request to {}.", new Object[] { Integer.toString(status),
			    action.name(), stage });

			if (status == HttpURLConnection.HTTP_CREATED) {
				drainOutput(conn);
				conn.disconnect();
			}
			else {
				log.warn("Server returned HTTP Status {} for {} stage request to {}.", 
				         new Object[] { Integer.toString(status), action.name(), stage });
				drainOutput(conn);
				conn.disconnect();
				throw new IOException(String.format("server responded with status:%s", Integer.toString(status)));
			}
		}
		catch (IOException ex) {
			String message = String.format("Failed to perform %s action to nexus stage(%s)", action.name(), stage.toString());
			throw new StageException(message, ex);
		}
	}

	/**
	 * Add the BASIC Authentication header to the HTTP connection.
	 * @param conn the HTTP URL Connection
	 */
	private void addAuthHeader(HttpURLConnection conn) {
		// java.net.Authenticator is brain damaged as it is global and no way to delegate for just one server...
		try {
			String auth = username + ":" + password;
			// there is a lot of debate about password and non ISO-8859-1 characters...
			// see https://bugzilla.mozilla.org/show_bug.cgi?id=41489
			// Base64 adds a trailing newline - just strip it as whitespace is illegal in Bsae64
			String encodedAuth = new Base64().encodeToString(auth.getBytes("ISO-8859-1")).trim();
			conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
			log.debug("Encoded Authentication is: "+encodedAuth);
		}
		catch (UnsupportedEncodingException ex) {
			String msg = "JVM does not conform to java specification.  Mandatory CharSet ISO-8859-1 is not available.";
			log.error(msg);
			throw new RuntimeException(msg, ex);
		}
	}


	private StageException createStageExceptionForIOException(URL url, IOException ex) {
		if (ex instanceof StageException) {
			return (StageException)ex;
		}
		if (ex.getMessage().equals(url.toString())) {
			// Sun JRE (and probably others too) often return just the URL in the error.
			return new StageException("Unable to connect to " + url, ex);
		}
		else {
			return new StageException(ex.getMessage(), ex);
		}
	}
	
	private void drainOutput(HttpURLConnection conn) throws IOException {
		// for things like unauthorised (401) we won't have any content and getting the inputStream will 
		// cause an IOException as we are in error - but there is no really way to tell this so check the 
		// length instead.
		if (conn.getContentLength() > 0) {
			if (conn.getErrorStream() != null) {
				IOUtils.skip(conn.getErrorStream(), conn.getContentLength());
			}
			else {
				IOUtils.skip(conn.getInputStream(), conn.getContentLength());
			}
		}
	}
}
