package com.auraya.state;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.auraya.api.Response;

import lombok.SneakyThrows;

public class EnrolTwoByFour3 extends BankState {

	@Override
	protected void onAttempt() {
		

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), "Finally Please say, ", 250));
			//addPrompt(new Prompt($("2"), "five zero four three"));
			//addPrompt(new Prompt($("3"), true, "five zero four three."));
			addPrompt(new Prompt($("2"), "three seven nine eight"));
			addPrompt(new Prompt($("3"), true, "three seven nine eight. "));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("4"), "Please repeat back the following eight digits.", 250));
			addPrompt(new Prompt($("2"), "three seven nine eight"));
			addPrompt(new Prompt($("3"), true, "three seven nine eight. "));
			//addPrompt(new Prompt($("2"), "five zero four three"));
			//addPrompt(new Prompt($("3"), true, "five zero four three."));
		}

		doGrammar(new Grammar(Grammar.Type.Digits));
	}
	
	protected void onFilled() {
		writeAudio();
		
		String state = this.getClass().getSimpleName();
		
		String spokenId = getSpokenId(state);
		if (spokenId.length() < 7) {
			onNomatch();
		} else {
			
			sendData("enrol", PRINT_2, state, getSpokenId(state,false)) ;
			

			String id = getId();
			
			List<byte[]> digitUtterances = getUtterances("VerifyTelephone","EnrolTelephone1", "EnrolOneToNine", "EnrolTwoByFour1", "EnrolTwoByFour3");
			List<byte[]> tpdUtterances =  getUtterances("VerifyTelephone","EnrolTelephone1", "EnrolOneToNine", "EnrolTwoByFour1", "EnrolTwoByFour3");
			
			writeAudioMap();

			Response response = context.getArmorvox().enrol(getGroup(), PRINT_1, id, tpdUtterances);
			if (response.isOk()) {
				
				response = context.getArmorvox().enrol(getGroup(), PRINT_2, id, digitUtterances);
				if (response.isOk()) {
					addPrompt(new Prompt($("5"), "You have been successfully registered. Call back again to access your account."));
					doHangup();
					String acmeNumber = context.getConfig().getString("demo_number","0289994431");
					String spokenNumber = context.map.getOrDefault("spoken_number", id);

					String website = context.map.getOrDefault("website", "");
					String message = String.format("Thank you for enrolling with Acme Bank. Your ID is %s. Call %s again to access your account. %s", spokenNumber, acmeNumber, website);
					String number = StringUtils.getDigits(context.map.get("session.callerid"));
					//if (number.startsWith("614")) {
						sendSMS(number, message);
					//} else {
					//	log.debug("Calling ID is not a mobile. No SMS sent. Message={}", message);
					//}
				} else {
					context.getArmorvox().delete(getGroup(), PRINT_1, id);
					context.getArmorvox().delete(getGroup(), PRINT_2, id);
					addPrompt(TRANSFER_MESSAGE);
					doHangup();
				}
			} else {
				context.getArmorvox().delete(getGroup(), PRINT_1, id);
				context.getArmorvox().delete(getGroup(), PRINT_2, id);
				addPrompt(TRANSFER_MESSAGE);
				doHangup();
			}
		}
	}

	@SneakyThrows
	private void writeAudioMap() {
		
		File userDir = new File("users/"+getId());
		for (Map.Entry<String,byte[]> e : context.audioMap.entrySet()) {
			FileUtils.writeByteArrayToFile(new File(userDir,e.getKey()+".wav"), e.getValue());
		}
	}
	
	List<byte[]> getUtterances(String... utteranceNames) {
		List<byte[]> list = new ArrayList<>();
		for (String utteranceName : utteranceNames) {
			byte[] utterance = context.audioMap.get(utteranceName);
			if (utterance != null) {
				list.add(utterance);
			}
		}
		return list;
	}
	
	
}
