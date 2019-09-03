package com.auraya.grammar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.auraya.grammar.Node.AttachedData;
import com.auraya.grammar.Node.NodeType;
import com.machinezoo.noexception.Exceptions;



/**
 * This represents a compiled grammar. 
 * 
 * This can produce valid parses for an input string. If the input cannot match the grammar then no parses are produced.
 * This can interpret a parse by executing JavaScript tags embedded in the parse tree.
 * 
 * 
 * @author Jamie Lister
 * @version 1.0
 */
public class AbstractGrammar implements Serializable {

	private static final long	serialVersionUID	= 1L;
	public Map<String,Node> rules = new LinkedHashMap<>();
	public String rootRule = null;

	private int numberOfNodes = 0;
	
	// This is a convenience member for other classes to keep track of associated data
	public Map<String,String> parameters;
	
	private static final Node nonCapturingStart = new Node(NodeType.Generic, "",0);;
	private static final Node nonCapturingEnd = new Node(NodeType.Generic, "",0);

	public static final String NULL_RULE_NAME = "$NULL";
	public static final String VOID_RULE_NAME = "$VOID";

	transient private ScriptEngine jsEngine;
	transient private Compilable compEngine;
	transient private Map<String,CompiledScript> compiledScripts;

	
	class Parse extends ArrayDeque<Node> implements Comparable<Parse> {
		private static final long	serialVersionUID	= 1L;
		short finish;
		short start;
		float logWeight;
		public int	contextWords;
		int hashcode;
		
		Parse(int capacity) { 
			super(capacity);
			start = 0; finish = 0; logWeight = 0.0F; 
		}
		public Parse(Parse parse) {
			super(parse);
			finish = parse.finish;
			start = parse.start;
			logWeight = parse.logWeight;
		}
		@Override
		public String toString() {
			return "Parse [finish=" + finish + ", start=" + start
					+ ", logWeight=" + logWeight + ", toString()="
					+ super.toString() + "]";
		}
		@Override
		public int compareTo(Parse other) {
			int v = Short.compare(start,other.start);
			if (v != 0) { return v; }
			v = Integer.compare(other.finish-other.start, finish-start);
			if (v != 0) { return v; }
			v = Integer.compare(other.contextWords, contextWords);
			if (v != 0) { return v; }
			return Float.compare(other.logWeight, logWeight);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Parse) {
				Parse other = (Parse) obj;
				
				if (start != other.start || finish != other.finish && contextWords != other.contextWords || logWeight != other.logWeight ||size() != other.size()) {
					return false;
				}
				
				if (isEmpty()) {
					return true;
				}
				
				Iterator<Node> n1 = iterator();
				Iterator<Node> n2 = other.iterator();
				while (n1.hasNext() && n2.hasNext()) {
					if (!n1.next().equals(n2.next())) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		}
		
		@Override
		public int hashCode() {
			if (hashcode == 0) {
				HashCodeBuilder hcb = new HashCodeBuilder();
				hcb.append(start);
				hcb.append(finish);
				hcb.append(contextWords);
				hcb.append(logWeight);
				for (Node n : this) {
					hcb.append(n);
				}
				hashcode = hcb.toHashCode();
			}
			return hashcode;
		}
		
		
	};
	
	public AbstractGrammar() {}
	
