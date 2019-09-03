package com.auraya.state;



import lombok.SneakyThrows;

public class EnrolTelephone3 extends BankState {
	
	@SneakyThrows
	@Override
	protected void onAttempt() {

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), true, "Please say your telephone number one last time."));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("2"), true, "Please say your telephone number."));
		}
		
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
