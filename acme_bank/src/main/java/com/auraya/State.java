package com.auraya;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;

import com.auraya.api.ArmorVox;
import com.auraya.grammar.ABNFCompiler;
import com.auraya.grammar.AbstractGrammar;
import com.auraya.grammar.Node;
import com.auraya.grammar.Node.NodeType;
import com.auraya.proxy.IProxyClient;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class State {
	

	public enum Format {
		ABNF,
		GRXML;
	}
	
	
	protected static class Grammar {
		
		public enum Mode {
			DTMF("dtmf"),
			VOICE("voice"),
			BOTH("dtmf voice");
			
			
			private String mode = null;
			private Mode(String mode) {
				this.mode = mode;
			}
			
		}
		

		
		public enum Type {
			Digits("digits"),
			Date("date"),
			Boolean("boolean"),
			Currency("currency"),
			Number("number"),
			Phone("phone"),
			Time("time");
			
			private String type = null;
			private Type(String type) {
				this.type = type;
			}
			
		}
		public Grammar(Type type) {
			this.type = type.type;
		}
		
		
		public Grammar(Type type, Mode mode, String args) {
			this.type = type.type + "?" + args;
			this.mode = mode.mode;
		}
		
		public Grammar(Type type, Mode mode) {
			this.type = type.type;
			this.mode = mode.mode;
		}
		
		public Grammar(String text) {
			this.text = text;
		}
		
		public Grammar(String src, boolean isSource) {
			this.src = src;
		}

		String type = null;
		String src = null;
		String text = null;

		String mode = Mode.VOICE.mode;
	}
	
	
	protected class Prompt {
	
		String text;
		String src;
		String id = getStateName();
		int pause = 0;
		boolean bargable = false;
		
		public Prompt(String id, String text) {
			this.text = text;
			this.id = id;
		}
		
		public Prompt(String text) {
			this.text = text;
		}
		
		public Prompt(boolean bargable, String text) {
			this.bargable = bargable;
			this.text = text;
		}

		public Prompt(String id, boolean bargable, String text) {
			this.id = id;
			this.bargable = bargable;
			this.text = text;
		}
		
		public Prompt(String text, int pause) {
			this.text = text;
			this.pause = pause;
		}	
		
		public Prompt(String id, String text, int pause) {
			this.id = id;
			this.text = text;
			this.pause = pause;
		}
		
		public Prompt(int pause) {
			super();
			this.pause = pause;
		}
		
		public Prompt(String id, boolean bargable, String text, int pause) {
			this.id = id;
			this.bargable = bargable;
			this.text = text;
			this.pause = pause;
		}
		
		public Prompt(String id, boolean bargable, String contour, String text) {
			this.id = id;
			this.bargable = bargable;
			this.text = text;
		}
		
	}
	
	static protected class Context {
		

		private ABNFCompiler compiler = new ABNFCompiler();
		
		ArmorVox armorvox = null;
		IProxyClient proxy = null;
		OutputStream outputStream;
		State currentState = null;
		private List<Prompt> prompts = new ArrayList<>();
		private Map<String,LongAdder> globalCounters = new HashMap<>();
		public Map<String,String> map = new HashMap<>();
		public Map<String,String> appMap = new HashMap<>();
		public Map<String,byte[]> audioMap = new HashMap<>();

		public Map<String,String> phraseMap = new HashMap<>();
		public List<String> logList = new ArrayList<>();
		
		Configuration config;
		
		public Context(State state, ArmorVox armorvox, IProxyClient proxy, Configuration config, Map<String,String> appMap) {
			this.currentState = state;
			this.armorvox = armorvox;
			this.proxy = proxy;
			this.config = config;
			state.context = this;
			this.appMap = appMap;
		}
		
		public Configuration getConfig() {
			return config;
		}
		
		
		public ABNFCompiler getCompiler() {
			return compiler;
		}
		
		public LongAdder getGlobalCounter(String name) {
			return globalCounters.computeIfAbsent(name, k -> new LongAdder());
		}
		
		public ArmorVox getArmorvox() {
			return armorvox;
		}
		
		public IProxyClient getProxy() {
			return proxy;
		}
		
		public Format getGrammarFormat() {
			return Format.valueOf(config.getString("grammar_format", "GRXML"));
		}

	}

	static final XMLOutputFactory xof = getXMLOutputFactory();
	
	protected Context context = null;
	protected Map<String,LongAdder> localCounters = new HashMap<>();
	protected Map<String,String> vxmlProperties = new HashMap<>();
	protected Map<String,String> vxmlVars = new HashMap<>();
	
	
	protected LongAdder getLocalCounter(String name) {
		return localCounters.computeIfAbsent(name, k -> new LongAdder());
	}
	

	protected String getStateName() {
		return getClass().getSimpleName();
	}

	protected void addPrompt(Prompt prompt) {
		context.prompts.add(prompt);
	}
	
	public State() {
			
		setProperty("recordutterance", "true");
		setProperty("recordutterancetype", "audio/wav");
		setProperty("confidencelevel", "0.1");
	}

    private static XMLOutputFactory getXMLOutputFactory() {
		XMLOutputFactory xof = XMLOutputFactory.newInstance();
		xof.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
		return xof;
	}

	public void gotoState(State state) {
		context.currentState = state;
		state.context = context;
		log.debug("Going to state {}", state.getStateName());
		state.onEvent("start");
	}

	public void onEvent(String event) {
		
		getLocalCounter(event).increment();
		context.getGlobalCounter(event).increment();

		switch (event) {
		case "filled": 
		case "noinput": 
		case "nomatch":
			getLocalCounter("attempt").increment();
			context.getGlobalCounter("attempt").increment();
		}
		
		
		switch (event) {
		case "start": onStart(); break;
		case "filled": onFilled(); break;
		case "noinput": onNoinput(); break;
		case "nomatch": onNomatch(); break;
		case "hangup":
		case "connection.disconnect.hangup": onHangup(); break;
		default: onError();
		}

	}
	
	protected void onFilled() {
		doHangup();
	}

	protected void onStart() {
		onAttempt();
	}

	protected void onAttempt() {
		doHangup();
	}

	protected void onNomatch() {
		onAttempt();
	}

	protected void onNoinput() {
		onAttempt();
	}

	protected void onHangup() {
		doHangup();
	}
	

	protected void onError() {
		doHangup();
	}
	
	protected String getUtterance() {
		return getUtterance(this.getClass().getSimpleName());
	}
	
	protected String getUtterance(String stateName) {
		String result = context.map.get(String.format("%s$.utterance", stateName));
		log.debug("stateName={} utterance={}",stateName,result);
		return result;
	}

	
	protected void setProperty(String name, String value) {
		vxmlProperties.put(name, value);
	}
	
	protected void setVar(String name, String expr) {
		vxmlVars.put(name, expr);
	}

	@SneakyThrows
	protected void doGrammar(Grammar grammar) {

		log.debug("ENTER");
		
		AtomicLong indent = new AtomicLong();
		try {
			XMLStreamWriter xtw = startVxml();
			
			indent.incrementAndGet();
			
			startEl(indent, xtw, "catch", "event", "connection.disconnect.hangup");
			writeVarQuote(indent, xtw, "event", "hangup");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");
			endEl(indent, xtw);
			
			startEl(indent, xtw, "form", "id", "Main");

			writeEl(indent, xtw, "property", "name", "imputmodes", "value", grammar.mode);
			for (Map.Entry<String, String> e : vxmlProperties.entrySet()) {
				writeEl(indent, xtw, "property", "name", e.getKey(), "value", e.getValue());
			}
			
			for (Map.Entry<String, String> e : vxmlVars.entrySet()) {
				writeEl(indent, xtw, "var", "name", e.getKey(), "expr", e.getValue());
			}
			
			if (grammar.type != null) {
				log.debug("grammar.type={}",grammar.type);
				startEl(indent, xtw, "field", "name", getName(), "type", grammar.type);
			} else {
				startEl(indent, xtw, "field", "name", getName());
			}
			
			for (Prompt prompt : context.prompts) {
				log.debug("prompt={} pause={}",prompt.text, prompt.pause);
				writePrompt(indent, xtw, prompt);
			}
			
			
			if (grammar.src != null) {
				writeEl(indent, xtw, "grammar", "src", grammar.src, "type", "application/srgs");
			}

			
			if (grammar.text != null) {
				log.debug("grammarText={}", StringUtils.abbreviate(grammar.text, 500));

				switch (context.getGrammarFormat()) {
					case ABNF: printABNF(indent, xtw, grammar); break;
					case GRXML: 

//						XMLStreamWriter test = startVxml(System.out);
//						printGRXML(indent, test, grammar);
//						endVxml(test);
						printGRXML(indent, xtw, grammar); 
						break;
				}
			}
			
			
			startEl(indent, xtw, "filled");

			writeVarQuote(indent, xtw, "event", "filled");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", String.format("event %s$.utterance %s$.confidence %s$.recording ", getName(), getName(), getName()) + String.join(" ", vxmlVars.keySet()));

			endEl(indent, xtw);
			
			startEl(indent, xtw, "catch");

			writeVar(indent, xtw, "event", "_event");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");

			endEl(indent, xtw);
			endEl(indent, xtw);
			endEl(indent, xtw);
			endEl(indent, xtw);
			
			
			endVxml(xtw);
		} catch (Exception e) {
			log.error("Error writing response.", e);
		} finally {
			context.prompts.clear();
		}
	}

	@SneakyThrows
	private void printGRXML(AtomicLong indent, XMLStreamWriter xtw, Grammar grammar) {
		List<String> errors = new ArrayList<>();
		String grammarText = "$main = "+grammar.text+";\n";
		AbstractGrammar g = context.getCompiler().compile(IOUtils.toInputStream(grammarText, "UTF-8"), errors);
		
		if (!errors.isEmpty()) {
			for (String error : errors) log.error("Grammar error: {}", error);
			return;
		}
		
		startEl(indent, xtw, "grammar", "version", "1.0", "root", xmlRule(g.rootRule), "type", "application/srgs+xml");
		
		for (Map.Entry<String, Node> rule : g.rules.entrySet()) {
			if (StringUtils.equals(g.rootRule, rule.getKey())) {
				startEl(indent, xtw, "rule", "id", xmlRule(rule.getKey()), "scope", "public");
			} else {
				startEl(indent, xtw, "rule", "id", xmlRule(rule.getKey()));
			}
			printGRXMLNode(indent, xtw, rule.getValue());
			endEl(indent, xtw);
		}
	
		endEl(indent, xtw);
	}
	
	private String xmlRule(String abnfRule) {
		return abnfRule.substring(1);
	}

	@SneakyThrows
	private void printGRXMLNode(AtomicLong indent, XMLStreamWriter xtw, Node node) {
		
		if (node.isPassThru()) {
			startEl(indent, xtw, "item", "repeat","0-1");
		}
		switch (node.getType()) {
		case AndNode:
			if (node.getChildren().size() > 1) {
				startEl(indent, xtw, "item");
			}
			for (Node child : node.getChildren()) {
				printGRXMLNode(indent, xtw, child);
			}
			if (node.getChildren().size() > 1) {
				endEl(indent, xtw);
			}
			break;
		case Atom:
			
			if (indent != null) {
				xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
			}
			xtw.writeStartElement(vxmlNS, "item");
			xtw.writeCharacters(node.getValue());
			xtw.writeEndElement();
			if (indent != null) {
				xtw.writeCharacters("\n");
			}
			break;
		case OrNode:
			startEl(indent, xtw, "one-of");
			for (Node child : node.getChildren()) {
				
				float weight = getWeight(child);
				if (weight != 1) {
					startEl(indent, xtw, "item", "weight", ""+weight);
				}
				printGRXMLNode(indent, xtw, child);
				if (weight != 1) {
					endEl(indent, xtw);
				}
			}
			endEl(indent, xtw);
			break;
		case Repeat:
			String repeats = "";
			if (node.getMinRepeats() > 0) repeats += node.getMinRepeats();			
			repeats += "-";
			if (node.getMaxRepeats() < Short.MAX_VALUE) repeats += node.getMaxRepeats();			
			
			startEl(indent, xtw, "item", "repeat", repeats);
			for (Node child : node.getChildren()) {
				printGRXMLNode(indent, xtw, child);
			}
			endEl(indent, xtw);
			break;
		case RuleRef:
			startEl(indent, xtw, "item");
			emptyEl(indent, xtw, "ruleref", "uri", "#"+xmlRule(node.getValue()));
			endEl(indent, xtw);
			break;
		default:
			log.debug("Grammar node: type={} value={}", node.getType(), node.getValue());
		}
		if (node.isPassThru()) {
			endEl(indent, xtw);
		}
	}

	private float getWeight(Node child) {
		if (child.getWeight() != 1 || !child.hasChildren() || child.getType() == NodeType.OrNode){
			return child.getWeight();
		}
	
		return getWeight(child.getChildI(0));
	}


	@SneakyThrows
	private void printABNF(AtomicLong indent, XMLStreamWriter xtw, Grammar grammar) {
		startEl(indent, xtw, "grammar", "mode", grammar.mode, "type", "application/srgs");
		xtw.writeCharacters("#ABNF 1.0;\n");
		xtw.writeCharacters("mode "+grammar.mode+";\n");
		xtw.writeCharacters("root $main;\n");
		xtw.writeCharacters("public $main = "+grammar.text+";\n");
		endEl(indent, xtw);
	}


	private String getName() {
		return this.getClass().getSimpleName();
	}


	private void endVxml(XMLStreamWriter xtw) throws XMLStreamException {
		xtw.writeEndDocument();
		xtw.flush();
		xtw.close();
	}
	
	@SneakyThrows
	protected void doHangup() {
		
		log.debug("ENTER");
		
		AtomicLong indent = new AtomicLong();
		try {
			XMLStreamWriter xtw = startVxml();
			

			indent.incrementAndGet();
			
			startEl(indent, xtw, "catch", "event", "connection.disconnect.hangup");
			writeEl(indent, xtw, "exit");
			endEl(indent, xtw);
			
			startEl(indent, xtw, "form", "id", "Main");
			
			startEl(indent, xtw, "block");
			
			for (Prompt prompt : context.prompts) {
				log.debug("prompt={} pause={}",prompt.text, prompt.pause);
				writePrompt(indent, xtw, prompt);
			}
			writeEl(indent, xtw, "disconnect");

			endEl(indent, xtw);
			
			endEl(indent, xtw);
			
			
			endVxml(xtw);
		} catch (Exception e) {
			log.error("Error writing response.", e);
		}
	}
	
	protected void doRecord(String maxtime, String finalsilence, boolean beep, boolean dtmfterm) {
		log.debug("ENTER");
		
		AtomicLong indent = new AtomicLong();
		try {
			XMLStreamWriter xtw = startVxml();
			
			indent.incrementAndGet();
			
			startEl(indent, xtw, "catch", "event", "connection.disconnect.hangup");
			writeVarQuote(indent, xtw, "event", "hangup");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");
			endEl(indent, xtw);
			
			startEl(indent, xtw, "form", "id", "Main");

			for (Map.Entry<String, String> e : vxmlProperties.entrySet()) {
				writeEl(indent, xtw, "property", "name", e.getKey(), "value", e.getValue());
			}
			
			for (Map.Entry<String, String> e : vxmlVars.entrySet()) {
				writeEl(indent, xtw, "var", "name", e.getKey(), "expr", e.getValue());
			}
			
			startEl(indent, xtw, "record", "name", getName(), "maxtime", maxtime, "finalsilence", finalsilence, "dtmfterm", ""+dtmfterm, "beep", ""+beep);
			
			for (Prompt prompt : context.prompts) {
				log.debug("prompt={} pause={}",prompt.text, prompt.pause);
				writePrompt(indent, xtw, prompt);
			}
				
			
			startEl(indent, xtw, "filled");

			writeVarQuote(indent, xtw, "event", "filled");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", String.format("event %s$.recording ", getName()) + String.join(" ", vxmlVars.keySet()) );

			endEl(indent, xtw);
			
			startEl(indent, xtw, "catch");

			writeVar(indent, xtw, "event", "_event");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");

			endEl(indent, xtw);
			endEl(indent, xtw);
			endEl(indent, xtw);
			endEl(indent, xtw);
			
			
			endVxml(xtw);
		} catch (Exception e) {
			log.error("Error writing response.", e);
		} finally {
			context.prompts.clear();
		}
	}
	
	protected void doBlock() {
		log.debug("ENTER");
		
		AtomicLong indent = new AtomicLong();
		try {
			XMLStreamWriter xtw = startVxml();
			
			indent.incrementAndGet();
			
			startEl(indent, xtw, "catch", "event", "connection.disconnect.hangup");
			writeVarQuote(indent, xtw, "event", "hangup");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");
			endEl(indent, xtw);
			
			startEl(indent, xtw, "form", "id", "Main");

			for (Map.Entry<String, String> e : vxmlProperties.entrySet()) {
				writeEl(indent, xtw, "property", "name", e.getKey(), "value", e.getValue());
			}
			
			for (Map.Entry<String, String> e : vxmlVars.entrySet()) {
				writeEl(indent, xtw, "var", "name", e.getKey(), "expr", e.getValue());
			}
			
			
			for (Prompt prompt : context.prompts) {
				log.debug("prompt={} pause={}",prompt.text, prompt.pause);
				writePrompt(indent, xtw, prompt);
			}
				
			
			startEl(indent, xtw, "block");
			writeVarQuote(indent, xtw, "event", "filled");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event "+String.join(" ", vxmlVars.keySet()) );
			
			endEl(indent, xtw);
			
			startEl(indent, xtw, "catch");

			writeVar(indent, xtw, "event", "_event");
			writeEl(indent, xtw, "submit", "next", "next", "method", "post", "enctype", "multipart/form-data", "namelist", "event");

			endEl(indent, xtw);
			endEl(indent, xtw);
			endEl(indent, xtw);
			
			
			endVxml(xtw);
		} catch (Exception e) {
			log.error("Error writing response.",  e);
		} finally {
			context.prompts.clear();
		}
	}



	private XMLStreamWriter startVxml() throws XMLStreamException {
		return startVxml(context.outputStream);
	}
	
	private XMLStreamWriter startVxml(OutputStream os) throws XMLStreamException {
		XMLStreamWriter xtw = xof.createXMLStreamWriter(os);
		xtw.setDefaultNamespace(vxmlNS);
		
		xtw.writeStartDocument();
		xtw.writeCharacters("\n");
		xtw.writeStartElement(vxmlNS, "vxml");

		xtw.writeDefaultNamespace(vxmlNS);
		xtw.writeNamespace("xsi", xsiNS);
		xtw.writeAttribute(xsiNS, "schemaLocation", "http://www.w3.org/2001/vxml http://www.w3.org/TR/voicexml20/vxml.xsd");
		xtw.writeAttribute("version", "2.1");
		xtw.writeAttribute("xml:lang", context.config.getString("lang","en-AU"));
		xtw.writeCharacters("\n");
		return xtw;
	}
	
	
	static final private String vxmlNS = "http://www.w3.org/2001/vxml";
	static final private String xsiNS = "http://www.w3.org/2001/XMLSchema-instance";
	
	
	@SneakyThrows
	private void writeEl(AtomicLong indent, XMLStreamWriter xtw, String el, String... attPairs) {
		xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		xtw.writeEmptyElement(vxmlNS, el);
		for (int i = 0; i < attPairs.length; i += 2) {
			String localName = attPairs[i];
			String atts = attPairs[i+1];
			xtw.writeAttribute(localName, atts);
		}
		xtw.writeCharacters("\n");
	}
	
	@SneakyThrows
	private void writePrompt(AtomicLong indent, XMLStreamWriter xtw, Prompt prompt) {
		xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		writeEl(xtw, "prompt", "bargein", prompt.bargable?"true":"false");
		if (!StringUtils.isEmpty(prompt.id)) {
			String id = prompt.id;
			String[] ids = StringUtils.split(id);
			if (ids.length > 1) {
				id = "temp/"+StringUtils.replaceAll(prompt.id, "[^0-9a-zA-Z]", "_");
				makeAudio(ids, id);
			}
			
			startEl(null, xtw, "audio", "src", "../prompts/" + id + ".wav", "maxage", "0", "maxstale", "0");
			startEl(null, xtw, "prosody", "rate", context.config.getString("rate", "medium"));
			xtw.writeCharacters(prompt.text);
			endEl(null, xtw);
			if (prompt.pause > 0) {
				writeEl(xtw, "break", "time", ""+prompt.pause+"ms");
				endEl(null, xtw);
			}
			endEl(null, xtw);
			

		}
		
		xtw.writeEndElement();
		xtw.writeCharacters("\n");
	}
	

	@SneakyThrows
	public static void makeAudio(String[] prompts, String promptId) {
		try {
			File newFile = new File("prompts/"+promptId+".wav");
			if (!newFile.exists()) {
				AudioInputStream aisOrig = null;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				for (String prompt : prompts) {
					aisOrig = AudioSystem.getAudioInputStream(new File("prompts/"+prompt+".wav"));
					IOUtils.copy(aisOrig, baos);
				}
				AudioInputStream ais = new AudioInputStream(baos.toInputStream(), aisOrig.getFormat(), baos.size());
				FileUtils.forceMkdirParent(newFile);
				AudioSystem.write(ais, Type.WAVE, newFile);
			}
		} catch (Exception e) {
			log.debug("", e);
		}
	}
	
	@SneakyThrows
	private void startEl(AtomicLong indent, XMLStreamWriter xtw, String el, String... attPairs) {
		if (indent != null) {
			xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		}
		writeEl(xtw, el, attPairs);
		if (indent != null) {
			indent.incrementAndGet();
			xtw.writeCharacters("\n");
		}
	}

	@SneakyThrows
	private void emptyEl(AtomicLong indent, XMLStreamWriter xtw, String el, String... attPairs) {
		if (indent != null) {
			xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		}
		xtw.writeEmptyElement(vxmlNS, el);
		for (int i = 0; i < attPairs.length; i += 2) {
			String localName = attPairs[i];
			String atts = attPairs[i+1];
			xtw.writeAttribute(localName, atts);
		}
		if (indent != null) {
			xtw.writeCharacters("\n");
		}
	}


	private void writeEl(XMLStreamWriter xtw, String el, String... attPairs) throws XMLStreamException {
		xtw.writeStartElement(vxmlNS, el);
		for (int i = 0; i < attPairs.length; i += 2) {
			String localName = attPairs[i];
			String atts = attPairs[i+1];
			xtw.writeAttribute(localName, atts);
		}
	}
	
	@SneakyThrows
	private void endEl(AtomicLong indent, XMLStreamWriter xtw) {
		if (indent != null) {
			indent.decrementAndGet();
			xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		}
		xtw.writeEndElement();
		if (indent != null) {
			xtw.writeCharacters("\n");
		}
		
	}
	
	
	@SneakyThrows
	private void writeVarQuote(AtomicLong indent, XMLStreamWriter xtw, String name, String expr) {
		xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		xtw.writeEmptyElement(vxmlNS, "var");
		xtw.writeAttribute("name", name);
		xtw.writeAttribute("expr", "'"+ expr+"'");
		xtw.writeCharacters("\n");
		
	}
	
	@SneakyThrows
	private void writeVar(AtomicLong indent, XMLStreamWriter xtw, String name, String expr) {
		xtw.writeCharacters(StringUtils.repeat("  ", indent.intValue()));
		xtw.writeEmptyElement(vxmlNS, "var");
		xtw.writeAttribute("name", name);
		xtw.writeAttribute("expr", expr);
		xtw.writeCharacters("\n");
		
	}
	
	

	

}
