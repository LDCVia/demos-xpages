package com.ldcvia.rest;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.View;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.xsp.extlib.util.ExtLibUtil;

/**
 * A managed bean (to run in sessionScope) that gets data from LDC Via services
 * @author whitemx
 *
 */
public class LDCViaREST implements Serializable {
	private String baseurl = null;
	private String adminapikey = null;
	private String userapikey = null;
	/**
	 * 
	 */
	private static final long serialVersionUID = 7362366782893881665L;

	/**
	 * Instantiate the object, get security config from ref data document
	 */
	public LDCViaREST() {
		try{
			Database database = ExtLibUtil.getCurrentDatabase();
			View settingsview = database.getView("Settings");
			Document settings = settingsview.getFirstDocument();
			if (settings == null){
				throw new Exception("Settings Document Has Not Been Configured");
			}else{
				this.adminapikey = settings.getItemValueString("apikey");
				this.baseurl = settings.getItemValueString("baseurl");
				settings.recycle();
			}
			settingsview.recycle();
			database = null;
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Switch operations to work as a different user using their email address as key
	 * All operations will have document level security applied
	 * @param email
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	public void operateAsEndUser(String email) throws ClientProtocolException, IOException, JsonException{
		if (email == null || email.equals("")){
			this.userapikey = null;
		}else{
			String responseBody = loadURL("/1.0/userdetails/" + email);
			JsonJavaFactory factory = JsonJavaFactory.instanceEx;
			JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, responseBody);
			this.userapikey = json.getString("apikey");
		}
	}
	
	/**
	 * Revert to operating as administrator (document level security will no longer apply)
	 */
	public void operateAsAdmin(){
		this.userapikey = null;
	}
	
	/**
	 * Get the host name that we're operating against
	 * @return
	 */
	public String getHostName(){
		return this.baseurl;
	}
	
	/**
	 * Are we operating as end user (or admin)
	 * @return
	 */
	public boolean isEndUser(){
		return !(this.userapikey == null);
	}
	
	/**
	 * Get the current api key being used (be it current user or admin)
	 * @return
	 */
	public String getApiKey(){
		String key = this.adminapikey;
		if(this.userapikey != null){
			key = this.userapikey;
		}
		return key;
	}
	
	/**
	 * Get a list of databases that the current user has access to
	 *  
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	public JsonJavaArray getDatabases() throws ClientProtocolException, IOException, JsonException{
		String responseBody = loadURL("/1.0/databases");
		JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, responseBody);
		
		return json.getAsArray("databases");
	}
	
	/**
	 * Gets a list of collections for the given database
	 * Each element contains two properties: collection and count. Count is relative to the api key used to get the collection list
	 * @param dbname
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	@SuppressWarnings("unchecked")
	public ArrayList getCollections(String dbname) throws ClientProtocolException, IOException, JsonException{
		String responseBody = loadURL("/1.0/collections/" + dbname);

		JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		ArrayList list = (ArrayList) JsonParser.fromJson(factory, responseBody);
		Collections.sort(list, new CollectionComparator());
		return list;
	}
	
	/**
	 * Get the meta data describing a collection: a list of fields in the collection
	 * @param dbname
	 * @param collection
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	public JsonJavaArray getMetaData(String dbname, String collection) throws ClientProtocolException, IOException, JsonException{
		String responseBody = loadURL("/1.0/metadata/" + dbname + "/" + collection);

		JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, responseBody);
		return json.getAsArray("fields");
	}
	
	/**
	 * Get a list of documents from a collection
	 * Document level security applies
	 * @param dbname
	 * @param collection
	 * @param position
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	public JsonJavaArray getDocuments(String dbname, String collection, int position) throws ClientProtocolException, IOException, JsonException{
		String responseBody = loadURL("/1.0/collections/" + dbname + "/" + collection + "?start=" + position);

		JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, responseBody);
		return json.getAsArray("data");
	}
	
	/**
	 * Get full detail for an individual document
	 * @param dbname
	 * @param collection
	 * @param unid
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws JsonException
	 */
	public JsonJavaObject getDocument(String dbname, String collection, String unid) throws ClientProtocolException, IOException, JsonException {
		String responseBody = loadURL("/1.0/document/" + dbname + "/" + collection + "/" + unid);

		JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, responseBody);
		return json;
	}

	/**
	 * Helper method to request a URL from the LDC Via service
	 * @param url
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private String loadURL(String url) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpget = new HttpGet(this.baseurl + url);

		// Create a custom response handler
		ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

			public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
				int status = response.getStatusLine().getStatusCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity) : null;
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
			}

		};
		httpget.addHeader("apikey", getApiKey());
		return httpclient.execute(httpget, responseHandler);
	}
}