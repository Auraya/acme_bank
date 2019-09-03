package com.auraya.api;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;


public interface ArmorVox {

	Response isEnrolled(String group, String printName, String id);

	Response enrol(String group, String type, String id, List<byte[]> utterances);

	Response verify(String group, String printName, String id, Double alRate, String phrase, byte[] utterance);

	Response delete(String group, String printName, String id);

	List<Pair<String, Double>> verifyList(String group, String printName, List<String> list, byte[] utterance);
	
	String getChannel();
}