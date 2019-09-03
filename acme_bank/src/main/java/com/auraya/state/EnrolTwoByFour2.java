package com.auraya.state;

public class EnrolTwoByFour2 extends BankState {
	
	@Override
	protected void onAttempt() {
		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), "Next Please say, ", 250));
			addPrompt(new Prompt($("2"), "three seven nine eight"));
			addPrompt(new Prompt($("3"), true, "three seven nine eight. "));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("4"), "Please repeat back the following eight digits. ", 250));
			addPrompt(new Prompt($("2"), "three seven nine eight"));
			addPrompt(new Prompt($("3"), true, "three seven nine eight. "));
		}

		doGrammar(new Grammar(Grammar.Type.Digits));
	}
	
	protected void onFilled() {
		String state = this.getClass().getSimpleName();
		
		writeAudio();
		String id = getSpokenId(state);
		if (id.length() < 7) {
			onNomatch();
		} else {
			sendData("enrol", PRINT_2, state, getSpokenId(state,false)) ;
			
			gotoState(new EnrolTwoByFour3());
		}
	}

}
