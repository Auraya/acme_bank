package com.auraya.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.auraya.grammar.Node.NodeType;

/**
 * Compiles a Lattice file (.lat format) to a Grammar object.
 * 
 * @author Jamie Lister
 *
 */
public class LatticeCompiler implements GrammarCompiler {
	

	static final LatticeLinkComparator latticeLinkComparator = new LatticeLinkComparator();
	//private int branchingFactor = 2;
	
	static class LatticeLink {
		Node node = new Node(NodeType.LatticeNode, "L", 0);
		Map<String,String> parameters = new HashMap<String,String>();
	}
	
	static class LatticeNode {
		
		
		Map<String,String> parameters = new HashMap<String,String>();
		PriorityQueue<LatticeLink> links = new PriorityQueue<LatticeLink>(10, latticeLinkComparator);
	}
	
	static class LatticeLinkComparator implements Comparator<LatticeLink> {

		@Override
		public int compare(LatticeLink l1, LatticeLink l2) {
			String acousticScoreString1 = l1.parameters.get("a");
			String lmScoreString1 = l1.parameters.get("l");
			
			String acousticScoreString2 = l2.parameters.get("a");
			String lmScoreString2 = l2.parameters.get("l");
			
			if (acousticScoreString1 != null && lmScoreString1 != null && acousticScoreString2 != null && lmScoreString2 != null) {
				float score1 = Float.parseFloat(acousticScoreString1) + Float.parseFloat(lmScoreString1);
				float score2 = Float.parseFloat(acousticScoreString2) + Float.parseFloat(lmScoreString2);
				return Float.compare(score1, score2);
			}
			return 0;
		}
		
	}
	

