package com.auraya.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArmorVox6 implements ArmorVox {
	

	static private final String[] startStrings = {"expr=\"'", "value=\"", "value='"};
	static private final String[] endStrings = {"'\"", "\"", "'"};
	
	
	private String uri;
	//private String group;
	private String channel = "ivr";
	private HttpClientBuilder builder;
	
	@SneakyThrows
	public ArmorVox6(String uri, String channel) {
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

	/* (non-Javadoc)
	 * @see com.auraya.api.ArmorVox#isEnrolled(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Response isEnrolled(String group, String printName, String id) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addTextBody("id", id);
		multipartEntityBuilder.addTextBody("print_name", printName);
		multipartEntityBuilder.addTextBody("group", group);
		
		
		List<String> lines = post(uri+"/check_enrolled",  multipartEntityBuilder.build());
		
		String condition = searchResponseFields(lines, "condition");
		String extra = searchResponseFields(lines, "extra");
		log.debug("id={} condition={} extra={}", id, condition, extra);
		
		return new Response(StringUtils.equals(condition, "ENROLLED"), StringUtils.equals(condition, "ENROLLED"), searchResponseFields(lines, "Extra"), 0.0);
	}
	
	/* (non-Javadoc)
	 * @see com.auraya.api.ArmorVox#enrol(java.lang.String, java.lang.String, java.lang.String, java.util.List)
	 */
	@Override
	public Response enrol(String group, String type, String id, List<byte[]> utterances) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addTextBody("id", id);
		multipartEntityBuilder.addTextBody("print_name", type);
		multipartEntityBuilder.addTextBody("channel", channel);
		multipartEntityBuilder.addTextBody("group", group);
		for (int i = 1; i <= utterances.size(); i++) {
			multipartEntityBuilder.addBinaryBody("utterance"+i, utterances.get(i-1));
		}
		
		List<String> lines = post(uri+"/enrol",  multipartEntityBuilder.build());
		String condition = searchResponseFields(lines, "condition");

		String extra = searchResponseFields(lines, "extra");
		log.debug("id={} type={} condition={} extra={}", id, type, condition, extra);
		return new Response(StringUtils.equals(condition, "GOOD"), StringUtils.equals(condition, "ENROLLED"), searchResponseFields(lines, "Extra"), 0);
	}
	
	@SneakyThrows
	protected List<String> post(String urlString, HttpEntity clientResponseEntity) {
		HttpPost post = new HttpPost(urlString);
		post.setHeader("User-Agent", "JavaArmorvoxClient");
		post.setEntity(clientResponseEntity);
		
		final HttpClient client = builder.build(); 
		
		final HttpResponse serverResponse = client.execute(post);
		return IOUtils.readLines(serverResponse.getEntity().getContent(),"UTF-8");
	}
	
	protected String searchResponseFields(List<String> lines, String field) {
		Pattern fieldPattern = Pattern.compile("name\\s*=\\s*['\"]"+Pattern.quote(field)+"['\"]");
		StringBuilder sb = new StringBuilder();
		
		lines.stream().filter(line -> (fieldPattern.matcher(line).find())).forEach(line -> {
			for (int i = 0; i <  startStrings.length; i++) {
				String startString = startStrings[i];
				String endString = endStrings[i];
				String subString = StringUtils.substringBetween(line, startString, endString);
				if (subString != null) {
					sb.append(StringEscapeUtils.unescapeXml(subString));
				}
			}
		});
		
		String result = sb.toString();
		return result;
	}

	/* (non-Javadoc)
	 * @see com.auraya.api.ArmorVox#verify(java.lang.String, java.lang.String, java.lang.String, byte[])
	 */
	@Override
	public Response verify(String group, String printName, String id, Double alRate, String phrase, byte[] utterance) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addTextBody("id", id);
		multipartEntityBuilder.addTextBody("print_name", printName);
		multipartEntityBuilder.addTextBody("channel", channel);
		multipartEntityBuilder.addTextBody("group", group);
		multipartEntityBuilder.addBinaryBody("utterance", utterance);
		
		if (phrase != null) {
			multipartEntityBuilder.addTextBody("phrase", phrase);
			multipartEntityBuilder.addTextBody("vocab", "en_us_v_2.0");
		}
		
		if (alRate != null) {
			String[] overrideDirectives = {
					"active_learning.rate="+alRate, 
					"active_learning.enabled=true"};
			
			multipartEntityBuilder.addTextBody("override", StringUtils.join(overrideDirectives,"\n"));
		}
		
		
		List<String> lines = post(uri+"/verify",  multipartEntityBuilder.build());
		String condition = searchResponseFields(lines, "condition");
		String extra = searchResponseFields(lines, "extra");
		log.debug("id={} condition={} extra={}", id, condition, extra);
		String impProbString = StringUtils.substringBetween(extra, ":", ",");
		Double impProb = impProbString != null?Double.parseDouble(impProbString):100;
		return new Response(StringUtils.equals(condition, "GOOD"), !StringUtils.equals(condition, "NOT_ENROLLED"), searchResponseFields(lines, "Extra"), impProb);

	}
	
	

	/* (non-Javadoc)
	 * @see com.auraya.api.ArmorVox#delete(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Response delete(String group, String printName, String id) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		multipartEntityBuilder.addTextBody("id", id);
		multipartEntityBuilder.addTextBody("print_name", printName);
		multipartEntityBuilder.addTextBody("group", group);
		
		
		List<String> lines = post(uri+"/delete",  multipartEntityBuilder.build());
		String condition = searchResponseFields(lines, "condition");

		String extra = searchResponseFields(lines, "extra");
		log.debug("id={} condition={} extra={}", id, condition, extra);
		return new Response(StringUtils.equals(condition, "GOOD"), !StringUtils.equals(condition, "NOT_ENROLLED"), searchResponseFields(lines, "Extra"), 0);

	}

	/* (non-Javadoc)
	 * @see com.auraya.api.ArmorVox#verifyList(java.lang.String, java.lang.String, java.util.List, byte[])
	 */
	@Override
	public List<Pair<String, Double>> verifyList(String group, String printName, List<String> list, byte[] utterance) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		String joinedList = StringUtils.join(list, ",");
		multipartEntityBuilder.addTextBody("list", joinedList);
		multipartEntityBuilder.addTextBody("print_name", printName);
		multipartEntityBuilder.addTextBody("channel", channel);
		multipartEntityBuilder.addTextBody("group", group);
		multipartEntityBuilder.addBinaryBody("utterance", utterance);
		
		
		List<String> lines = post(uri+"/cross_match",  multipartEntityBuilder.build());
		String condition = searchResponseFields(lines, "condition");
		String extra = searchResponseFields(lines, "extra");
		log.debug("joinedList={} condition={} extra={}", joinedList, condition, extra);
		
		List<Pair<String,Double>> result = new ArrayList<>();
		if (StringUtils.equals(condition, "GOOD")) {
			String[] parts = StringUtils.split(extra,",");
			for (String part : parts) {
				String[] parts2 = StringUtils.split(part, ":");
				if (parts2[1].equals("NE")) {
					continue;
				}
				String id = parts2[0];
				Double score = Double.valueOf(parts2[1]);
				result.add(Pair.of(id, score));
			}
		}
		
		return result;
		
	}
}
