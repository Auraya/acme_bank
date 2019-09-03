package com.auraya.state;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class ActivateCard extends BankState {
	
	@Override
	protected void onAttempt() {
		addPrompt(new Prompt($("1"), true, "Please say the last four digits of the card. "));
		doGrammar(new Grammar(Grammar.Type.Digits));
	}
	
	@Override
	protected void onFilled() {
		writeAudio();
		String id = getSpokenId("ActivateCard");
		if (id.length() != 4) {
			onNomatch();
			return;
		}
		addPrompt(new Prompt($("2"), "The card ending in"));
		char[] cs = id.toCharArray();
		String[] place = new String[]{"a","e","h","i"};
		List<String> prompts = new ArrayList<>();
		for (int i = 0; i < cs.length; i++) {
			prompts.add("digits/"+cs[i]+place[i]);
		}
		addPrompt(new Prompt(StringUtils.join(prompts, " "), getUtterance()));
		addPrompt(new Prompt($("3"), "has been activated. "));
		
		
		gotoState(new ChooseService());
	}

}
