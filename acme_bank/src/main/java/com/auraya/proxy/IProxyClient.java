package com.auraya.proxy;

import org.apache.commons.configuration2.Configuration;
import org.json.JSONObject;

public interface IProxyClient {
	
	void init(Configuration configuration);

	void sendData(String id, JSONObject o);

	void saveUser(String id, String group, String mobile, String name);

	void deleteUser(String id, String mobile, String name);

	String syncRecognizeFile(byte[] data);

}