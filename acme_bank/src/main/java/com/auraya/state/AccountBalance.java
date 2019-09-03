package com.auraya.state;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountBalance extends BankState {

	@Override
	protected void onStart() {
		log.debug("ENTER");
		
		String dollars = context.map.get("dollars");
		String cents = context.map.get("cents");
		
		List<String> prompts = makeAmountPrompts(dollars, cents);
		addPrompt(new Prompt($("1"), "Your account balance is"));
		addPrompt(new Prompt(StringUtils.join(prompts, " "), String.format("%s dollars and %s cents.", dollars, cents)));

		gotoState(new ChooseService());
	}



}