	public Node getRootNode() {
		return rules.get(rootRule);
	}
	
	
	/*
	Use cached best coverage if possible. Otherwise, search for coverage then append remaining best coverage.
	*/
	private Collection<CoveringParses> bestWordCoverage(int maxSize, short start, List<Parse> allParses, int searchI, List<Collection<CoveringParses>> indexedBestParse) {
	
		Collection<CoveringParses> cachedParses = indexedBestParse.get(start);
		if (cachedParses != null) {
			return cachedParses;
		}
		
		HashSet<CoveringParses> coveringParsesSet = new HashSet<>();
		PriorityQueue<CoveringParses> coveringParsesList = new PriorityQueue<>(maxSize+1);

			
		while(searchI < allParses.size()) {
			Parse parse = allParses.get(searchI);
			if (parse.start >= start && parse.finish > start) {
				
				boolean foundInnerParses = false;
				if (parse.finish < indexedBestParse.size()) {
					Collection<CoveringParses> innerCPList = bestWordCoverage(maxSize, parse.finish, allParses, searchI+1, indexedBestParse);
					foundInnerParses = !innerCPList.isEmpty();
					for (CoveringParses innerCP : innerCPList) {
						short numWords = (short) (innerCP.numWords + parse.finish - parse.start);
						float logWeight = innerCP.logWeight + parse.logWeight;

						if (compare(maxSize, numWords, innerCP.parses.size()+1, logWeight, coveringParsesList) > 0) {

							CoveringParses coveringParses = new CoveringParses();
							
							coveringParses.numWords = numWords;
							coveringParses.logWeight = logWeight;
							coveringParses.numNodes = parse.size();
							coveringParses.numNodes += innerCP.numNodes;
							
							coveringParses.parses.addAll(innerCP.parses);
							coveringParses.parses.addFirst(parse);
							if (coveringParsesSet.add(coveringParses)) {
								coveringParsesList.add(coveringParses);
								
								if (coveringParsesList.size() > maxSize) {
									coveringParsesList.poll(); // remove least when size grows too large
								}
							}
						}	
					}
				}
				
				if (!foundInnerParses)  {	
					short numWords = (short) (parse.finish - parse.start);
					
					if (compare(maxSize, numWords, 1, parse.logWeight, coveringParsesList) >= 0) {

						CoveringParses coveringParses = new CoveringParses();
						coveringParses.numWords = (short) (parse.finish - parse.start);
						coveringParses.logWeight = parse.logWeight;
						coveringParses.numNodes = parse.size();
						
						coveringParses.parses.addFirst(parse);
						if (coveringParsesSet.add(coveringParses)) {
							coveringParsesList.add(coveringParses);
							if (coveringParsesList.size() > maxSize) {
								coveringParsesList.poll(); // remove least when size grows too large
							}
						}
					}
				}
			}
			searchI++;
		}
		indexedBestParse.set(start,coveringParsesList);
		
		return coveringParsesList;
	}
	
	private int compare(int maxSize, short numWords, int rules, float logWeight, PriorityQueue<CoveringParses> coveringParsesList) {

		if (coveringParsesList.size() < maxSize) {
			return 1;
		}
		CoveringParses ref = coveringParsesList.peek();
		if (ref == null) {
			return 1;
		}
		int v = Integer.compare(numWords, ref.numWords);
		if (v != 0) {
			return v;
		}
		v = Integer.compare(ref.parses.size(), rules);
		if (v != 0) {
			return v;
		}
		return Float.compare(logWeight, ref.logWeight);
	}

	private void clearNodeData(Node node) {
		if (node.getAttachedData() != null) {
			node.setAttachedData(null);
			if (node.type == NodeType.RuleRef) {
	
				Node refNode = rules.get(node.value);
				if (refNode != null) {
					clearNodeData(refNode);
				}
			}
			if (node.hasChildren()) {
				for (Node child : node.getChildren()) {
					clearNodeData(child);
				}
			}
		}
	} 
	
	public enum ParseMode {
		ROBUST,
		EXACT,
		GREEDY_ROBUST
	}

