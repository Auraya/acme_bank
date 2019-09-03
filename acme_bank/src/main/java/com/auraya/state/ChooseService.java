package com.auraya.state;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;

import lombok.SneakyThrows;

public class ChooseService extends BankState {
	
	@Override
	protected void onAttempt() {

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), "You may hang up at any time. "));
			addPrompt(new Prompt($("2"), true, "Would you like to hear your balance, transfer money, or activate a card. "));
		} else {
			addPrompt(new Prompt($("3"), "When you are finished, you may hang up. "));
			addPrompt(new Prompt($("4"), true, "To hear your balance say 'balance', to transfer money say 'transfer', to activate a card say 'activate'. "));
		}
		
		doGrammar(new Grammar("[i'd like to] ([hear] [my] balance [again] | transfer [[some] money] | activate [[a] card] | delete [my [voice] print] | add to fraud list | remove from fraud list)"));
		//doGrammar(new Grammar("balance | transfer | activate "));
		
		//doGrammar(new Grammar("[  ( (i'd like to)? (hear? my? balance again?) )  (transfer (some? money)? )  (activate (a? card)? )  (delete (my voice? print)? ) (add to fraud list) (remove from fraud list) ]"));
		//doGrammar(new Grammar("<one-of><item>balance</item><item>transfer</item><item>activate</item></one-of>"));

	}
	
	@SneakyThrows
	@Override
	protected void onFilled() {
		writeAudio();
		String utterance = getUtterance();
		int level = getLevel();
		
		if (utterance.contains("transfer")) {
			
			if (level < 2) {
				addPrompt(new Prompt($("5"), "To transfer to a third party you will need to answer another security question."));
				gotoState(new VerifyTwoByFour(new TransferMoney()));
			} else {
				gotoState(new TransferMoney());
			}
			return;
		}
		
		if (utterance.contains("balance")) {
			gotoState(new AccountBalance());
			return;
		}
		
		if (utterance.contains("activate")) {
			if (level < 2) {
				addPrompt(new Prompt($("6"), "To activate a card you will need to answer another security question."));
				gotoState(new VerifyTwoByFour(new ActivateCard()));
			} else {
				gotoState(new ActivateCard());
			}
			return;
		}
		

		if (utterance.contains("delete")) {
			gotoState(new DeletePrint());
			return;
		}
		
		if (utterance.contains("add")) {
			
			String fraudListPath = context.getConfig().getString("fraudlist_path", "fraud_list.txt");
			File fraudFile = new File(fraudListPath);
			List<String> fraudList = FileUtils.readLines(fraudFile, "UTF-8");
			if (!fraudList.contains(getId())) {
				fraudList.add(getId());
				FileUtils.writeLines(fraudFile, fraudList);
			}
			
			addPrompt(new Prompt("This account was added to the fraud list."));
			gotoState(new ChooseService());
			return;
		}
		

		if (utterance.contains("remove")) {
			String fraudListPath = context.getConfig().getString("fraudlist_path", "fraud_list.txt");
			File fraudFile = new File(fraudListPath);
			List<String> fraudList = FileUtils.readLines(fraudFile, "UTF-8");
			while (fraudList.remove(getId())) {
				FileUtils.writeLines(fraudFile, fraudList);
			}
			
			addPrompt(new Prompt("This account was removed from the fraud list."));
			gotoState(new ChooseService());
			return;
		}
		
		
		
		onNomatch();
	}
	
	

}