	/* (non-Javadoc)
	 * @see net.auraya.grammar.Compiler#compile(java.io.InputStream, java.util.List)
	 */
	@Override
	public AbstractGrammar compile(InputStream is, List<String> errors) throws NumberFormatException, IOException {
		
		
		Map<String,String> parameters = new HashMap<>();
		
		List<LatticeNode> nodes = new ArrayList<>();
		List<LatticeLink> links = new ArrayList<>();
		
		LatticeNode currentNode = null;
		LatticeLink currentLink = null;
		
		
		try (Scanner sc = new Scanner(is)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.startsWith("#")) {
					// comment line
					continue;
				}
				String[] parts = line.split("\\s+");
				
				for (String part : parts) {
					int equalsPos = part.indexOf('=');
					if (equalsPos > 0) {
						String key = part.substring(0, equalsPos);
						String abreviation = key.substring(0, 1);
						String value = part.substring(equalsPos+1);
						
						if (abreviation.equals("I")) {
							currentNode = nodes.get(Integer.parseInt(value));
						}
						
						if (abreviation.equals("J")) {
							currentLink = links.get(Integer.parseInt(value));
						}
						
						if (currentLink != null) {
							// must be a current link
							currentLink.parameters.put(abreviation,value);
						} else if (currentNode != null) {
							// must be a current node
							currentNode.parameters.put(abreviation, value);
						}
							
						if (currentNode == null) {
							// must still be in header
							if (abreviation.equals("N")) {
								int size = Integer.parseInt(value);
								for (int i = 0; i < size; i++) {
									nodes.add(new LatticeNode());
								}
							} else if (abreviation.equals("L")) {
								int size = Integer.parseInt(value);
								for (int i = 0; i < size; i++) {
									links.add(new LatticeLink());
								}
							} else {
								if (key.toLowerCase().equals(key)) {
									parameters.put(key, value);
								} else {
									parameters.put(abreviation, value);
								}
							}	
						}
						
					} else {
						errors.add("Parameter did not contain an equals sign");
						return null;
					}
				}
			} 
		}
		
		return makeGrammar(links, nodes, errors, parameters);
	}
	
	private AbstractGrammar makeGrammar(List<LatticeLink> links, List<LatticeNode> nodes, List<String> errors, Map<String,String> parameters) { 
		
		AbstractGrammar grammar = new AbstractGrammar();
		grammar.parameters = parameters;
	
		int l = 0;
		ArrayList<LatticeLink> linksToRemove = new ArrayList<>();
		for (LatticeLink link : links) {

			int start = Integer.parseInt(link.parameters.get("S"));
			int end = Integer.parseInt(link.parameters.get("E"));
			String word = link.parameters.get("W");
			
			LatticeNode startNode = nodes.get(start);
			LatticeNode endNode = nodes.get(end);
			
			// find the word string
			if (word == null) {
				word = endNode.parameters.get("W");
				if (word == null) {
					errors.add("Link ["+l+"] or node ["+end+"] did not contain a word");
					return grammar;
				}
			}
			
			link.node.value = word;
			startNode.links.add(link);
					
			//if (startNode.links.size() > branchingFactor) {
				// remove least link
				//link = startNode.links.poll();
				//end = Integer.parseInt(link.parameters.get("E"));
				//endNode = nodes.get(end);
				//endNode.links.remove(link);
			//	linksToRemove.remove(link);
			//}
			l++;
		}
		links.removeAll(linksToRemove);
		
		for (LatticeLink link : links) {
			int end = Integer.parseInt(link.parameters.get("E"));
			LatticeNode endNode = nodes.get(end);
			
			for (LatticeLink followingLink : endNode.links) {
				link.node.getChildren().add(followingLink.node);
			}
		}
		
		
		
		if (!nodes.isEmpty()) {
			grammar.rootRule = "START";
			Node startLink = new Node(NodeType.LatticeNode,null,0);
			for (LatticeLink nextStartLink : nodes.get(0).links) {
				startLink.getChildren().add(nextStartLink.node);
			}
			
			//startLink.setPassThru(true);
			grammar.rules.put("START", startLink);
		}
		
		return grammar;
	}
	
	
	public void determinize(AbstractGrammar grammar) {
		
		class Link {
			String word;
			Node endNode;
			
			public Link(String word, Node endNode) {
				super();
				this.word = word;
				this.endNode = endNode;
			}

			@Override
			public int hashCode() {
				HashCodeBuilder b = new HashCodeBuilder();
				b.append(word);
				b.append(endNode);
				return b.toHashCode();
			}
			
			@Override
			public boolean equals(Object obj) {
				if (obj instanceof Link) {
					Link l = (Link) obj;
					return word.equals(l.word) && endNode == l.endNode;
				}
				return false;
			}
			
		};
		
		class Transition {
			Set<Node> start;
			Set<Node> end;
			String word;
			public Transition(Set<Node> start, Set<Node> end, String word) {
				super();
				this.start = start;
				this.end = end;
				this.word = word;
			}
		};
		
		
		
		Node rootNode = grammar.rules.get(grammar.rootRule);
		Set<Node> initialNodeSet = new HashSet<>();
		initialNodeSet.add(rootNode);
		
		Deque<Set<Node>> unprocessedSubsets = new ArrayDeque<>();
		Set<Set<Node>> processedSubsets = new HashSet<>();
		unprocessedSubsets.add(initialNodeSet);
		processedSubsets.add(initialNodeSet);
		
		List<Transition> transitions = new ArrayList<>();
		/*
		class NodeCounter implements NodeVisitor {
			int count = 0;
			@Override
			public void visit(Node node) {
				count++;
			}
		}
		NodeCounter nodeCounter = new NodeCounter();
		rootNode.visitChildren(nodeCounter);
		*/
		while (!unprocessedSubsets.isEmpty()) {
			Set<Node> fromSet = unprocessedSubsets.poll();
			
			//Set<Link> forwardLinks = new HashSet<Link>();

			Map<String,List<Link>> linksByWord = new HashMap<>();
			for (Node node : fromSet) {
				//Node atom = linkChild.getChildI(0);
				//Node nextNode = linkChild.getChildI(0);
				for (Node childNode : node.getChildren()) {

					String word = childNode.value;
					linksByWord.computeIfAbsent(word, k -> new ArrayList<>()).add(new Link(word, childNode));
				}
				
			}
			
			for (Map.Entry<String,List<Link>> entry : linksByWord.entrySet()) {
				Set<Node> toSet = new HashSet<>();

				for (Link link : entry.getValue()) {
					toSet.add(link.endNode);
				}
				
				if (processedSubsets.add(toSet)) {
					unprocessedSubsets.add(toSet);
				}
				/*
				if (processedSubsets.size() > nodeCounter.count * blowoutFactor) {
					System.out.println("Terminate determinization algorithm if number of nodes gets too big");
					return;
				}
				*/
				transitions.add(new Transition(fromSet, toSet, entry.getKey()));
			}
		}
		
		ArrayList<LatticeNode> nodes = new ArrayList<>();
		Map<Set<Node>, LatticeNode> nodeMap = new HashMap<>();
		int i = 0;
		for (Set<Node> set : processedSubsets) {
			LatticeNode ln = new LatticeNode();
			ln.parameters.put("I", Integer.toString(i));
			nodeMap.put(set,ln);
			nodes.add(ln);
			i++;
		}
		
		ArrayList<LatticeLink> links = new ArrayList<>();
		int j = 0;
		for (Transition transition : transitions) {
			LatticeNode startNode = nodeMap.get(transition.start);
			LatticeNode endNode = nodeMap.get(transition.end);
			
			LatticeLink link = new LatticeLink();
			link.parameters.put("S", startNode.parameters.get("I"));
			link.parameters.put("E", endNode.parameters.get("I"));
			link.parameters.put("W", transition.word);
			link.parameters.put("J", Integer.toString(j));
			j = 0;
		}
		
		for (LatticeLink link : links) {
			int end = Integer.parseInt(link.parameters.get("E"));
			LatticeNode endNode = nodes.get(end);
			
			for (LatticeLink followingLink : endNode.links) {
				link.node.getChildren().add(followingLink.node);
			}
		}
		
		//grammar.rules.put(grammar.rootRule, nodeMap.get(initialNodeSet));
		
		grammar = makeGrammar(links, nodes, new ArrayList<String>(), new HashMap<String,String>());
	}
}
