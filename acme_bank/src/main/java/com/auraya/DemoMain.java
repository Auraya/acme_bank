package com.auraya;


import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic="demo.main")
public class DemoMain {
	


	@SneakyThrows
	public static Configuration getConfig(String filename) {
		File propertiesFile = new File(filename);
		Parameters params = new Parameters();
		ReloadingFileBasedConfigurationBuilder<FileBasedConfiguration> builder = new ReloadingFileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
		.configure(params.fileBased().setFile(propertiesFile).setListDelimiterHandler(new DefaultListDelimiterHandler(',')).setThrowExceptionOnMissing(true));
		return builder.getConfiguration();
	}
	
	@SneakyThrows
	public static void main(String[] args) {
		
		log.info("Starting DEMO server");
		Server server = new Server();
		
		Configuration config = getConfig("config/config.properties");
		
		 // HTTP Configuration
       HttpConfiguration httpConfig = new HttpConfiguration();
       httpConfig.setOutputBufferSize(32768);
       httpConfig.setRequestHeaderSize(8192);
       httpConfig.setResponseHeaderSize(8192);
       httpConfig.setSendServerVersion(true);
       httpConfig.setSendDateHeader(true);
      
       ContextHandlerCollection contexts = new ContextHandlerCollection();
      
       

       // Specify the Session ID Manager
       DefaultSessionIdManager idmanager = new DefaultSessionIdManager(server);
       server.setSessionIdManager(idmanager);

       SessionHandler sessions = new SessionHandler();
       
       ContextHandler demoHandler = new ContextHandler(config.getString("http.context","/demo1"));
      
      // demoHandler.getServletContext().setAttribute("app_map", new HashMap<String,String>());
       demoHandler.setHandler(sessions);
       
       sessions.setHandler(new AppHandler(config));
       contexts.addHandler(demoHandler);
       
       ContextHandler promptHandler = new ContextHandler(config.getString("prompt_context", "/prompts") );
       ResourceHandler promptResourceHandler = new ResourceHandler();
       promptResourceHandler.setResourceBase(config.getString("prompt_directory", "prompts"));
       promptResourceHandler.setCacheControl("no-cache");
       promptResourceHandler.setEtags(true);
       promptHandler.setHandler(promptResourceHandler);
       contexts.addHandler(promptHandler);
       
       ContextHandler grammarHandler = new ContextHandler(config.getString("grammar_context","/grammars"));
       ResourceHandler grammarResourceHandler = new ResourceHandler();
       grammarResourceHandler.setResourceBase(config.getString("grammar_directory", "grammars"));
       grammarResourceHandler.setDirectoriesListed(true);       
       grammarResourceHandler.setCacheControl("no-cache");
       grammarResourceHandler.setEtags(true);
       grammarHandler.setHandler(grammarResourceHandler);
       contexts.addHandler(grammarHandler);
       
       PasscodeHandler passcodeHandler = new PasscodeHandler();
       ContextHandler passcodeContextHandler = new ContextHandler(config.getString("passcode_context","/code"));
       passcodeContextHandler.setHandler(passcodeHandler);
       contexts.addHandler(passcodeContextHandler);
       
       demoHandler.getServletContext().setAttribute("app_map", passcodeHandler.getMapCodeToGroup());
       
       
        
       server.setHandler(contexts);
		
		  // Extra options
       server.setDumpAfterStart(false);
       server.setDumpBeforeStop(false);
       server.setStopAtShutdown(false);

       // === jetty-http.xml ===
       ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));

       int port = config.getInt("http.port", 8090);
       http.setPort(port);
       http.setIdleTimeout(10000);
       server.addConnector(http);
       server.start();

       log.info("DEMO server started on port={}", port);	
       server.join();
	}
	

	

}
