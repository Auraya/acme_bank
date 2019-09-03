package com.auraya.state;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;


public class Start extends BankState {
	
	@Override
	protected void onStart() {

		//addPrompt(new Prompt(2000));
		addPrompt(new Prompt($("1"),"Welcome to ArmorVox Bank."));
		addPrompt(new Prompt($("2"),"This call is being recorded for quality purposes."));
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		addLog(String.format("call_start=%s", sdf.format(new Date())));
		addLog("callerid="+getId());
		
		
		Random random = new Random();
		context.map.put("dollars", ""+(random.nextInt(8000)+2000));
		//context.map.put("cents", ""+(random.nextInt(100)));
		context.map.put("cents", "0");
		
		gotoState(new VerifyTelephone());
	}

}
