package com.auraya.state;



import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransferMoney extends BankState {
	
	
	@Override
	protected void onStart() {
		addPrompt(new Prompt($("1"), "You may transfer money to a third-party. "));
		super.onStart();
	}

	@Override
	protected void onAttempt() {
		addPrompt(new Prompt($("2"), true, "Please say how much you would like to transfer. "));
		//setProperty("confidencelevel", "0.1");
		doGrammar(new Grammar(Grammar.Type.Currency));
	}
	
	//@Override
	protected void onFilledWithConfirmation() {
		writeAudio();
		
		String amount = context.map.get("TransferMoney$.utterance");
		
		log.debug("amount={}",amount);
		gotoState(new TransferConfirmation(amount));
	}
	
	
	@Override
	protected void onFilled() {
		writeAudio();
		String amount = context.map.get("TransferMoney$.utterance");
		
		int level = getLevel();
		log.debug("amount="+amount);
		double dollarsAndCentsToTransfer = convertAmount(amount);
		double dollarsAndCentsInBalance = Double.valueOf(context.map.get("dollars")) + (Double.valueOf(context.map.get("cents")) / 100);
		log.debug("dollarsAndCentsToTransfer={} dollarsAndCentsInBalance={}");
		if (dollarsAndCentsInBalance < dollarsAndCentsToTransfer) {
			addPrompt(new Prompt("TransferConfirmation_3", "You have insufficient funds to transfer that amount. "));
			gotoState(new AccountBalance());
			return;
		}
		
		if (level < 3 && dollarsAndCentsToTransfer >= 1000) {
			addPrompt(new Prompt("TransferConfirmation_4", "Such a large transfer amount requires another security question. "));
			gotoState(new VerifyTwoByFour(new TransferSuccess(amount)));
		} else if (level < 2) {
			addPrompt(new Prompt("TransferConfirmation_5", "Transferring money to a third party requires an additional security question. "));
			gotoState(new VerifyTwoByFour(new TransferSuccess(amount)));
		} else {
			gotoState(new TransferSuccess(amount));
		}
		
	}
}
