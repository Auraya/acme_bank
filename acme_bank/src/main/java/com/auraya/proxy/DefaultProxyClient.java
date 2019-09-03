package com.auraya.proxy;

import org.apache.commons.configuration2.Configuration;
import org.json.JSONObject;

public class DefaultProxyClient implements IProxyClient {


	@Override
	public void sendData(String id, JSONObject o) {
		
	}

	@Override
	public void saveUser(String id, String group, String mobile, String name) {
		
	}

	@Override
	public void deleteUser(String id, String mobile, String name) {
		
	}

	@Override
	public String syncRecognizeFile(byte[] data) {
		return "";
	}
	
	@Override
	public void init(Configuration configuration) {
		
	}

}