	/**
	 * Attempts to match the given transcription against the grammar.
	 * 
	 * @param transcription
	 * @param errors
	 * @return
	 */
	public List<CoveringParses> parse(String transcription, List<String> errors, ParseMode parseMode, int maxSize) {

		// clear the nodeData
		
		// split the tokens
		String[] tokens = transcription.split("\\s+");
		// find the root rule (usually first in file), use it
		Node node = rules.get(rootRule);
		clearNodeData(node);
		
		Collection<Parse> parses = getNewParses();
		Map<Node,Map<Short, Collection<Parse>>> nodeCacheMap = new HashMap<>();
		if (node != null) {
			
			int lastToken = (parseMode != ParseMode.EXACT)?tokens.length:1;
			

			short nextStart = 0;
			for (short start = 0; start < lastToken; start = nextStart) {
				//long startTime= System.currentTimeMillis();
				Collection<Parse> tempParses =  getNewParses();
				parse(node, tokens, start, tempParses, nodeCacheMap, false);

				nextStart = (short) (start+1);
				
				// check for nonCapturing sections
				for (Parse p : tempParses) {
					boolean isNonCapturing = false;
					short tempStart = start;
					short tempFinish = start;
					p.contextWords = p.finish - p.start;
					
					if (parseMode == ParseMode.GREEDY_ROBUST) {
						nextStart = (short) Math.max(nextStart, p.finish);
					}
					
					for (Node n : p) {
						if (n == nonCapturingStart) {
							isNonCapturing = true;
						}
						if (n == nonCapturingEnd) {
							isNonCapturing = false;
						}
						if (n.type == NodeType.Atom) {
							if (isNonCapturing && tempFinish == tempStart) {
								tempStart++;
								tempFinish++;
							}
							if (!isNonCapturing) {
								tempFinish++;
							}
						}
					}
					p.start = tempStart;
					p.finish = tempFinish;
				}
				parses.addAll(tempParses);
				
				//long endTime= System.currentTimeMillis();
				//System.out.println("time="+(endTime-startTime)+", \t"+tokens[start]);
			}
		} else {
			String error = "No root rule [" + rootRule + "]";
			errors.add(error);
		}
		//System.out.println("nodeCacheMap.size() ["+nodeCacheMap.size()+"]");
		//System.out.println("cacheHits ["+cacheHits+"]");

		if (!parses.isEmpty()) {

			
			List<Collection<CoveringParses>> indexedBestParse = new ArrayList<>(tokens.length);
			indexedBestParse.addAll(Collections.<List<CoveringParses>>nCopies(tokens.length, null));
			
			// sort parses
			ArrayList<Parse> parsesList = new ArrayList<Parse>(parses);
			Collections.sort(parsesList);	

			//long startTime = System.currentTimeMillis();
	        Collection<CoveringParses> coveringParsesList = bestWordCoverage(maxSize, (short)0,  parsesList, 0, indexedBestParse);
			//long endTime = System.currentTimeMillis();
			//System.out.println("Covering Parses Time: " + (endTime - startTime));
			
			ArrayList<CoveringParses> list = new ArrayList<>();
			for (CoveringParses coveringParses : coveringParsesList) {
				list.add(coveringParses);
			}
			Collections.sort(list);
			
			return list;
		}
		return new ArrayList<>();
	}

	private Collection<Parse> getNewParses() {
		return new ArrayList<>(); //queueBuilder.create();
	}

