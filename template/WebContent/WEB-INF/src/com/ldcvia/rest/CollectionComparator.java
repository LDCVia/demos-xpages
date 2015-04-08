package com.ldcvia.rest;

import java.util.Comparator;

import com.ibm.commons.util.io.json.JsonJavaObject;

public class CollectionComparator implements Comparator<JsonJavaObject> {
	public int compare(JsonJavaObject arg0, JsonJavaObject arg1) {
		return arg0.getString("collection").compareTo(arg1.getString("collection"));
	}
}