package com.auraya.state;


import org.apache.commons.lang3.StringUtils;

import lombok.SneakyThrows;

public class EnrolOneToNine extends BankState {
	
	@Override
	protected void onStart() {
		context.map.put("enrolment_utterances", StringUtils.join(context.audioMap.keySet(),","));
		super.onStart();
	}
	 
	@SneakyThrows
	@Override
	protected void onAttempt() {

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), false, "Now, Please count from one to nine."));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("2"), "Please repeat the following nine digits. ", 250));
			addPrompt(new Prompt($("3"), true, "one two three four five six seven eight nine."));
		}
		doGrammar(new Grammar("one two three four five six seven eight nine"));
	}
	
	protected void onFilled() {
		writeAudio();

		String state = this.getClass().getSimpleName();
		sendData("enrol", PRINT_1, state, getSpokenId(state,false)) ;
		gotoState(new EnrolTwoByFour1());
	}

}
