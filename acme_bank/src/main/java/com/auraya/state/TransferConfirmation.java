package com.auraya.state;


import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransferConfirmation extends BankState {

	private String amount;

	public TransferConfirmation(String amount) {
		this.amount = amount;
	}
	
	@Override
	protected void onAttempt() {
		
		double dollarsAndCentsToTransfer = convertAmount(amount);
		long roundedCents = Math.round(100*(dollarsAndCentsToTransfer - Math.floor(dollarsAndCentsToTransfer)));
		String dollars = ""+Math.floor(dollarsAndCentsToTransfer);
		String cents = ""+roundedCents;

		List<String> prompts = makeAmountPrompts(dollars, cents);

		
		context.getGlobalCounter("TransferConfirmation.attempt").increment();
		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), "Was that"));
			addPrompt(new Prompt(StringUtils.join(prompts, " "), true, amount));
		} else {
			addPrompt(new Prompt($("2"), false, "Yes or no. ")); 
			addPrompt(new Prompt($("1"), "Was that"));
			addPrompt(new Prompt(StringUtils.join(prompts, " "), true, amount));
		}
		doGrammar(new Grammar(Grammar.Type.Boolean));
	}
	
	@Override
	protected void onFilled() {
		writeAudio();
		String utterance = getUtterance();
		
		if (utterance.contains("right") || utterance.contains("yes") || utterance.contains("okay") || utterance.contains("yeah") || utterance.contains("yep")) {
			
			int level = getLevel();
			log.debug("amount="+amount);
			double dollarsAndCentsToTransfer = convertAmount(amount);
			double dollarsAndCentsInBalance = Double.valueOf(context.map.get("dollars")) + (Double.valueOf(context.map.get("cents")) / 100);
			log.debug("dollarsAndCentsToTransfer={} dollarsAndCentsInBalance={}");
			if (dollarsAndCentsInBalance < dollarsAndCentsToTransfer) {
				addPrompt(new Prompt($("3"), "You have insufficient funds to transfer that amount. "));
				gotoState(new AccountBalance());
				return;
			}
			
			if (level < 3 && dollarsAndCentsToTransfer >= 1000) {
				addPrompt(new Prompt($("4"), "Such a large transfer amount requires another security question. "));
				gotoState(new VerifyTwoByFour(new TransferSuccess(amount)));
			} else if (level < 2) {
				addPrompt(new Prompt($("5"), "Transferring money to a third party requires an additional security question. "));
				gotoState(new VerifyTwoByFour(new TransferSuccess(amount)));
			} else {
				gotoState(new TransferSuccess(amount));
			}
		} else {
			if (context.getGlobalCounter("TransferConfirmation.attempt").intValue() > 3) {
				addPrompt(TRANSFER_MESSAGE);
				doHangup();
				return;
			};
			gotoState(new TransferMoney());
		}
	}
	
}
