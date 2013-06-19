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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The Stage client acts as the interface to Nexus Pro staging via the Nexus REST APIs. A single StageClient
 * is not thread safe.
 * 
 * @author James Nord
 * @version 0.5
 */
public class StageClient {

	private Logger log = LoggerFactory.getLogger(StageClient.class);

	/** XPath instance for running xpath queries */
	private XPath xpath;

	/** The base URL of the Nexus service. */
	private URL nexusURL;

	/** The username passed to Nexus for authentication. */
	private String username;

	/** The password passed to Nexus for authentication. */
	private String password;

	private transient String nexusVersion; 

	/**
	 * Create a new StageClient to handle communicating to a Nexus Pro server Staging suite.
	 * 
	 * @param nexusURL the base URL for the Nexus server.
	 * @param username user name to use with staging privileges.
	 * @param password password for the user.
	 */
	public StageClient(URL nexusURL, String username, String password) {
		this.nexusURL = nexusURL;
		this.username = username;
		this.password = password;
		// XPathFactory is not thread safe.
		XPathFactory factory;
		synchronized (XPathFactory.class) {
			factory = XPathFactory.newInstance();
		}
		synchronized (factory) {
			xpath = factory.newXPath();
		}
	}


	/**
	 * Get the ID for the Staging repository that holds the specified GAV.
	 * 
	 * @param groupId groupID to search for.
	 * @param artifactId artifactID to search for.
	 * @param version version of the group/artifact to search for - may be <code>null</code>.
	 * @return the stageID or null if no machine stage was found.
	 * @throws StageException if any issue occurred whilst locating the open stage.
	 */
	public Stage getOpenStageID(String group, String artifact, String version) throws StageException {
		log.debug("Looking for stage repo for {}:{}:{}", new Object[] {group, artifact, version});
		List<Stage> stages = getOpenStageIDs();
		Stage stage = null;
		for (Stage testStage : stages) {
			if (checkStageForGAV(testStage, group, artifact, version)) {
				if (stage == null) {
					stage = testStage;
					log.debug("Found stage repo {} for {}:{}:{}", new Object[] {stage, group, artifact, version});
				}
				else {
					// multiple stages match!!!
					log.warn("Found a matching stage ({}) for {}:{} but already found a matchine one ({})",
					         new Object[] {testStage, group, artifact, stage});
				}
			}
		}
		return stage;
	}


	/**
	 * Close the specified stage.
	 * 
	 * @param stage the stage to close.
	 * @throws StageException if any issue occurred whilst closing the stage.
	 */
	public void closeStage(Stage stage, String description) throws StageException {
		performStageAction(StageAction.CLOSE, stage, description);
		if (isAsyncClose()) {
			waitForActionToComplete(stage);
			// check the action completed successfully and no rules failed.
			URL url = getActivityURL(stage);
			Document doc = getDocument(url);
			// last stagingActivity that was a close
			String xpathExpr = "(/list/stagingActivity[name='close'])[last()]";
			Node lastCloseNode = (Node) evaluateXPath(xpathExpr, doc, XPathConstants.NODE);
			if (lastCloseNode == null) {
				throw new StageException("Stage activity completed but no close action was recorded!");
			}
			Node closed =
			      (Node) evaluateXPath("events/stagingActivityEvent[name='repositoryClosed']", lastCloseNode,
			                           XPathConstants.NODE);
			if (closed != null) {
				// we have successfully closed the repository
				return;
			}
			Node failed =
			      (Node) evaluateXPath("events/stagingActivityEvent[name='repositoryCloseFailed']",
			                           lastCloseNode, XPathConstants.NODE);
			if (failed == null) {
				throw new StageException(
				                         "Close stage action was signalled as completed, but was not recorded as either failed or succeeded!");
			}
			StringBuilder failureMessage =
			      new StringBuilder("Closing stage ").append(stage.getStageID()).append(" failed.\n");
			String cause =
			      (String) evaluateXPath("properties/stagingProperty[name='cause']/value", failed,
			                             XPathConstants.STRING);
			failureMessage.append('\t').append(cause);
			NodeList failedRules =
			      (NodeList) evaluateXPath("events/stagingActivityEvent[name='ruleFailed']/properties/stagingProperty[name='failureMessage']/value",
			                               lastCloseNode, XPathConstants.NODESET);
			for (int i = 0; i < failedRules.getLength(); i++) {
				failureMessage.append("\n\t");
				failureMessage.append(failedRules.item(i).getTextContent());
			}
			throw new StageException(failureMessage.toString());
		}
	}


