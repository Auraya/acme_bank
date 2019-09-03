package com.auraya;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import lombok.SneakyThrows;

public class PasscodeHandler extends AbstractHandler {
	
	FileBasedConfiguration config = null;
	Map<String,String> mapCodeToGroup = new ConcurrentHashMap<>();
	File propertiesFile = new File("passcodes.properties");
	
	Random random = new Random();

	@SneakyThrows
	public PasscodeHandler() {
		propertiesFile.createNewFile();
		
		Configurations configs = new Configurations();
		FileBasedConfigurationBuilder<PropertiesConfiguration> builder = configs.propertiesBuilder(propertiesFile);
		config = builder.getConfiguration();
		
		Iterator<String> i = config.getKeys();
		while (i.hasNext()) {
			String group = i.next();
			mapCodeToGroup.put(config.getString(group), group);
		}
			
	}
	
	public Map<String, String> getMapCodeToGroup() {
		return mapCodeToGroup;
	}
	
	@SneakyThrows
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		
		String group = baseRequest.getParameter("group");
		if (group != null) {
			String passcode = config.getString(group, null);
			if (passcode == null) {
				// make new random passcode, 4 digits
				do {
					passcode = "";
					for (int j = 0; j < 4; j++) {
						passcode += ""+random.nextInt(10);
					}
				} while (mapCodeToGroup.keySet().contains(passcode));
				
				config.setProperty(group, passcode);
				mapCodeToGroup.put(passcode, group);
				
				try (FileWriter out = new FileWriter(propertiesFile)) {
					config.write(out);
				}
			}
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(passcode);
		}
		
		String passcode = baseRequest.getParameter("passcode");
		if (passcode != null) {
			group = mapCodeToGroup.get(passcode);
			if (group != null) {

				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print(group);
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				response.getWriter().print("");
			}
		}
		response.getWriter().flush();
	}

	

}
