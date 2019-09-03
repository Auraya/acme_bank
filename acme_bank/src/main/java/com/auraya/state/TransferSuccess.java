package com.auraya.state;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class TransferSuccess extends BankState {
	
	String amount;

	public TransferSuccess(String amount) {
		this.amount = amount;
	}
	
	@Override
	protected void onStart() {
		
		double dollarsAndCentsToTransfer = convertAmount(amount);
		double dollarsAndCentsInBalance = Double.valueOf(context.map.get("dollars")) + (Double.valueOf(context.map.get("cents")) / 100);
		
		dollarsAndCentsInBalance -= dollarsAndCentsToTransfer;
		long roundedCents = Math.round(100*(dollarsAndCentsInBalance - Math.floor(dollarsAndCentsInBalance)));
		context.map.put("dollars", ""+Math.floor(dollarsAndCentsInBalance));
		context.map.put("cents", ""+roundedCents);
		
		addPrompt(new Prompt($("1"), "You have successfully transferred "));
		
		roundedCents = Math.round(100*(dollarsAndCentsToTransfer - Math.floor(dollarsAndCentsToTransfer)));
		String dollars = ""+Math.floor(dollarsAndCentsToTransfer);
		String cents = ""+roundedCents;

		List<String> prompts = makeAmountPrompts(dollars, cents);

		addPrompt(new Prompt(StringUtils.join(prompts, " "), amount));
		gotoState(new ChooseService());
	}

}