	/**
	 * Drop the stage from Nexus staging.
	 * 
	 * @param stage the Stage to drop.
	 * @throws StageException if any issue occurred whilst dropping the stage.
	 */
	public void dropStage(Stage stage) throws StageException {
		performStageAction(StageAction.DROP, stage, null);
		// no need to wait for this to complete as there is no way to tell!
	}


	/**
	 * Release the stage from Nexus staging into the default repository for the stage. This does not drop stage
	 * repository after a successful release.
	 * 
	 * @param stage the Stage to promote.
	 * @throws StageException if any issue occurred whilst promoting the stage.
	 */
	public void releaseStage(Stage stage) throws StageException {
		performStageAction(StageAction.RELEASE, stage, null);
		if (isAsyncClose()) {
			waitForActionToComplete(stage);
		}
	}


	/**
	 * Promote the stage from Nexus staging into the specified profile.
	 * 
	 * @param stage the Stage to promote.
	 * @throws StageException if any issue occurred whilst promoting the stage.
	 */
	public void promoteStage(Stage stage) throws StageException {
		throw new UnsupportedOperationException("not implemented");
		// need to get the first repo target id for the stage...
		// performStageAction(StageAction.PROMOTE, stage, null);
	}


	/**
	 * Completion of the stage action is asynchronous - so poll until the action completed.
	 * 
	 * @param stage the stage to wait until the previous action is completed.
	 * @throws StageException
	 */
	protected void waitForActionToComplete(Stage stage) throws StageException {
		log.debug("Waiting for {} to finish transitioning.", stage);
		int i = 0;
		boolean transitioning = false;
		try {
			final URL activityUrl = getRepositoryURL(stage);
			do {
				Document doc = getDocument(activityUrl);
				String status =
				      (String) evaluateXPath("/stagingProfileRepository/transitioning", doc,
				                             XPathConstants.STRING);
				transitioning = Boolean.valueOf(status).booleanValue();
				if (transitioning) {
					i++;
					Thread.sleep(500L);
					if (i % 100 == 0) {
						log.debug("Still waiting for {} to finish transitioning.", stage);
					}
					// TODO should we ever time out?
				}
			} while (transitioning);
		}
		catch (InterruptedException ex) {
			throw new StageException(ex);
		}

	}


