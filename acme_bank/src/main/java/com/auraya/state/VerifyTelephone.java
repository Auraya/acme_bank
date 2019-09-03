package com.auraya.state;



import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;

import com.auraya.api.Response;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VerifyTelephone extends BankState {
	
	@Override
	protected void onStart() {
		addPrompt(new Prompt($("1"), "To access the banking services, we will first need to check your identity."));
		super.onStart();
	}
	
	@SneakyThrows
	@Override
	protected void onAttempt() {
		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt("Now", "Now, "));
			addPrompt(new Prompt($("2"), "Please say your telephone number."));
		} else {
			//addPrompt(new Prompt($("3"), "Your ten digit telephone number must include the area prefix such as 0 2, 0 3, or 0 4.", 200));
			addPrompt(new Prompt("Now", "Now, "));
			addPrompt(new Prompt($("2"), true, "Please say your telephone number."));
		}
		int minLen = context.getConfig().getInt("id_min_len", 8);
		int maxLen = context.getConfig().getInt("id_max_len", 11);
		doGrammar(getNumberGrammar(minLen, maxLen, getId()));
	}
	
	@Override
	protected void onFilled() {
		
		writeAudio();
		
		String callerId = getId();
		String spokenId = getSpokenId("VerifyTelephone");
		
		context.map.put("spoken_number", getSpokenId("VerifyTelephone", false));
		
		log.debug("callerId={} spokenId={}",callerId, spokenId);
		checkFraudList();
		

		sendData("verify", PRINT_1, "VerifyTelephone", getSpokenId("VerifyTelephone", false)) ;
		
		if (StringUtils.equals(spokenId, callerId)) {
			log.debug("Ids are the same");
			handleSameId(callerId);
		} else {
			handleDifferentIds(callerId, spokenId);	
		}
		
		
	}

	private void handleDifferentIds(String callerId, String spokenId) {
		
		int distance =LevenshteinDistance.getDefaultInstance().apply(callerId, spokenId);
		log.debug("Ids are different. Levenstein distance={}", distance);
		
		Double alRate = context.getConfig().getDouble("alRate.phone", 0.1);
		

		Double faRate = context.getConfig().getDouble("faRate.phone", 0.1);
		
		// FIRST TRY CLI ID
		String phrase = context.map.get("session.callerid").replaceAll("[^0-9]", "");
		if (phrase.length() >= context.getConfig().getInt("min_cli_length", 4)) {
			phrase = phrase.replaceAll("([0-9](?!$))", "$1 ");
			
			log.debug("checking phrase matches: {}", phrase);
			Response verifyCallerIdResponse = context.getArmorvox().verify(getGroup(), PRINT_1, callerId, alRate, phrase, context.audioMap.get("VerifyTelephone"));		
			
			log.debug("impProb={} alRate={}, faRate={}", verifyCallerIdResponse.getImpProb(), alRate, faRate);
			
			if (verifyCallerIdResponse.getImpProb() <= faRate) {
				// All good!
				setLevel(2);
				setId(callerId);
				gotoState(new AccountBalance());
				return;
			}
		}
		
		// THEN TRY SPOKEN ID instead
		Response verifySpokenResponse = context.getArmorvox().verify(getGroup(), PRINT_1, spokenId, alRate, null, context.audioMap.get("VerifyTelephone"));
		log.debug("impProb={} alRate={}, faRate={}", verifySpokenResponse.getImpProb(), alRate, faRate);
		
		if (verifySpokenResponse.getImpProb() <= faRate) {
			// All good!
			setLevel(1);
			setId(spokenId);
			gotoState(new AccountBalance());
			return;
		}
		
		
		if (getLocalCounter("attempt").intValue() == 1) {
			
			// first failure can be counted as a nomatch
			// save utterance for active learning if necessary
			context.audioMap.put("VerifyTelephoneAL", context.audioMap.get("VerifyTelephone"));
			
			onNomatch();
			return;
		} 
				
		if (verifySpokenResponse.isEnrolled()) {
			// they were enrolled, but not successful, so probably impostor
			// Phone number and spoken number were the same - nothing else to try
			log.debug("they were enrolled, but not successful, so probably impostor");
			addPrompt(TRANSFER_MESSAGE);
			doHangup();
		} else {
		
			// perhaps we got the ID wrong or they'd like to enrol?
			gotoState(new ConfirmID(spokenId));
		}
		
	}

	private void handleSameId(String callerId) {
	

		Double faRate = context.getConfig().getDouble("faRate.phone", 0.1);
		
		Response enrolledResponse = context.getArmorvox().isEnrolled(getGroup(), PRINT_1, callerId);
		if (enrolledResponse.isOk()) {
			Double alRate = context.getConfig().getDouble("alRate.phone", 0.1);
			Double alRateForce = context.getConfig().getDouble("alRate.phone.force", 2.0);
			
			
			Response verifyResponse = context.getArmorvox().verify(getGroup(), PRINT_1, callerId, alRate, null, context.audioMap.get("VerifyTelephone"));
			
			log.debug("impProb={} alRate={}, faRate={}", verifyResponse.getImpProb(), alRate, faRate);
			
			if (verifyResponse.getImpProb() <= faRate) {
				// All good!
				// Any active learning to do?
				
				if (context.audioMap.get("VerifyTelephoneAL") != null) {
					context.getArmorvox().verify(getGroup(), PRINT_1, callerId, alRateForce, null, context.audioMap.get("VerifyTelephoneAL"));
				}
				setLevel(2);
				setId(callerId);
				gotoState(new AccountBalance());

			} else {
				
				if (getLocalCounter("attempt").intValue() == 1) {
					
					// first failure can be counted as a nomatch
					// save utterance for active learning if necessary
					context.audioMap.put("VerifyTelephoneAL", context.audioMap.get("VerifyTelephone"));
					onNomatch();
				} else {
					log.debug("Failed twice with enrolled number - probably an impostor");
					addPrompt(TRANSFER_MESSAGE);
					doHangup();
				}
			} 
		} else {
			// Not enrolled?
			addPrompt(new Prompt($("4"), "Your number is not registered with us. To open a new account, just answer a few questions so that we can enrol you."));
			
			setId(callerId);
			gotoState(new EnrolName());
		}
		
	}

	@SneakyThrows
	private void checkFraudList() {
		String fraudListPath = context.getConfig().getString("fraudlist_path", "fraud_list.txt");
		List<String> fraudList = FileUtils.readLines(new File(fraudListPath), "UTF-8");
		
		
		
		if (!fraudList.isEmpty()) {
			List<Pair<String,Double>> fraudScores = context.getArmorvox().verifyList(getGroup(), PRINT_2, fraudList, context.audioMap.get("VerifyTelephone"));
			fraudScores.removeIf(p -> p.getRight() > context.getConfig().getDouble("fraud_threshold", 2.0));
			//fraudScores.removeIf(p -> p.getLeft().equals(callerId));
			if (!fraudScores.isEmpty()) {
				// we have found potential fraudsters
				// send SMS
				String number = StringUtils.getDigits(context.map.get("session.callerid"));
				String[] ids = fraudScores.stream().map(p -> p.getLeft()).toArray(String[]::new);
				String message = "Potential Fraudsters: "+StringUtils.join(ids);
				log.debug("{}", message);
				//if (number.startsWith("614") || number.startsWith("04") ) {
					sendSMS(number, message);
				//} else {
				//	log.debug("Calling ID is not a mobile. No SMS sent. Message={}", message);
				//}
			}
		}
	}

}
