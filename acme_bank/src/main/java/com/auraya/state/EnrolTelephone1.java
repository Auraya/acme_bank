package com.auraya.state;

import lombok.SneakyThrows;

public class EnrolTelephone1 extends BankState {
	
	@Override
	protected void onStart() {
		addPrompt(new Prompt($("1"), "During the enrolment process, we will collect some samples of your voice. Now, "));
		super.onStart();
	}
	
	@SneakyThrows
	@Override
	protected void onAttempt() {
		
		addPrompt(new Prompt($("2"), true, "Please say your telephone number."));
		int minLen = context.getConfig().getInt("id_min_len", 8);
		int maxLen = context.getConfig().getInt("id_max_len", 11);
		doGrammar(getNumberGrammar(minLen, maxLen, getId()));
	}
	
	@Override
	protected void onFilled() {
		writeAudio();

		String state = this.getClass().getSimpleName();
		sendData("enrol", PRINT_1, state, getSpokenId(state,false)) ;
		gotoState(new EnrolName());
	}
	
}
