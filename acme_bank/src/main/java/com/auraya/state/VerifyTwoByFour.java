package com.auraya.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;

import com.auraya.State;
import com.auraya.api.Response;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VerifyTwoByFour extends BankState {

	private State nextState;
	private List<String> numbers = new ArrayList<>();

	public VerifyTwoByFour(State nextState) {
		this.nextState = nextState;
	}
	
	@Override
	protected void onAttempt() {
		Random random = new Random();
		String[] array = new String[]{"one","two","three","four","five","six","seven","eight","nine"};
		numbers.clear();
		List<String> prompts1 = new ArrayList<>();
		List<String> prompts2 = new ArrayList<>();
		String[] place1 = new String[]{"a","b","e","f"};
		String[] place2 = new String[]{"g","e","h","i"};
		
		for (int i = 0; i < 4; i++) {
			int n = random.nextInt(array.length);
			String num = array[n];
			while (numbers.contains(num)) {
				n = random.nextInt(array.length);
				num = array[n];
			}
			String nString = ""+(n+1);
			prompts1.add("digits/"+nString+place1[i]);
			prompts2.add("digits/"+nString+place2[i]);
			numbers.add(num);
		}
		
		addPrompt(new Prompt($("1"), "Please repeat back the following eight digits. ", 250));
		
		
		addPrompt(new Prompt(StringUtils.join(prompts1, " "), StringUtils.join(numbers, " ")));
		addPrompt(new Prompt(StringUtils.join(prompts2, " "), true, StringUtils.join(numbers, " ")+"."));
		
		//doGrammar(new Grammar(StringUtils.join(numbers, " ") +" " +StringUtils.join(numbers, " ")));
		doGrammar(new Grammar(Grammar.Type.Digits));
	}
	
	@Override
	protected void onFilled() {
		writeAudio();
		
		String id = getId();
		String phrase = StringUtils.join(numbers, " ") + " " + StringUtils.join(numbers, " ");
		sendData("verify", PRINT_2, "VerifyTwoByFour", phrase) ;
		
		Double alRate = context.getConfig().getDouble("alRate.random", 1.0);
		Double alRateForce = context.getConfig().getDouble("alRate.random.force", 10.0);
		Double faRate = context.getConfig().getDouble("faRate.random", 1.0);
		
		
		Response verifyResponse = context.getArmorvox().verify(getGroup(), PRINT_2, id, alRate, phrase, context.audioMap.get("VerifyTwoByFour"));
		
		log.debug("impProb={} alRate={}, faRate={}", verifyResponse.getImpProb(), alRate, faRate);
		
		if (verifyResponse.getImpProb() <= faRate) {
			// All good! Active Learning?
			
			if (context.audioMap.get("VerifyTwoByFourAL") != null) {
				context.getArmorvox().verify(getGroup(), PRINT_2, id, alRateForce, null, context.audioMap.get("VerifyTwoByFourAL"));
			}
			setLevel(3);
			gotoState(nextState);
			return;
		}
		
		if (!verifyResponse.isEnrolled()) {
			log.warn("4x2 is not enrolled!! ERROR");
			addPrompt(TRANSFER_MESSAGE);
			doHangup();
			return;
		}
		
		if (getLocalCounter("attempt").intValue() == 1) {
			
			// first failure can be counted as a nomatch
			context.audioMap.put("VerifyTwoByFourAL", context.audioMap.get("VerifyTwoByFour"));
			onNomatch();
			return;
		} 
		
		log.warn("Failed twice. Probably an impostor...");
		addPrompt(TRANSFER_MESSAGE);
		doHangup();
		return;
	}
	

}
