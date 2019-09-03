package com.auraya.state;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnterPasscode extends BankState {

	
	@Override
	protected void onAttempt() {
		
		addPrompt(new Prompt($("1"), "Please enter your passcode using the keypad."));
		doGrammar(new Grammar(Grammar.Type.Digits, Grammar.Mode.DTMF, "length=4"));
	}
	
	@Override
	protected void onFilled() {

		String dtmfPasscode = getSpokenId("EnterPasscode");
		log.debug("dtmfPasscode={}", dtmfPasscode);
		if (dtmfPasscode.length() != context.getConfig().getInt("passcode_len", 4)) {
			onNomatch();
			return;
		}
		
		
		String group = context.appMap.get(dtmfPasscode);
		log.debug("dtmfGroup={}", group);
		if (group == null) {
			onNomatch();
			return;
		}
		
		context.map.put("group", group);
		gotoState(new Start());
	}


}
