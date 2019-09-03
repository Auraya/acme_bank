package com.auraya.api;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArmorVox8 implements ArmorVox {
	

	
	private String uri;
	private String channel = "ivr";
	private HttpClientBuilder builder;
	private ObjectMapper mapper = new ObjectMapper();
	
	@SneakyThrows
	public ArmorVox8(String uri, String channel) {
		this.uri = uri;
		//this.group = group;
		this.channel = channel; 
		
		RegistryBuilder<ConnectionSocketFactory> csf = RegistryBuilder.<ConnectionSocketFactory> create();
		
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, null, null);
		
		SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		csf.register("https", scsf);
		
		csf.register("http", PlainConnectionSocketFactory.getSocketFactory());
		
		
		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(csf.build());
		
		int maxPerRoute = 20;
		
		connManager.setDefaultMaxPerRoute(maxPerRoute);

		builder = HttpClientBuilder.create();

		builder.setConnectionManager(connManager);
		
		RequestConfig.Builder requestBuilder = RequestConfig.custom()
				.setConnectTimeout(60000)
				.setSocketTimeout(60000);

		builder.setDefaultRequestConfig(requestBuilder.build());
	}
	
	public String getChannel() {
		return channel;
	}

	public Response isEnrolled(String group, String printName, String id) {
			
		JsonNode node = get(String.format("%s/voiceprint/%s/%s?no_payload=true", uri, id, printName), group);
		
		String condition = node.findPath("status").asText("missing");
		log.debug("id={} node={}", id, node);
		
		return new Response(StringUtils.equals(condition, "good"), StringUtils.equals(condition, "good"), null, 0);
	}
	
	@SneakyThrows
	public Response enrol(String group, String printName, String id, List<byte[]> utterances) {
		
		
		ObjectNode request =  mapper.createObjectNode();
		ArrayNode aa = request.putArray("utterances");
		for (byte[] bytes : utterances) aa.add(createUtterance(bytes));
		request.put("channel", channel);
		
		HttpEntity entity = new StringEntity(mapper.writeValueAsString(request));
		
		
		JsonNode node = post(String.format("%s/voiceprint/%s/%s", uri, id, printName), group, entity);
		String condition = node.findPath("status").asText("missing");
		log.debug("id={} node={}", id, node);
		return new Response(StringUtils.equals(condition, "good"), StringUtils.equals(condition, "enrolled"), null, 0);

	}
	
	@SneakyThrows
	protected JsonNode post(String urlString, String group, HttpEntity clientResponseEntity) {
		HttpPost http = new HttpPost(urlString);
		http.setHeader("User-Agent", "JavaArmorvoxClient");
		http.setHeader("authorization", group);
		http.setEntity(clientResponseEntity);
		
		final HttpClient client = builder.build(); 
		final HttpResponse serverResponse = client.execute(http);
		return mapper.readTree(serverResponse.getEntity().getContent());
	}
	
	@SneakyThrows
	protected JsonNode put(String urlString, String group, HttpEntity clientResponseEntity) {
		HttpPut http = new HttpPut(urlString);
		http.setHeader("User-Agent", "JavaArmorvoxClient");
		http.setHeader("authorization", group);
		http.setEntity(clientResponseEntity);
		
		final HttpClient client = builder.build(); 
		final HttpResponse serverResponse = client.execute(http);
		return mapper.readTree(serverResponse.getEntity().getContent());
	}
	
	@SneakyThrows
	protected JsonNode get(String urlString, String group) {
		HttpGet http = new HttpGet(urlString);
		http.setHeader("User-Agent", "JavaArmorvoxClient");
		http.setHeader("authorization", group);
		
		final HttpClient client = builder.build(); 
		final HttpResponse serverResponse = client.execute(http);
		return mapper.readTree(serverResponse.getEntity().getContent());
	}
	
	@SneakyThrows
	protected JsonNode delete(String urlString, String group) {
		HttpDelete http = new HttpDelete(urlString);
		http.setHeader("User-Agent", "JavaArmorvoxClient");
		http.setHeader("authorization", group);
		
		final HttpClient client = builder.build(); 
		final HttpResponse serverResponse = client.execute(http);
		return mapper.readTree(serverResponse.getEntity().getContent());
	}
	
	private ObjectNode createUtterance(byte[] bytes, String phrase, String vocab, Boolean recognition) {
		ObjectNode utterance = mapper.createObjectNode();
		utterance.put("content", bytes);
		if (phrase != null) utterance.put("phrase", phrase);
		if (vocab != null) utterance.put("vocab", vocab);
		if (recognition != null) utterance.put("recognition", recognition);
		
		return utterance;
	}
	
	private ObjectNode createUtterance(byte[] bytes) {
		return createUtterance(bytes, null, null, null);
	}
	
	@SneakyThrows
	public Response verify(String group, String printName, String id, Double alRate, String phrase, byte[] utterance) {
		
		ObjectNode request =  mapper.createObjectNode();
		
		ObjectNode utteranceNode = phrase != null?createUtterance(utterance, phrase, "en_digits", false): createUtterance(utterance);
		
		request.set("utterance", utteranceNode);
		request.put("channel", channel);
		
		if (alRate != null) {
			String[] overrideDirectives = {
					"active_learning.rate="+alRate, 
					"active_learning.enabled=true"};
			
			request.put("override", StringUtils.join(overrideDirectives,"\n"));
		}
		
			
		HttpEntity entity = new StringEntity(mapper.writeValueAsString(request));
		
		JsonNode node = put(String.format("%s/voiceprint/%s/%s", uri, id, printName), group, entity);
		Double impProb = node.findPath("impostor_prob").asDouble(100);
		
		String condition = node.findPath("status").asText("missing");
		log.debug("id={} node={}", id, node);
		return new Response(StringUtils.equals(condition, "good"), !StringUtils.equals(condition, "not_enrolled"), impProb.toString(), impProb);
	}
	
	public Response delete(String group, String printName, String id) {
		
		JsonNode node = delete(String.format("%s/voiceprint/%s/%s", uri, id, printName), group);
		String condition = node.findPath("status").asText("missing");
		log.debug("id={} node={}", id, node);
		return new Response(StringUtils.equals(condition, "good"), !StringUtils.equals(condition, "not_enrolled"), null, 0);

	}

	@SneakyThrows
	public List<Pair<String, Double>> verifyList(String group, String printName, List<String> list, byte[] utterance) {

		
		ObjectNode request =  mapper.createObjectNode();
		request.set("utterance", createUtterance(utterance));
		request.put("channel", channel);
		ArrayNode aa = request.putArray("ids");
		for (String id : list) aa.add(id);
			
		HttpEntity entity = new StringEntity(mapper.writeValueAsString(request));
		
		JsonNode node = put(String.format("%s/voiceprint/%s", uri, printName), group, entity);

		String condition = node.findPath("status").asText("missing");
		log.debug("ids={} node={}", aa, node);
		
		
		List<Pair<String,Double>> result = new ArrayList<>();
		if (StringUtils.equals(condition, "good")) {
			JsonNode speakersArray = node.get("speakers");
			if (speakersArray.isArray()) {
				for (int i = 0; i < speakersArray.size(); i++) {
					JsonNode speaker = speakersArray.get(i);
					String id = speaker.get("id").asText();
					Double impProb = speaker.get("impostor_prob").asDouble();
					result.add(Pair.of(id, impProb));
				}
			}
		}
		
		return result;
	}
}
