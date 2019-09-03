package com.auraya.state;

public class EnrolTwoByFour1 extends BankState {
	
	@Override
	protected void onAttempt() {

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), "Next we'll ask you to repeat back a few random digits. Now, Please say", 250));
			addPrompt(new Prompt($("2"), "four two six one"));
			addPrompt(new Prompt($("3"), true, "four two six one. "));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("4"), "Please repeat back the following eight digits. ", 250));
			addPrompt(new Prompt($("2"), "four two six one"));
			addPrompt(new Prompt($("3"), true, "four two six one. "));
		}
		
		doGrammar(new Grammar(Grammar.Type.Digits));
	}
	
	protected void onFilled() {
		writeAudio();
		String state = this.getClass().getSimpleName();
		
		String id = getSpokenId(state);
		if (id.length() < 7) {
			onNomatch();
		} else {
			sendData("enrol", PRINT_2, state, getSpokenId(state,false)) ;
			
			gotoState(new EnrolTwoByFour3());
		}
	}

}