	/**
	 * Check if we have the required permissions for nexus staging.
	 * 
	 * @return
	 * @throws StageException if an exception occurred whilst checking the authorisation.
	 */
	public void checkAuthentication() throws StageException {
		try {
			URL url = new URL(nexusURL, "service/local/status");
			Document doc = getDocument(url);

			/*
			 * check for the following permissions:
			 */
			String[] requiredPerms =
			      new String[] {"nexus:stagingprofiles", "nexus:stagingfinish", "nexus:stagingprofilerepos",
			                    "nexus:stagingpromote", "nexus:stagingdrop"};

			for (String perm : requiredPerms) {
				String expression = "//clientPermissions/permissions/permission[id=\"" + perm + "\"]/value";
				Node node = (Node) evaluateXPath(expression, doc, XPathConstants.NODE);
				if (node == null) {
					throw new StageException(
					                         "Invalid reponse from server - is the URL a Nexus Professional server?");
				}
				int val = Integer.parseInt(node.getTextContent());
				if (val == 0) {
					throw new StageException("User has insufficient privileges to perform staging actions ("
					                         + perm + ")");
				}
			}
		}
		catch (MalformedURLException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
	}


	/**
	 * Retrieve the Nexus servers version.
	 * 
	 * @return the String representation of the server version.
	 * @throws StageException if we could not obtain the nexus server version.
	 */
	protected String getServerVersion() throws StageException {
		if (nexusVersion == null) {
			try {
				URL url = new URL(nexusURL, "service/local/status");
				Document doc = getDocument(url);
				Node node = (Node) evaluateXPath("//version", doc, XPathConstants.NODE);
				if (node == null) {
					throw new StageException(
					                         "Invalid reponse from server - is the URL a Nexus Professional server?");
				}
				nexusVersion = node.getTextContent();
				log.debug("This nexus server has version: {}", nexusVersion);
				return nexusVersion;
			}
			catch (MalformedURLException ex) {
				throw createStageExceptionForIOException(nexusURL, ex);
			}
		}
		return nexusVersion;
	}

	/**
	 * Checks if this Nexus server uses asynchronous stage actions.
	 * 
	 * @param version the version of this server
	 * @return true if this server uses asynchronous stage actions (i.e. the server is 2.4 or newer).
	 * @throws StageException if we could not retreive the server version.
	 */
	protected boolean isAsyncClose() throws StageException {
		String version = getServerVersion();
		return isAsyncClose(version);
	}

	/**
	 * Checks if this Nexus server uses asynchronous stage actions.
	 * 
	 * @param version the version of this server
	 * @return true if this server uses asynchronous stage actions (i.e. the server is 2.4 or newer).
	 */
	protected boolean isAsyncClose(String version) {
		String[] versionArr = version.split("\\.");
		if (Integer.parseInt(versionArr[0]) > 2) {
			return true;
		}
		if (Integer.parseInt(versionArr[0]) == 2) {
			if (Integer.parseInt(versionArr[1]) >= 4) {
				return true;
			}
		}
		return false;
	}


	public List<Stage> getOpenStageIDs() throws StageException {
		log.debug("retreiving list of stages");
		try {
			URL url = new URL(nexusURL, "service/local/staging/profile_repositories");
			Document doc = getDocument(url);
			return getOpenStageIDs(doc);
		}
		catch (MalformedURLException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
	}


	/**
	 * Parses a stagingRepositories element to obtain the list of open stages.
	 * 
	 * @param doc the stagingRepositories to parse.
	 * @return a List of open stages.
	 * @throws XPathException if the XPath expression is invalid.
	 */
	protected List<Stage> getOpenStageIDs(Document doc) throws StageException {
		List<Stage> stages = new ArrayList<Stage>();

		NodeList stageRepositories =
		      (NodeList) evaluateXPath("//stagingProfileRepository", doc, XPathConstants.NODESET);
		for (int i = 0; i < stageRepositories.getLength(); i++) {

			Node stageRepo = stageRepositories.item(i);

			Node type = (Node) evaluateXPath("./type", stageRepo, XPathConstants.NODE);
			// type will be "open" or "closed"
			if ("open".equals(type.getTextContent())) {
				Node profileId = (Node) evaluateXPath("./profileId", stageRepo, XPathConstants.NODE);
				Node repoId = (Node) evaluateXPath("./repositoryId", stageRepo, XPathConstants.NODE);

				stages.add(new Stage(profileId.getTextContent(), repoId.getTextContent()));
			}
		}
		return stages;
	}


	/**
	 * Evaluate the xPath expression on the given node.
	 * 
	 * @param expression the expression to evaluate.
	 * @param node the node on which the xpath should be performed.
	 * @param type the return type of the expression.
	 * @return the resulting object from the xpath query
	 * @throws StageException If <code>expression</code> cannot be evaluated.
	 */
	private Object evaluateXPath(String expression, Node node, QName type) throws StageException {
		try {
			xpath.reset();
			return xpath.evaluate(expression, node, type);
		}
		catch (XPathExpressionException ex) {
			throw new StageException("Could not evaluate xPath expression (" + expression + ')', ex);
		}
	}


	public boolean
	      checkStageForGAV(Stage stage, String group, String artifact, String version) throws StageException {
		// do we always know the version???
		// to browse an open repo
		// /service/local/repositories/${stageID}/content/...
		// the stage repos are not listed via a call to
		// /service/local/repositories/ but are in existence!
		boolean found = false;
		try {
			URL url;
			if (version == null) {
				url =
				      new URL(nexusURL, "service/local/repositories/" + stage.getStageID() + "/content/"
				                        + group.replace('.', '/') + '/' + artifact + "/?isLocal");
			}
			else {
				url =
				      new URL(nexusURL, "service/local/repositories/" + stage.getStageID() + "/content/"
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
				log.warn("Server returned HTTP status {} when we only expected a 200 or 404.",
				         Integer.toString(response));
			}
			conn.disconnect();
		}
		catch (IOException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
		return found;
	}


	/**
	 * Retrieve and parse an XML file from the given URL.
	 * 
	 * @param url the URL where the XML document can be obtained.
	 * @return the parsed Document.
	 * @throws StageException if there was an issue obtaining or parsing the document.
	 */
	protected Document getDocument(URL url) throws StageException {
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			addAuthHeader(conn);
			conn.setRequestProperty("Accept", "application/xml");
			int status = conn.getResponseCode();
			if (status == HttpURLConnection.HTTP_OK) {
				DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				Document doc = builder.parse(conn.getInputStream());
				conn.disconnect();
				return doc;
			}
			else {
				drainOutput(conn);
				if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
					throw new IOException("Incorrect username / password supplied.");
				}
				else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
					throw new IOException("Document not found - is this a Nexus server?");
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
	 * @param stage The stage to target
	 * @param description the description (used for promote - ignored otherwise)
	 * @param autodrop <code>Boolean.TRUE</code> or <code>Boolean.FALSE</code> sets the autoDropAfterRelease to
	 *           the appropriate value. <code>null</code> omits the value.
	 * @return The XML for the promoteRequest.
	 * @throws StageException if we could not determine if this server support async close or not.
	 */
	protected String createPromoteRequestPayload(Stage stage, String description, Boolean autodrop) throws StageException {
		// TODO? this is missing the targetRepoID which is needed for promote...
		// if the description contains a CDATA END tag then split it across multiple CDATA sections.
		String escapedDescr = (description == null) ? "" : description;
		if (escapedDescr.contains("]]>")) {
			escapedDescr = escapedDescr.replace("]]>", "]]]]><![CDATA[>");
		}
		if (autodrop != null) {
			return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><promoteRequest><data><autoDropAfterRelease>%s</autoDropAfterRelease><stagedRepositoryId>%s</stagedRepositoryId><description><![CDATA[%s]]></description></data></promoteRequest>",
			                     autodrop.toString(), stage.getStageID(), escapedDescr);
		}
		return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><promoteRequest><data><stagedRepositoryId>%s</stagedRepositoryId><description><![CDATA[%s]]></description></data></promoteRequest>",
		                     stage.getStageID(), escapedDescr);
	}


	/**
	 * Perform a staging action.
	 * 
	 * @param action the action to perform.
	 * @param stage the stage on which to perform the action.
	 * @param description description to pass to the server for the action (e.g. the description of the stage
	 *           repo).
	 * @throws StageException if an exception occurs whilst performing the action.
	 */
	protected void
	      performStageAction(StageAction action, Stage stage, String description) throws StageException {
		log.debug("Performing action {} on stage {}", new Object[] {action, stage});
		try {
			URL url = action.getURL(nexusURL, stage);
			String payload;
			if (action == StageAction.PROMOTE && isAsyncClose()) {
				payload = createPromoteRequestPayload(stage, description, Boolean.FALSE);
			}
			else {
				payload = createPromoteRequestPayload(stage, description, null);
			}
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
			log.debug("Server returned HTTP Status {} for {} stage request to {}.",
			          new Object[] {Integer.toString(status), action.name(), stage});

			if (status == HttpURLConnection.HTTP_CREATED) {
				drainOutput(conn);
				conn.disconnect();
			}
			else {
				log.warn("Server returned HTTP Status {} for {} stage request to {}.",
				         new Object[] {Integer.toString(status), action.name(), stage});
				drainOutput(conn);
				conn.disconnect();
				throw new IOException(String.format("server responded with status:%s", Integer.toString(status)));
			}
		}
		catch (IOException ex) {
			String message =
			      String.format("Failed to perform %s action to nexus stage(%s)", action.name(),
			                    stage.toString());
			throw new StageException(message, ex);
		}
	}


	/**
	 * Add the BASIC Authentication header to the HTTP connection.
	 * 
	 * @param conn the HTTP URL Connection
	 */
	private void addAuthHeader(URLConnection conn) {
		// java.net.Authenticator is brain damaged as it is global and no way to delegate for just one server...
		try {
			String auth = username + ":" + password;
			// there is a lot of debate about password and non ISO-8859-1 characters...
			// see https://bugzilla.mozilla.org/show_bug.cgi?id=41489
			// Base64 adds a trailing newline - just strip it as whitespace is illegal in Base64
			String encodedAuth = new Base64().encodeToString(auth.getBytes("ISO-8859-1")).trim();
			conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
			log.debug("Encoded Authentication is: " + encodedAuth);
		}
		catch (UnsupportedEncodingException ex) {
			String msg =
			      "JVM does not conform to java specification.  Mandatory CharSet ISO-8859-1 is not available.";
			log.error(msg);
			throw new RuntimeException(msg, ex);
		}
	}


	private StageException createStageExceptionForIOException(URL url, IOException ex) {
		if (ex instanceof StageException) {
			return (StageException) ex;
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
			if (conn.getContentLength() < 1024) {
				byte[] data = new byte[conn.getConnectTimeout()];
			}
			if (conn.getErrorStream() != null) {
				IOUtils.skip(conn.getErrorStream(), conn.getContentLength());
			}
			else {
				IOUtils.skip(conn.getInputStream(), conn.getContentLength());
			}
		}
	}


	/**
	 * Get the URL used to query the activity on the specified Stage.
	 * 
	 * @param stage the stage to query activity for.
	 * @return a new URL for querying the activity.
	 * @throws StageException if the URL is invalid.
	 */
	private URL getActivityURL(Stage stage) throws StageException {
		return constructURL("service/local/staging/repository/%1$s/activity", stage);
	}


	/**
	 * Get the URL used to query the specified Stage.
	 * 
	 * @param stage the stage to query activity for.
	 * @return a new URL for querying the activity.
	 * @throws StageException if the URL is invalid.
	 */
	private URL getRepositoryURL(Stage stage) throws StageException {
		return constructURL("service/local/staging/repository/%1$s", stage);
	}


	/**
	 * Format a URL based on the specified stage and format.
	 * 
	 * @param stage the stage to query activity for.
	 * @param format a format string. "%1" is the stageID %2 is the profileID.
	 * @return a new URL constructed from the Stage and the format..
	 * @throws StageException if the URL is invalid.
	 */
	private URL constructURL(String format, Stage stage) throws StageException {
		try {
			return new URL(nexusURL, String.format(format, stage.getStageID()));
		}
		catch (MalformedURLException ex) {
			throw createStageExceptionForIOException(nexusURL, ex);
		}
	}
}
