package com.auraya;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONObject;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyClient {
	
	static {
		
	}

	private String uri;
	private HttpClientBuilder builder;
	//private SpeechClient speech; 
	
	@SneakyThrows
	public ProxyClient(String uri) {
		this.uri = uri;
		
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

	@SneakyThrows
	public void sendData(String id, JSONObject o) {
		id = URLEncoder.encode(id, "UTF-8");
		
		StringEntity entity = new StringEntity(o.toString(),"UTF-8");
		String s = String.format("%s/send_data.php?id=%s", uri, id);
		List<String> lines = post(s,  entity);
		
		log.debug("SendData response: {}", StringUtils.join(lines));
	}
	
	@SneakyThrows
	public void saveUser(String id, String group, String mobile, String name) {
		id = URLEncoder.encode(id, "UTF-8");
		mobile = URLEncoder.encode(mobile, "UTF-8");
		name = URLEncoder.encode(name, "UTF-8");
		group = URLEncoder.encode(group, "UTF-8");
		String email = URLEncoder.encode("", "UTF-8");
		String s = String.format("%s/save_user.php?id=%s&mobile=%s&name=%s&group=%s&email=%s", uri, id, mobile, name, group, email);
		List<String> lines = get(s);
		log.debug("SendData request: {} response: {}", s, StringUtils.join(lines));
	}
	
	@SneakyThrows
	public void deleteUser(String id, String mobile, String name) {
		id = URLEncoder.encode(id, "UTF-8");
		mobile = URLEncoder.encode(mobile, "UTF-8");
		name = URLEncoder.encode(name, "UTF-8");
		String s = String.format("%s/delete_user.php?id=%s", uri, id);
		List<String> lines = get(s);
		log.debug("SendData response: {}", StringUtils.join(lines));
	}

	
	@SneakyThrows
	public static void main(String[] args) {
		ProxyClient client = new ProxyClient("https://cloud.armorvox.com/id_manager/");
		client.syncRecognizeFile( Files.readAllBytes(Paths.get("test_utts/EnrolName-1-2.wav")));
		//client.syncRecognizeFile("C:/jamie/EnrolName-1-6.wav");
		//client.syncRecognizeFile("C:/jamie/EnrolName-2-5.wav");
		//client.syncRecognizeFile("C:/jamie/EnrolName-2-7.wav");
	}
	
	@SneakyThrows
	public String syncRecognizeFile(byte[] data) {
		
//		SpeechSettings.Builder builder = SpeechSettings.newBuilder();
//		
//		CredentialsProvider credentialsProvider = CredentialsProvider.class
//		builder.setCredentialsProvider(credentialsProvider);
//		
//		  SpeechSettings settings = SpeechSettings.defaultCredentialsProviderBuilder().();
//		  

		try (SpeechClient speech = SpeechClient.create()) {
		 
			//  byte[] dataNameIs = Files.readAllBytes(Paths.get("c:/jamie/MyNameIsUlaw.wav"));
			 // ByteString audioBytesNameIs = ByteString.copyFrom(dataNameIs,44,dataNameIs.length-44);
	
			 
			 
			 // Path path = Paths.get(fileName);
			 // byte[] data = Files.readAllBytes(path);
			  ByteString audioBytes = ByteString.copyFrom(data,44,data.length-44);
			 // audioBytes = audioBytesNameIs.concat(audioBytes);
			  // Configure request with local raw PCM audio
			  //RecognitionConfig.AudioEncoding.
			  
	//		  SpeechContext speechContext = 
	//				  SpeechContext.newBuilder()
	//				  .addPhrases("mike")
	//				  .addPhrases("banbrook")
	//				  .build();
	//		  
			  RecognitionConfig config = RecognitionConfig.newBuilder()
			      .setEncoding(AudioEncoding.MULAW)
			      .setLanguageCode("en-GB")
			      .setSampleRateHertz(8000)
			      .setEnableWordTimeOffsets(true)
			    //  .addSpeechContexts(speechContext)
			      .build();
			  RecognitionAudio audio = RecognitionAudio.newBuilder()
			      .setContent(audioBytes)
			      .build();
	
			  // Use blocking call to get audio transcript
			  RecognizeResponse response = speech.recognize(config, audio);
			  List<SpeechRecognitionResult> results = response.getResultsList();
	
			  for (SpeechRecognitionResult result: results) {
			    // There can be several alternative transcripts for a given chunk of speech. Just use the
			    // first (most likely) one here.
				  for (SpeechRecognitionAlternative alternative : result.getAlternativesList()) {
			   // SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
					// log.debug( alternative.getTranscript());
					 log.debug("Transcription: {}  Conf: {}", alternative.getTranscript(),  alternative.getConfidence());
	//					
					  return alternative.getTranscript();
					  
	//				  for (WordInfo info : alternative.getWordsList()) {
	//
	//					  System.out.printf("word: %s start=%d end=%d%n", info.getWord(),  info.getStartTime().getNanos()/1000000, info.getEndTime().getNanos()/1000000);
	//				  }
				  }
			  }
		}
		  
		return "";
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
	
	@SneakyThrows
	protected List<String> get(String urlString) {
		HttpGet req = new HttpGet(urlString);
		req.setHeader("User-Agent", "JavaArmorvoxClient");
		
		final HttpClient client = builder.build(); 
		
		final HttpResponse serverResponse = client.execute(req);
		return IOUtils.readLines(serverResponse.getEntity().getContent(),"UTF-8");
	}
	
	
}
