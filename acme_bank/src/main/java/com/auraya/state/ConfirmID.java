package com.auraya.state;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.auraya.api.Response;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfirmID extends BankState {

	String id;
	
	public ConfirmID(String id) {
		this.id = id;
	}
	
	@Override
	protected void onAttempt() {
		
		addPrompt(new Prompt($("1"), "Please enter your telephone number using the keypad."));
		doGrammar(new Grammar(Grammar.Type.Digits, Grammar.Mode.DTMF));
	}
	
	@Override
	protected void onFilled() {

		String dtmfID = getSpokenId("ConfirmID");
		log.debug("dtmfID={}", dtmfID);
		if (dtmfID.length() < context.getConfig().getInt("id_min_len", 8)) {
			onNomatch();
			return;
		}
		context.map.put("spoken_number", getSpokenId("ConfirmID", false));
		
		setId(dtmfID);
		
		
		Response isEnrolled = context.getArmorvox().isEnrolled(getGroup(),PRINT_1, dtmfID);
		if (isEnrolled.isOk()) {

			log.debug("is enrolled");
			if (StringUtils.equals(getId(), id)) {
				// this means they said their telephone number, and we recognised them, they just failed biometrics
				addPrompt(TRANSFER_MESSAGE);
				doHangup();
			} else {
				

				Double alRate = context.getConfig().getDouble("alRate.phone", 0.1);
				Double faRate = context.getConfig().getDouble("faRate.phone", 0.1);
				
				
				// verify all the tries again until they succeed (in reverse order)
				Response verifyResponse1 = context.getArmorvox().verify(getGroup(), PRINT_1, dtmfID, alRate, null, context.audioMap.get("VerifyTelephone"));
				
				log.debug("impProb={} alRate={}, faRate={}", verifyResponse1.getImpProb(), alRate, faRate);
				
				
				if (verifyResponse1.getImpProb() <= faRate){
					gotoState(new VerifyTwoByFour(new AccountBalance()));
				} else {
					Response verifyResponse2 = context.getArmorvox().verify(getGroup(), PRINT_1, dtmfID, alRate, null, context.audioMap.get("VerifyTelephoneAL"));
					
					log.debug("impProb={} alRate={}, faRate={}", verifyResponse2.getImpProb(), alRate, faRate);
					
					
					if (verifyResponse2.getImpProb() <= faRate) {
						gotoState(new VerifyTwoByFour(new AccountBalance()));
					} else {
						// this means they said their telephone number, and we recognised them, they just failed biometrics
						addPrompt(TRANSFER_MESSAGE);
						doHangup();
					}
				}
			}
		} else {
			// Not enrolled?
			log.debug("not enrolled");
			
			List<String> prompts = new ArrayList<>();

			addPrompt(new Prompt($("2"),"Telephone Number"));
			char[] cs = StringUtils.getDigits(getUtterance()).toCharArray();
			String[] place = new String[]{"a", "b", "b", "c", "d", "e", "f", "g", "h", "i"};
			for (int i = 0; i < cs.length-3; i++) {
				prompts.add("digits/"+Character.toString(cs[i]) + place[i]);
			}
			for (int i = cs.length-3, j = 3; i < cs.length; i++, j--) {
				prompts.add("digits/"+Character.toString(cs[i]) + place[place.length-j]);
			}
			
			addPrompt(new Prompt(StringUtils.join(prompts, " "), getUtterance()));		
			addPrompt(new Prompt($("3"), " is not registered with us. To open a new account, just answer a few questions so that we can enrol you."));
			
			
			if (context.audioMap.size() == 1) {
				gotoState(new EnrolName());	
			} else {
				gotoState(new EnrolTelephone1());
			}
		}
			
	}


}
