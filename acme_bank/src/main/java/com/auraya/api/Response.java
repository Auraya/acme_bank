package com.auraya.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors
@AllArgsConstructor
@Getter
public class Response {
	boolean ok = false;
	boolean enrolled = false;
	String reason;
	double impProb = 0;
	
	
}