	private void parse(Node node, String[] tokens, short start, Collection<Parse> parses, Map<Node, Map<Short,Collection<Parse>>> nodeCacheMap, boolean inRepeat) {
 
		// does this node already have the parses available?
		List<Collection<Parse>> indexedParses = NodeAttachedData.get(node).getIndexedParses(tokens.length+1);
		
		if (!inRepeat) {
			Collection<Parse> cacheValue = indexedParses.get(start);
			if (cacheValue != null) {
				//System.out.println(cacheHits);
				parses.clear();
				for (Parse parse : cacheValue) {
					parses.add(new Parse(parse));
				}
				return;	
			}
		}
		//System.out.println(node.value);
		

		// 0 repeats?
		if (!inRepeat && node.isPassThru) {
			Parse parse = new Parse(tokens.length);
			parse.add(node);
			parse.start = start;
			parse.finish = start;
			parse.logWeight = node.getLogWeight();
			parses.add(parse);
		}

		if (!inRepeat && (node.minRepeats != 1 || node.maxRepeats != 1)) {
			
			
			Collection<Parse> repeatParses = getNewParses();

			// go through the mandatory repeats (up to min repeats)
			for ( short repeat = 0; repeat < node.minRepeats; ++repeat) {
				parseSequentialItem(node.getChildI(0), tokens, start, repeatParses, nodeCacheMap, true);

				if (repeatParses.isEmpty()) {
					// no parses left, return with nothing for whole sequence
					indexedParses.set(start, getNewParses());
					return;
				}
			}

			// add the parses 
			parses.addAll(repeatParses);
			

			// go through the optional repeats (up to max repeats)
			for (short repeat = node.minRepeats; repeat < node.maxRepeats; ++repeat) {
				parseSequentialItem(node.getChildI(0), tokens, start, repeatParses, nodeCacheMap, true);

				if (repeatParses.isEmpty()) {
					// no parses left, stop here
					break;
				}
				
				// add the parses 
				parses.addAll(repeatParses);
			}

		} else {

			// Atom node
			if (node.type == NodeType.Atom && start < tokens.length && tokens[start].equals(node.value)) {
				Parse parse = new Parse(tokens.length);
				parse.add(node);
				parse.start = start;
				parse.finish = (short) (start + 1);
				parse.logWeight = node.getLogWeight();
				parses.add(parse);
			}

			// AND (sequential) node
			
			if (node.type == NodeType.AndNode && node.hasChildren()) {	

				Collection<Parse> tempParses =  getNewParses();

				// go through each "and" node sequentially, accumulate parses in "tempParses"
				for (Node child : node.getChildren()) {
				
					parseSequentialItem(child, tokens, start, tempParses, nodeCacheMap, false);

					if (tempParses.isEmpty()) {
						// no parses left, return with nothing for whole sequence
						break;
					}
				}

				// there were parses for whole sequence, copy them to return variable "parses"
				for (Parse k : tempParses) {
					k.add(node);
					k.logWeight += node.getLogWeight();
					
					if (node.value.equals(ABNFCompiler.NON_CAPTURING)) {
						k.addFirst(nonCapturingStart);
						k.addLast(nonCapturingEnd);
					}
				}
				

				parses.addAll(tempParses);
			}

			// Or (parallel) node
			if (node.type == NodeType.OrNode) {
				NodeAttachedData data = NodeAttachedData.get(node);
				if (data.mappedNodes == null) {
					data.mappedNodes = new HashMap<>();
					
					for (Node child : node.getChildren()) {
						ArrayList<String> initialTokens = new ArrayList<>();
						addInitialTokens(child, initialTokens);
						for (String token : initialTokens) {
							data.mappedNodes.computeIfAbsent(token, k -> new ArrayList<>()).add(child);
						}
					}
				}
				if (start < tokens.length) {
					List<Node> nodes = data.mappedNodes.get(tokens[start]);
					if (nodes != null) {
						for (Node child : nodes) {
							Collection<Parse> tempParses =  getNewParses();
							parse(child, tokens, start, tempParses, nodeCacheMap, false);
							
							parses.addAll(tempParses);
						}
					}
				}
			}

			// Rule Reference node
			if (node.type == NodeType.RuleRef) {

				// check for NULL
				if (node.value.equals(NULL_RULE_NAME)) {
					Parse parse = new Parse(tokens.length);
					parse.add(node);
					parse.start = start;
					parse.finish = start;
					parse.logWeight = node.getLogWeight();
					parses.add(parse);
					List<Parse> cacheCopy = new ArrayList<>();
					cacheCopy.add(new Parse(parse));
					indexedParses.set(start, cacheCopy);
					return;
				}

				// check for VOID
				if (node.value.equals(VOID_RULE_NAME)) {
					indexedParses.set(start,new ArrayList<AbstractGrammar.Parse>());
					return;
				}

				// Else find the Rule
				Node rule = rules.get(node.value);
				if (rule != null) {
					//parses
					Collection<Parse> tempParses = getNewParses();
					parse(rule, tokens, start, tempParses, nodeCacheMap, false);
					for (Parse tempParse : tempParses) {	
						//k.addFirst(ruleContextStart);
						tempParse.addFirst(new Node(NodeType.Generic,node.value,0));
						
						tempParse.add(node);
						tempParse.logWeight += node.getLogWeight();
					}
					parses.addAll(tempParses);
					
				} else {
					throw new RuntimeException("Unknown rule ["+node.value+"]");
				}
			}
		}
		//if (!parses.isEmpty()) 
		// put parses into cache, deep copy
		List<Parse> cacheCopy = new ArrayList<>(parses.size());
		for (Parse parse : parses) {
			cacheCopy.add(new Parse(parse));
		}
		indexedParses.set(start, cacheCopy);
	}
	
