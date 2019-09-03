package com.auraya.state;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.MDC;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import com.auraya.State;
import com.google.api.client.util.Base64;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BankState extends State {
	
	final static private String[] array = new String[]{"zero","one","two","three","four","five","six","seven","eight","nine"};
	final static private String[] array2 = new String[]{"(zero|oh)","one","two","three","four","five","six","seven","eight","nine"};
	final protected Prompt TRANSFER_MESSAGE = new Prompt("Transfer_1","Please wait while I transfer you to someone to assist with your request.");
	
	final protected String PRINT_1 = "digit";
	final protected String PRINT_2 = "tpd";
	
	@Override
	protected void onNoinput() {
		onRecognitionProblem();	
	}
	
	@Override
	protected void onNomatch() {
		onRecognitionProblem();
	}
	
	protected void onRecognitionProblem() {
		if (getLocalCounter("attempt").intValue() == 3) {
			addPrompt(TRANSFER_MESSAGE);
			doHangup();
		} else {
			addPrompt(new Prompt("Problem_1", "Sorry, I didn't get that."));
			onAttempt();
		}
	}
	
	@SneakyThrows
	protected byte[] writeAudio() {
		byte[] bytes = context.audioMap.get(getClass().getSimpleName());
		if (bytes != null) {
			File dir = new File("sessions/"+MDC.get("sessionId"));
			String name = String.format("%s-%d-%d.wav", getClass().getSimpleName(), getLocalCounter("attempt").intValue(), context.getGlobalCounter("attempt").intValue());
			File file = new File(dir, name);
			FileUtils.writeByteArrayToFile(file, bytes);
			addLog(file.getPath()+"="+getUtterance());
		}
		
		return bytes;
	}
	
	protected void addLog(String message) {
		context.logList.add(message);
	}
	
	protected String getGroup() {
		return context.map.get("group");
	}
	
	protected void sendData(String phase, String print, String uttName, String phrase) {
		JSONObject o = new JSONObject();
		o.put("utterance", Base64.encodeBase64String(context.audioMap.get(uttName)));
		o.put("channel", context.getArmorvox().getChannel());
		o.put("group", getGroup());
		
		o.put("print", print);
		o.put("phase", phase);
		o.put("rate", 8000);
		o.put("phrase", phrase);
		o.put("id", getId());
		o.put("date", ZonedDateTime.now(ZoneId.of("UTC")).toString());
		
		context.getProxy().sendData(getId(),o);
	}
	

	@Override
	protected void doHangup() {
		writeLog();
		super.doHangup();
	}
	
	
	@SneakyThrows
	protected void writeLog() {
		File file = new File("accounts/"+getId()+".txt");
		FileUtils.writeLines(file, context.logList, true);
		context.logList.clear();
	}
	
	@SneakyThrows
	public Grammar getNumberGrammar(int min_length, int max_length, String id) {
		
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("number_grammar.txt")) {
			String digitGrammar = IOUtils.toString(is, "UTF-8");
			//$digits7 | $digits8  | $digits9 | $digits10;
			
			List<String> numGrams = new ArrayList<>();
			
			for (int i = min_length; i <= max_length; i++) {
				numGrams.add(String.format("$digits%d", i));
			}
			
			if (StringUtils.isNumeric(id)) {
	
				List<String> preNumGrams = new ArrayList<>();
				for (int i = min_length - id.length(); i <= max_length - id.length(); i++) {
					if (i > 0) preNumGrams.add(String.format("$digits%d", i));
				}
	
				List<String> items = new ArrayList<>();
				if (!preNumGrams.isEmpty()) {
					items.add(String.format("(%s)",StringUtils.join(preNumGrams, "|")));
				}
				for (char c : id.toCharArray()) {
					items.add(array2[(int)c-'0']);
				}
				
				numGrams.add(String.format("/%s/ (%s)", context.getConfig().getString("cli_weight", "5"), StringUtils.join(items, " ")));
			} 
			return new Grammar(String.format("[plus] (%s); %n%s", StringUtils.join(numGrams,"|"), digitGrammar));
		}
	}
	
	protected String getId() {
		String id = context.map.get("session.callerid");
		id = StringUtils.right(id, context.getConfig().getInt("id_min_len", 8));
		
		id = context.map.getOrDefault("id", id);
		log.debug("id={}", id);
		return id;
	}
	
	protected void setLevel(int level) {
		log.debug("Bio-Level={}", level);
		context.map.put("level", ""+level);
	}
	
	protected int getLevel() {
		return Integer.valueOf(context.map.getOrDefault("level", "0"));
	}

	protected void setId(String id) {
		context.map.put("id", id);
	}
	
	
	protected String getSpokenId(String stateName) {
		return getSpokenId(stateName, true);
	}
	
	protected String getSpokenId(String stateName, boolean truncated) {
		String string = getUtterance(stateName);
		String result = "";
		if (string != null) {
			Map<String,String> map = new HashMap<>();
			for (int i = 0; i < array.length; i++) {
				map.put(array[i], ""+i);
			}
			map.put("oh", "0");
			StringBuffer id = new StringBuffer();
			int multiple = 1;
			for (String part : StringUtils.split(string," ")) {
				switch (part) {
				case "triple": multiple = 3; break;
				case "double": multiple = 2; break;
				default:
					id.append(StringUtils.repeat(map.computeIfAbsent(part, k -> k), multiple));
					multiple = 1;
				}
			}
			if (truncated) {
				result = StringUtils.right(id.toString(), context.getConfig().getInt("id_min_len", 8));
			} else {
				result = id.toString();
			}
		}
		log.debug("stateName={} id={}", stateName, result);
		return result;
	}
	

	static double convertAmount(String amount) {
		String[] parts = StringUtils.split(amount);
		
		String[] digits0_19 = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
		String[] digits20_90 = {"no", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
		
		double scale = 1;
		double dollarsAndCentsTotal = 0;
		double dollarsAndCents = 0;
		int level = 0;
		for (String part : parts) {
			int newLevel = 0;
			int number = 0;
			boolean isMultiply = true;
			int i = searchArray(digits0_19, part);
			if (i >=0 ) {
				isMultiply = false;
				number = i;
			} else {
				i = searchArray(digits20_90, part);
				if (i >=0 ) {
					isMultiply = false;
					number = i * 10;
				} else {
					switch (part) {
					case "hundreds":
					case "hundred":  number = 100;  break;
					case "thousands":
					case "thousand": number = 1000; newLevel=3; break;
					case "millions":
					case "million":  number = 1000000; newLevel=6; break;
					case "billions":
					case "billion":  number = 1000000000; newLevel=9; break;
					case "dollar":  
					case "dollars": dollarsAndCentsTotal += dollarsAndCents; dollarsAndCents = 0; scale = 0.01; newLevel=0; break;
					case "cent":  
					case "cents": number = 1; if (scale == 1) { dollarsAndCentsTotal += dollarsAndCents * 0.01;  dollarsAndCents=0;}
					default:
						continue;
					}
				}
			}
			
			
			if (newLevel < level) {
				dollarsAndCentsTotal += dollarsAndCents;
				dollarsAndCents = 0;
			}
			
			if (isMultiply) {
				dollarsAndCents *= number;
			} else {
				dollarsAndCents += number * scale;
			}
			
			level = newLevel;
			
		}
		dollarsAndCentsTotal += dollarsAndCents;
		
		
		return dollarsAndCentsTotal;
	}

	static int searchArray(String[] array, String s) {
		for (int i = 0; i < array.length; i++) {
			if (array[i].equals(s)) {
				return i;
			}
		}
		return -1;
	}
	
	protected String $(String extension) {
		return getStateName() + "_" + extension;
	}
	
	
	protected static List<String> makeAmountPrompts(String dollars, String cents) {
		int iDollars = Double.valueOf(dollars).intValue();
		int iCents =  Double.valueOf(cents).intValue();
		
		List<String> prompts = new ArrayList<>();
		
		if (iDollars == 0) {
			prompts.add("digits/0f");
		}
		boolean needsAnd = iDollars >= 100;
		if (iDollars >= 1000) {
			int thousands = iDollars - (iDollars % 1000);
			prompts.add("digits/"+thousands);
			iDollars %= 1000;
		}
		if (iDollars >= 100) {
			int hundreds = iDollars - (iDollars % 100);
			prompts.add("digits/"+hundreds);
			iDollars %= 100;
		}
		if (iDollars > 0 && needsAnd) {
			prompts.add("digits/and");
		}
		if (iDollars >= 20) {
			int tens = iDollars - (iDollars % 10);
			prompts.add("digits/"+tens);
			iDollars %= 10;
		} else if (iDollars > 9) {
			prompts.add("digits/"+iDollars);
			iDollars = 0;
		}
		
		if (iDollars > 0) {
			prompts.add("digits/"+iDollars + "h");
		}
		
		if (iCents == 0) {
			prompts.add("digits/dollars_only");
		} else {
			prompts.add("digits/dollars");
			
			if (iCents == 0) {
				prompts.add("digits/0f");
			}
			
			if (iCents >= 20) {
				int tens = iCents - (iCents % 10);
				prompts.add("digits/"+tens);
				iCents %= 10;
			} else if (iCents > 9) {
				prompts.add("digits/"+iCents);
				iCents = 0;
			}
			if (iCents > 0) {
				prompts.add("digits/"+iCents + "h");
			}
			
			prompts.add("digits/cents");
		}
		return prompts;
	}
	
	protected void sendSMS(String number, String message) {
		// Declare the security credentials to use
		String username = context.getConfig().getString("sms.username","user1");
		String password =  context.getConfig().getString("sms.password","secret");

		// Set the attributes of the message to send
		String type = "1-way";
		String senderid =  context.getConfig().getString("sms.senderid","AcmeBank");
		String to = number;

		try {

			log.debug("Sending SMS to {}, message {}", number, message);
			// Build URL encoded query string
			String encoding = "UTF-8";
			String queryString 
			= "username=" + URLEncoder.encode(username, encoding) 
			+ "&password=" + URLEncoder.encode(password, encoding) 
			+ "&message=" + URLEncoder.encode(message, encoding)
			+ "&senderid=" + URLEncoder.encode(senderid, encoding) 
			+ "&to=" + URLEncoder.encode(to, encoding) 
			+ "&type=" + URLEncoder.encode(type, encoding);

			// Send request to the API servers over HTTPS
			URL url = new URL("https://api.directsms.com.au/s3/http/send_message?");
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(queryString);
			wr.flush();

			// The response from the gateway is going to look like this:
			// id: a4c5ad77ad6faf5aa55f66a
			//
			// In the event of an error, it will look like this:
			// err: invalid login credentials
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String result = rd.readLine();
			wr.close();
			rd.close();

			if (result == null) {
				log.debug("No response received");
			} else if (result.startsWith("id:")) {
				log.debug("Message sent successfully. {}", result);
			} else {
				log.debug("Error - {}", result);
			}
		} catch (Exception e) {
			log.error("Error - ", e);
		}
	}
	
//	public static void main(String[] args) {
//		sendSMS("+61414948438", "Thank you for enrolling with Acme Bank. Your ID is 0414 948 438. Call 0283109724 again to access your account.");
//		//sendSMS("61418255938", "Thank you for enrolling with Acme Bank. Your ID is 0418255938. Call 0283109724 again to access your account.");
//	}
//	
}
