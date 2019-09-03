package com.auraya;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.MDC;

import com.auraya.api.ArmorVox;
import com.auraya.api.ArmorVox6;
import com.auraya.api.ArmorVox8;
import com.auraya.proxy.DefaultProxyClient;
import com.auraya.proxy.IProxyClient;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppHandler  extends AbstractHandler {
	


	Configuration config;
	ArmorVox armorvox;
	private IProxyClient proxy;
	
	@SneakyThrows
	public AppHandler(Configuration config) {
		this.config = config;
		String uri = config.getString("armorvox.uri", "http://localhost:9006/v6/");
		String channel = config.getString("armorvox.channel", "ivr");
		
		if (config.getBoolean("api8")) {
			this.armorvox = new ArmorVox8(uri, channel);
		} else {
			this.armorvox = new ArmorVox6(uri, channel);
		}
		
		String className = config.getString("proxy.class", DefaultProxyClient.class.getName());
		Class<? extends IProxyClient> clazz =  Class.forName(className).asSubclass(IProxyClient.class);;
		Constructor<? extends IProxyClient> ctor = clazz.getConstructor();
		this.proxy = ctor.newInstance();
		this.proxy.init(config);
	}
	
	
	@Override
	public void handle(String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
		
		MDC.put("sessionId", request.getSession().getId());
		
		Map<String,String> map = new HashMap<>();
		Map<String,byte[]> audioMap = new HashMap<>();
		
		
		if (StringUtils.startsWith(request.getContentType(), "multipart/form-data")) {
			int maxFileLength = 20000000;
			MultipartConfigElement multipartConfigElement = new MultipartConfigElement("tmp", maxFileLength, maxFileLength, maxFileLength);
			request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);
			
			for (Part part : request.getParts()) {
				if (part.getName().endsWith("recording")) {
					byte[] utterance = IOUtils.toByteArray(part.getInputStream());
					log.debug("{}.length={}", part.getName(), utterance.length);
					audioMap.put(StringUtils.substringBefore(part.getName(), "$"), utterance);
				} else {
					map.put(part.getName(), getString(request, part.getName()));
				}
			}
		} else {
			for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
				map.put(e.getKey(), e.getValue()[0]);
			}
		}
		
		for (Map.Entry<String, String> e : map.entrySet()) {
			log.debug("{}={}", e.getKey(), e.getValue());
		}
			
		State state = getState(request,  response);
		state.context.map.putAll(map);
		state.context.audioMap.putAll(audioMap);
		
		String event = map.getOrDefault("event", "start");
		state.onEvent(event);
		
		
	}
	
	@SuppressWarnings("unchecked")
	@SneakyThrows
	private State getState(HttpServletRequest request, HttpServletResponse response) {
		Object contextObject = request.getSession().getAttribute("context");
		State.Context context = null;
		if (contextObject != null) {
			context = ((State.Context) contextObject);
		} else {
			Map<String,String> appMap = (Map<String,String>) request.getServletContext().getAttribute("app_map");
			String clazz = config.getString("application.start_state", "com.auraya.state.Start");
			Class<State> classDefinition = (Class<State>) Class.forName(clazz);
			context = new State.Context(classDefinition.newInstance(), armorvox, proxy, config, appMap);
			request.getSession().setAttribute("context", context);
		}
		context.outputStream = response.getOutputStream();
		return context.currentState;
	}

	
	@SneakyThrows
	protected String getString(HttpServletRequest request, String name) {
		
		if (request.getPart(name) == null) {
			return "EMPTY";
			//throw new RuntimeException(String.format("Expected request parameter [%s] is missing", name));
		}
		return new String(getBytes(request,name),"UTF-8");
	}
	
	@SneakyThrows
	protected byte[] getBytes(HttpServletRequest request, String name) {
		return IOUtils.toByteArray(request.getPart(name).getInputStream());
	}
	
	
	
	
}