	public void calculateNumberOfNodes() {
		numberOfNodes = 0;
		for (Node n : rules.values()) {
			numberOfNodes += countNodes(n);
		}
	}
	
	private int countNodes(Node n) {
		int total = 1;
		if (n.hasChildren()) {
			for (Node c : n.getChildren()) {
				total += countNodes(c);
			}
		}
		return total;
	}
	
	public int getNumberOfNodes() {
		return numberOfNodes;
	}

	private boolean addInitialTokens(Node node, Collection<String> tokens) {
		switch (node.type) {
		case Atom: tokens.add(node.value); return node.isPassThru;
		case OrNode: 
			boolean isPassThru = node.isPassThru;
			for (Node child : node.getChildren()) {
				isPassThru |= addInitialTokens(child, tokens);
			}
			return isPassThru;
		case AndNode:
			isPassThru = node.isPassThru;
			for (Node child : node.getChildren()) {
				isPassThru &= addInitialTokens(child, tokens);
				if (!child.isPassThru) {
					break;
				}
			}
			return isPassThru;
		case Repeat:
			return addInitialTokens(node.getChildI(0), tokens) || node.isPassThru;
		case RuleRef:
			if (!node.value.equals(NULL_RULE_NAME) && !node.value.equals(VOID_RULE_NAME)) {
				Node ruleNode = rules.get(node.value);
				return addInitialTokens(ruleNode, tokens) || node.isPassThru;
			} else {
				return node.isPassThru;
			}
		default:
			throw new RuntimeException("Unexpected node type.");
		}
		
		
	}

	private void parseSequentialItem(Node node, String[] tokens,  short start, Collection<Parse> parses, Map<Node,Map<Short, Collection<Parse>>> nodeCacheMap, boolean inRepeat) {

		if (parses.isEmpty()) {	
			parse(node, tokens, start, parses, nodeCacheMap, inRepeat);
		} else {
			ArrayList<Parse> newParses = new ArrayList<>(parses.size());
			Iterator<Parse> iterator = parses.iterator();
			while (iterator.hasNext()) {
				Parse parse = iterator.next();

				Collection<Parse> nodeParses =  getNewParses();
				

				//if (parse.finish < tokens.length || node.isPassThru) {
					parse(node, tokens, parse.finish, nodeParses, nodeCacheMap, inRepeat);
				//}
				
				if (nodeParses.isEmpty()) {
					iterator.remove();
					continue;
				}
				
				// copy this nodes parses "nodeParses" to the end of the current tempParse so far
				int i = 0;
				for (Parse nodeParse : nodeParses) {
					Parse newParse = parse;

					i++;
					if (i < nodeParses.size()) {
						newParse = new Parse(tokens.length);
						newParse.addAll(parse);
						newParses.add(newParse);
					}
					
					newParse.addAll(nodeParse);
					newParse.finish = nodeParse.finish;
					newParse.logWeight += nodeParse.logWeight;
				}
				
				
			}
			parses.addAll(newParses);	
		}
	}

