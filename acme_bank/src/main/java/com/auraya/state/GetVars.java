package com.auraya.state;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetVars extends BankState {
	
	@Override
	protected void onStart() {
		
		setVar("phone", "session.telephone.ani");
		setVar("dnis", "session.telephone.dnis");
		
		doBlock();
	}
	
	@Override
	protected void onFilled() {

		String phone = context.map.get("phone");
		String dnis = context.map.get("dnis");
		
		log.debug("phone={} dnis={}", phone, dnis);
		
		context.map.put("session.callerid", phone);
		context.map.put("session.calledid", dnis);
		
		gotoState(new EnterPasscode());
	}
	
}
