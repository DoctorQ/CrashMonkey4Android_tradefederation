package com.android.tradefed.util.net;

import java.io.IOException;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import com.android.tradefed.log.LogUtil.CLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonHelper {

	public static String getJsonString(String url) {
		String ret = null;
		Connection conn = Jsoup.connect(url);
		Response resp = null;
		conn.ignoreContentType(true);
		try {
			resp = conn.execute();
			return resp.body();
		} catch (IOException e) {
			CLog.i("failed to get json result for %s, %s",url,e.getMessage());
		} 
		return ret;
	}
	
	public static JsonObject getJsonObject(String url) {
		JsonParser parser = new JsonParser();
		JsonObject ret = null;
		String json = getJsonString(url);
		if(json != null) {
			return parser.parse(json).getAsJsonObject();
		}
		return ret;
	}
}