	synchronized public Map<String,Object> interpret(CoveringParses coveringParses,  List<String> errors, boolean printNodes) throws ScriptException {
		
		
		if (jsEngine == null) {
			ScriptEngineManager mgr = new ScriptEngineManager();
			jsEngine = mgr.getEngineByName("JavaScript");
			//jsEngine = mgr.getEngineByName("nashorn");
			compEngine = (Compilable) jsEngine;
			compiledScripts = new HashMap<>();
		}
		
		// Create a new context.
		Bindings standardScope = new SimpleBindings();
		
		jsEngine.eval("_ = {};", standardScope);
		
		RuleBindings finalRuleScope = null;
		for (Parse parse : coveringParses.parses) {
			
			// a stack to hold 'globals' when entering new rules
			ArrayDeque<RuleBindings> globalStack = new ArrayDeque<>();
			RuleBindings ruleScope = new RuleBindings(standardScope, true);
			finalRuleScope = ruleScope;
			
			for (Node node : parse) {

				if (printNodes) { 
					System.out.println("\t" + node.type + "\tline="+node.lineNumber + "\t" + node.value) ;
				}
				
				if (node.type == NodeType.Generic && !node.value.isEmpty()) {					
					globalStack.add(ruleScope);
					ruleScope = new RuleBindings(standardScope, true);
				}
				if (node.type == NodeType.RuleRef && !node.value.equals(NULL_RULE_NAME) && !node.value.equals(VOID_RULE_NAME)) {
					
					// Pop out of context, save the global prototype (to use as property in calling rule context)
					
					RuleBindings innerRuleScope = ruleScope;
					Object scriptResult = innerRuleScope.scriptResult;

					ruleScope = globalStack.pollLast();
					finalRuleScope = ruleScope;
					
					String ruleName = node.value.substring(1);
					StringBuilder sb = new StringBuilder();
					sb.append(ruleName);

					List<String> propertiesToDelete = new ArrayList<>();
					
					
					if (scriptResult != null) {
						ruleScope.put("scriptResult", scriptResult);
						propertiesToDelete.add("scriptResult");
						
						if (scriptResult instanceof String) {
							sb.append("= new String(scriptResult);\n");
						} else if (scriptResult instanceof Double) {
							sb.append("= new Number(scriptResult);\n");
						} else if (scriptResult instanceof Boolean) {
							sb.append("= new Boolean(scriptResult);\n");
						} else {
							sb.append("= scriptResult;\n");
						}
					} else {
						sb.append("= new String('-');\n");
					}
					
					int n = 0;
					for (Map.Entry<String, Object> entry : innerRuleScope.tagScope.entrySet()) {

						if (entry.getValue() != null) {
							String packageName = entry.getValue().getClass().getPackage().getName();
							if (packageName.startsWith("sun") || packageName.startsWith("javax")) {
								continue;
							}
						}
						
						String propValName = "_property_" + n++;
						ruleScope.put(propValName, entry.getValue());
						propertiesToDelete.add(propValName);
						
						sb.append(ruleName);
						sb.append("['");
						sb.append(entry.getKey());
						sb.append("']=");
						sb.append(propValName);
						sb.append(";\n");
					}
					
					if (node.handle != null) {
						sb.append(node.handle);
						sb.append("=");
						sb.append(ruleName);
						sb.append(";\n");
					}
					String script = sb.toString();
					CompiledScript compiledScript = compiledScripts.computeIfAbsent(script, Exceptions.sneak().function(k -> compEngine.compile(k)));
					compiledScript.eval(ruleScope);
					ruleScope.keySet().removeAll(propertiesToDelete);
				}

				// Process the tags
				if (node.hasTags()) {
					for (Tag t : node.getTags()) {
						
						
						String tag =t.tag;
						//int lineNumber = t.lineNumber;
						//int columnOffset = t.column;
	
						if (printNodes) {
							System.out.println("\t" + tag.substring(0,Math.min(30, tag.length())));
						}
						CompiledScript compiledScript = (CompiledScript) t.cachedTag;
						if (compiledScript == null) {
							t.cachedTag = compiledScripts.computeIfAbsent(tag, Exceptions.sneak().function(k -> compEngine.compile(k)));
						} 
						if (tag.equals("'V'")) {
						//	System.out.println("JERE");
						}
							
						ruleScope.scriptResult = compiledScript.eval(ruleScope.tagScope);
					}	
				}
			}
		}
		

		final Object finalScriptResult = finalRuleScope != null ? finalRuleScope.scriptResult: null;
		Map<String,Object> result = new HashMap<String,Object>() {
			private static final long	serialVersionUID	= 1L;

			@Override
			public String toString() {
				if (finalScriptResult != null) {
					return finalScriptResult.toString();
				} else {
					return null;
				}
			}
		};
		
		if (finalRuleScope != null) {
			for (Map.Entry<String, Object> entry : finalRuleScope.tagScope.entrySet()) {
	
				if (entry.getValue() != null) {
					String packageName = entry.getValue().getClass().getPackage().getName();
					if (packageName.startsWith("sun") || packageName.startsWith("javax")) {
						continue;
					}
				}
				
				result.put(entry.getKey(), entry.getValue());
			}
		}
		
		return result;
		
	}
	

	
	private class RuleBindings extends SimpleBindings {
		
