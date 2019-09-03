package com.auraya.state;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeletePrint extends BankState {


	@Override
	protected void onAttempt() {
		addPrompt(new Prompt($("1"),"You have chosen to delete your voice print. Are you sure?"));
		doGrammar(new Grammar("yes | no"));
	}
	
	@Override
	protected void onFilled() {
		writeAudio();
		String utterance = getUtterance();
		
		try {
			if (utterance.contains("yes")) {
				context.getArmorvox().delete(getGroup(), PRINT_1, getId());
				context.getArmorvox().delete(getGroup(), PRINT_2, getId());
				addPrompt(new Prompt($("2"), "Your print was successfully deleted. Please call again."));
				doHangup();
			} else {
				gotoState(new ChooseService());
			}
		} catch (Exception e) {
			log.error("Exception", e);
			addPrompt(new Prompt($("2"), "There was a error while deleting your print."));
			gotoState(new ChooseService());
		}
	}
}
