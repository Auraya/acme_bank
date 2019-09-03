package com.auraya.state;


import java.io.InputStream;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.SneakyThrows;

public class EnrolName extends BankState {

	@SneakyThrows
	@Override
	protected void onAttempt() {

		if (getLocalCounter("attempt").intValue() == 0) {
			addPrompt(new Prompt($("1"), true, "Now please say your full name."));
		}
		
		if (getLocalCounter("attempt").intValue() > 0) {
			addPrompt(new Prompt($("2"), true, "Please say your first and last name."));
		}
		
		setProperty("confidencelevel", "0.0");
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("name_grammar.txt")) {
			String nameGrammar = IOUtils.toString(is, "UTF-8");
			doGrammar(new Grammar(nameGrammar));
		}
		
		//doRecord("5s", "1s", false, false);
	}
	
	@SneakyThrows
	@Override
	protected void onFilled() {
		//@SuppressWarnings("unused")
		byte[] data = writeAudio();
		String mobile = getSpokenId("VerifyTelephone", false);

		String nuanceName = getUtterance();
		
		Executors.newSingleThreadExecutor().submit(() -> {
			String name = context.getProxy().syncRecognizeFile(data);
			if (StringUtils.isEmpty(name)) {
				name = nuanceName;
			}
			context.getProxy().saveUser(getId(), getGroup(), mobile, name);
		});
		
		gotoState(new EnrolOneToNine());
	}
	
	
}