		Object scriptResult = null;
		Bindings superScope = null;
		RuleBindings tagScope = null;
		
		RuleBindings(Bindings superScope, boolean createTagScope) {
			this.superScope = superScope;
			if (createTagScope) {
				this.tagScope = new RuleBindings(this, false);
			}
		}
		
		@Override
		public Object get(Object key) {
			Object o = super.get(key);
			
			if (o == null) {
				o = superScope.get(key);
			}
			return o;
		}
		
		@Override
		public boolean containsKey(Object key) {
			return super.containsKey(key) || superScope.containsKey(key);
		}
		
		@Override
		public boolean containsValue(Object value) {
			return super.containsValue(value) || superScope.containsValue(value);
		}
		
		@Override
		public boolean isEmpty() {
			return super.isEmpty() && superScope.isEmpty() ;
		}
	}
	


	public void serialise(DataOutput out) throws IOException {
		
		out.writeBoolean(rootRule != null);
		if (rootRule != null) {
			out.writeUTF(rootRule);
		}
		
		out.writeShort(rules.size());
		for (Map.Entry<String, Node> rule : rules.entrySet()) {
			out.writeUTF(rule.getKey());
			rule.getValue().serialise(out);
		}
	}
	
	public AbstractGrammar(DataInput in) throws IOException {
		if (in.readBoolean()) {
			rootRule = in.readUTF();
		}
		
		short numRules = in.readShort();
		for (short i = 0; i < numRules; i++) {
			rules.put(in.readUTF(), new Node(in));
		}
	}
	
	public void serialise(File file) {
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
			zip.setLevel(3);
			zip.putNextEntry(new ZipEntry(getClass().getName()));
			try (ObjectOutputStream oos = new ObjectOutputStream(zip)) {
				oos.writeObject(this);
			}
		} catch (IOException e) {
			throw new RuntimeException("Couldn't serialise", e);
		}
	}
	
	static public AbstractGrammar deserialise(File file) {
		try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
			zip.getNextEntry();
			try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(zip))) {
				return (AbstractGrammar) ois.readObject();
			}
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException("Couldn't deserialise", e);
		}
	}
	

	
	static public class NodeAttachedData implements AttachedData {

		private Map<String,List<Node>> mappedNodes;
		private List<Collection<Parse>> indexedParses;
		
		public  Map<String,List<Node>> getNodes() {
			return mappedNodes;
		}
	
		public List<Collection<Parse>> getIndexedParses(int length) {
			if (indexedParses == null) {
				indexedParses = new ArrayList<>(length);
				indexedParses.addAll(Collections.<Collection<Parse>>nCopies(length, null));
			}
			return indexedParses;
		}
		
		public static NodeAttachedData get(Node node) {
			NodeAttachedData data;
			synchronized (node) {
				data = node.getAttachedData();
				if (data == null) {
					data = new NodeAttachedData();
					node.setAttachedData(data);
				}
			}
			return data;
		}

		@Override
		public AttachedData deepCopy() {
			NodeAttachedData copy = new NodeAttachedData();
			copy.mappedNodes = new HashMap<>(this.mappedNodes);
			return copy;
		}

		@Override
		public Set<Integer> getList() {
			return null;
		}
	}

	public AbstractGrammar copy() {
		AbstractGrammar copy = new AbstractGrammar();
		copy.numberOfNodes = numberOfNodes;
		copy.rootRule = rootRule;
		
		for (Map.Entry<String, Node> entry : rules.entrySet()) {
			copy.rules.put(entry.getKey(), entry.getValue().deepCopy());
		}
		
		return copy;
	}
	
	
}
