package com.auraya.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.auraya.grammar.Node.NodeType;


/**
 * Compiler for ABNF grammars.
 * 
 * There is support for NON_CAPTURING (NC) custom extension. 
 * This extension is relevant for robust parsing where the NC group provides context,
 * but does not contribute toward the word count. 
 * 
 * A NC group is specified as 
 * 		(?= group contents )
 * 
 * 
 * @author Jamie Lister
 * @version 1.0
 */
public class ABNFCompiler implements GrammarCompiler {

	final static String	IMPLIED				= "_";
	final static String	OPTION				= "[";
	final static String	NON_CAPTURING		= "&?=";
	final static String	REGULAR				= "&";
	final static String	CHOICE				= "|";
	final static String	REPEATED			= "<>";
	final static String	DEFAULT_RULE_NAME	= "$DEFAULT_RULE";
	
	enum CompileState {
		IN_RULE, IN_DEFAULT_RULE, BEFORE_RULE, AFTER_RULE_EQUALS, IN_TAG, IN_COMMENT
	};

	static enum TokenType {
				END_OF_STREAM, NO_MATCH, WHITESPACE("\\s+", true), 
				RULE_NAME("\\$_*[A-Za-z]+[A-Za-z0-9_]*", true), 
				END_RULE(";"), 
				EQUALS("="), 
				RULE_ALTERNATIVE("|"), 
				WEIGHT( "\\/([0-9]+|[0-9]+\\.[0-9]*|[0-9]*\\.[0-9]+)\\/", true), 	
				AND_OPEN_NON_CAPTURING("(?="), 
				AND_OPEN("("), 
				AND_CLOSE(")"), 
				OPTION_OPEN("["), 
				OPTION_CLOSE("]"), 
				REPEAT("<(([0-9]+)(-)?|([0-9]+)-([0-9]+)|(-)?([0-9]+))>", true), 
				COMMENT_OPEN("/*"), 
				COMMENT_CLOSE("*/"), 
				COMMENT_SINGLE_LINE("(\\/\\/).*", true), 
				TAG_OPEN("{"), TAG_CLOSE("}"), 
				TAG_TOKEN("[^{}\"']+|[\"']", true), 
				COMMENT_TOKEN( "([^*]|\\*[^/])+", true), 
				DOUBLE_QUOTE("\"(\\\\.|[^\"\\\\]|)+\"", true), 
				SINGLE_QUOTE("'(\\\\.|[^'\\\\])+'", true), 
				TOKEN("_*[a-zA-Z'][0-9a-zA-Z.,_'-]*", true), 
				HANDLE( "\\:_*[a-zA-Z][0-9a-zA-Z_]*", true);

		Pattern	p			= null;
		String	s			= null;
		boolean	isRegexp	= false;

		private TokenType(String s, boolean isRegex) {
			this.s = s;
			this.isRegexp = isRegex;
			if (isRegexp) {
				p = Pattern.compile(s);
			}
		}

		private TokenType(String s) {
			this.s = s;
		}

		private TokenType() {
		}

		static List<TokenType>	abnfTokens		= Arrays.asList(WHITESPACE, TOKEN, AND_OPEN_NON_CAPTURING, AND_OPEN,  AND_CLOSE, OPTION_OPEN, OPTION_CLOSE, RULE_ALTERNATIVE, TAG_OPEN, RULE_NAME, COMMENT_OPEN, COMMENT_SINGLE_LINE, END_RULE, EQUALS,  WEIGHT, REPEAT,  HANDLE);

		static List<TokenType>	tagTokens		= Arrays.asList(WHITESPACE, COMMENT_OPEN, COMMENT_CLOSE, COMMENT_SINGLE_LINE, TAG_OPEN, TAG_CLOSE, DOUBLE_QUOTE, SINGLE_QUOTE, TAG_TOKEN);

		static List<TokenType>	commentTokens	= Arrays.asList(COMMENT_CLOSE, COMMENT_TOKEN);

	};

	class StreamState extends LineNumberReader {

		String	line	= "";
		String	what;
		int		charNumber;
		short	column;
		Matcher	matcher	= Pattern.compile(" ").matcher("");

		public StreamState(Reader reader) {
			super(reader);
		}

		public TokenType getToken(Tokenizer tokenizer) throws IOException {

			while (line.isEmpty()) {
				if ((line = readLine()) != null) {
					line += "\n";
					column = 0;
				} else {
					return TokenType.END_OF_STREAM;
				}
			}
			matcher.reset(line);
			for (TokenType tokenType : tokenizer.list) {
				if (tokenType.isRegexp) {
					matcher.usePattern(tokenType.p);

					if (matcher.lookingAt()) {
						what = matcher.group();
						line = line.substring(what.length());
						charNumber += what.length();
						column += what.length();
						return tokenType;
					}

				} else {
					if (line.startsWith(tokenType.s)) {
						what = tokenType.s;
						// start += what.length();
						line = line.substring(what.length());

						charNumber += what.length();
						column += what.length();
						return tokenType;
					}
				}
			}

			return TokenType.NO_MATCH;
		}
	}

	static class Tokenizer {
		List<TokenType>	list;

		public Tokenizer(List<TokenType> list) {
			this.list = list;
		}

	}

	static Tokenizer	abnfTokenizer		= new Tokenizer(TokenType.abnfTokens);
	static Tokenizer	tagTokenizer		= new Tokenizer(TokenType.tagTokens);
	static Tokenizer	commentTokenizer	= new Tokenizer(TokenType.commentTokens);

	public AbstractGrammar compile(InputStream is, List<String> errors) throws NumberFormatException, IOException {

		AbstractGrammar grammar = new AbstractGrammar();
		
		StreamState ss = new StreamState(new InputStreamReader(is));

		String rootRuleName = null;
		String currentRuleName = null;
		CompileState state = CompileState.BEFORE_RULE;
		CompileState commentState = CompileState.BEFORE_RULE;

		String tag = null;
		Node currentTaggableNode = null;
		Node currentNode = null;
		String error = "Unknown error";

		TokenType type;
		short openBrackets = 0;

		String weight = null;

		boolean ok = true;

		Tokenizer tokenizer = abnfTokenizer;

		while (ok && (type = ss.getToken(tokenizer)) != TokenType.END_OF_STREAM) {

			if (type == TokenType.NO_MATCH) {
				error = "No match";
				ok = false;
				break;
			}

			// ignore comments
			if (state == CompileState.IN_COMMENT) {
				if (type == TokenType.COMMENT_CLOSE) {
					state = commentState;
					tokenizer = abnfTokenizer;
				}
				continue;
			}

			if (state == CompileState.IN_TAG) {
				if (type == TokenType.TAG_CLOSE) {
					if (openBrackets == 0) {
						tokenizer = abnfTokenizer;
						currentTaggableNode.getTags().add(new Tag(tag, ss.getLineNumber(), ss.column));
						state = CompileState.IN_RULE;
						continue;
					} else {
						openBrackets--;
					}
				}
				if (type == TokenType.TAG_OPEN) {
					openBrackets++;
				}
				tag += ss.what;
				continue;
			}

			if (type == TokenType.COMMENT_OPEN) {
				commentState = state;
				state = CompileState.IN_COMMENT;
				tokenizer = commentTokenizer;
				continue;
			}

			if (type == TokenType.COMMENT_SINGLE_LINE) {
				// do nothing!
				continue;
			}

			if (type == TokenType.WHITESPACE) {
				continue;
			}

			ok = false;

			if (state == CompileState.AFTER_RULE_EQUALS) {
				state = CompileState.IN_RULE;
			}

			if (state == CompileState.BEFORE_RULE) {
				switch (type) {

				case RULE_NAME: {
					if (grammar.rules.get(ss.what) == null) {
						currentRuleName = ss.what;
						ok = true;
					}
					break;
				}
				case EQUALS: {
					if (currentRuleName != null) {
						state = CompileState.AFTER_RULE_EQUALS;
						currentNode = new Node(NodeType.AndNode, IMPLIED, ss.getLineNumber());
						ok = true;
						break;
					}
				}
				default: {
					// this means we are defining a ROOT rule (!)
					if (rootRuleName == null) {
						currentRuleName = DEFAULT_RULE_NAME;
						rootRuleName = currentRuleName;
						state = CompileState.IN_DEFAULT_RULE;
						currentNode = new Node(NodeType.AndNode, IMPLIED, ss.getLineNumber());
						ok = true;
					} else {
						error = "Redefinition of default root rule";
					}
				}
				}
			}

			if (state == CompileState.IN_RULE || state == CompileState.IN_DEFAULT_RULE) {

				String andNodeType = REGULAR;
				switch (type) {

				case END_RULE: {
					if (weight != null) {
						error = "Weight does not apply here";
						break;
					}

					ok = true;
					// All AND nodes must have at least one child
					// Explicit AND nodes must be completed (i.e. should NOT be
					// ancestor)
					while (currentNode.parent != null) {
						if (currentNode.type == NodeType.AndNode && (!currentNode.hasChildren() || !currentNode.value.equals(IMPLIED))) {
							ok = false;
							error = "Imcomplete ( or [";
							break;
						}
						currentNode = currentNode.parent;
					}

					if (!ok) {
						break;
					}

					// default is to use the first rule in the file
					if (grammar.rootRule == null) {
						grammar.rootRule = currentRuleName;
					}

					grammar.rules.put(currentRuleName, currentNode);
					state = CompileState.BEFORE_RULE;
					currentRuleName = null;

					break;
				}

				case TOKEN: {
					Node node = new Node(NodeType.Atom, ss.what, ss.getLineNumber());
					currentTaggableNode = node;
					currentNode.addChild(node);

					if (weight != null) {
						node.setWeight(Float.parseFloat(weight));
						weight = null;
					}

					ok = true;
					break;
				}
				case HANDLE: {
					if (currentTaggableNode.type == NodeType.RuleRef) {
						ok = true;
						currentTaggableNode.handle = ss.what.substring(1);
					} else {
						error = "Handle can only follow a rule reference";
					}
					break;
				}
				case WEIGHT: {
					if (weight != null) {
						error = "Multiple weights";
						break;
					}
					weight = ss.matcher.group(1);
					ok = true;
					break;
				}
				case REPEAT: {
					if (weight != null) {
						error = "Weight does not apply here";
						break;
					}

					if (currentTaggableNode == null) {
						error = "Repeat does not apply here";
						break;
					}
					Node repeatNode = new Node(NodeType.Repeat, REPEATED, ss.getLineNumber());
					repeatNode.makeAsParentOf(currentTaggableNode);
					// NUM
					if (ss.matcher.group(2) != null) {
						short repeats = Short.parseShort(ss.matcher.group(2));
						repeatNode.minRepeats = repeats;
						repeatNode.maxRepeats = repeats;
					}
					// NUM-
					if (ss.matcher.group(3) != null) {
						repeatNode.maxRepeats = Node.MAX_REPEATS;
					}

					// NUM-NUM
					if (ss.matcher.group(4) != null && ss.matcher.group(5) != null) {
						repeatNode.minRepeats = Short.parseShort(ss.matcher.group(4));
						repeatNode.maxRepeats = Short.parseShort(ss.matcher.group(5));
					}

					// -NUM
					if (ss.matcher.group(7) != null) {
						short repeats = Short.parseShort(ss.matcher.group(7));
						repeatNode.minRepeats = 0;
						repeatNode.maxRepeats = repeats;
					}
					ok = true;
					if (repeatNode.minRepeats == 0) {
						repeatNode.minRepeats = 1;
						repeatNode.isPassThru = true;
					}
					break;
				}
				case RULE_NAME: {
					Node node = new Node(NodeType.RuleRef, ss.what, ss.getLineNumber());
					currentTaggableNode = node;
					currentNode.addChild(node);
					
					switch (ss.what) {
						case AbstractGrammar.NULL_RULE_NAME:
							node.isPassThru = true;
							break;
						case AbstractGrammar.VOID_RULE_NAME:
							node.isPassThru = false;
						default:
					}
					
					

					if (weight != null) {
						node.setWeight(Float.parseFloat(weight));
						weight = null;
					}

					ok = true;
					break;
				}
				case RULE_ALTERNATIVE: {
					if (weight != null) {
						error = "Weight does not apply here";
						break;
					}

					// we must have an "implied" AND node, not empty
					if (currentNode.type != NodeType.AndNode && !currentNode.value.equals(IMPLIED) || !currentNode.hasChildren()) {
						break;
					}

					Node parent = currentNode.parent;

					// is this the first alternative so far? if so then create
					// an OR node, make it the currentNode
					if (parent == null) {
						Node orNode = new Node(NodeType.OrNode, CHOICE, ss.getLineNumber());
						orNode.addChild(currentNode);
						currentNode = orNode;
					} else if (parent.type != NodeType.OrNode) {
						Node orNode = new Node(NodeType.OrNode, CHOICE, ss.getLineNumber());
						orNode.makeAsParentOf(currentNode);
						currentNode = orNode;
					} else {
						currentNode = parent;
					}

					// create implied AND node, make it the currentNode
					Node impliedAndNode = new Node(NodeType.AndNode, IMPLIED, ss.getLineNumber());
					currentNode.addChild(impliedAndNode);
					currentNode = impliedAndNode;
					currentTaggableNode = null;
					ok = true;
					break;
				}
				case OPTION_OPEN:
					andNodeType = OPTION;
				case AND_OPEN_NON_CAPTURING: 
					if (REGULAR.equals(andNodeType)) {
						andNodeType = NON_CAPTURING;
					}
				case AND_OPEN: {
					// create regular AND node
					Node andNode = new Node(NodeType.AndNode, andNodeType, ss.getLineNumber());
					currentNode.addChild(andNode);
					currentNode = andNode;
					
					if (weight != null) {
						andNode.setWeight(Float.parseFloat(weight));
						weight = null;
					}

					
					// create new implied AND
					Node impliedAndNode = new Node(NodeType.AndNode, IMPLIED, ss.getLineNumber());
					
					
					currentNode.addChild(impliedAndNode);
					currentNode = impliedAndNode;

					ok = true;
					break;
				}
				case OPTION_CLOSE:
					andNodeType = OPTION;
				case AND_CLOSE: {
					// find closest matching AND node parent
					// check that all IMPLIED AND nodes are not empty
					boolean loop = true;
					while (loop) {

						if (currentNode.type == NodeType.AndNode && currentNode.value.equals(IMPLIED) && !currentNode.hasChildren()) {
							loop = false;
							error = "Empty alternative";
							continue;
						}

						if (currentNode.type == NodeType.AndNode && currentNode.value.startsWith(andNodeType)) {
							loop = false;
							ok = true;
							currentTaggableNode = currentNode;

							if (andNodeType == OPTION) {
								currentNode.isPassThru = true;
								//currentNode.minRepeats = 0;
								//currentNode.maxRepeats = 1;
							}
						}

						currentNode = currentNode.parent;

						if (currentNode.parent == null) {
							loop = false;
						}
					}
					break;
				}
				case TAG_OPEN: {

					if (currentTaggableNode != null) {

						if (weight != null) {
							error = "Weight does not apply here";
							break;
						}

						tag = "";
						openBrackets = 0;
						tokenizer = tagTokenizer;
						state = CompileState.IN_TAG;
						ok = true;
					} else {
						error = "Can't have tag here";
					}

					break;
				}
				default:
				}
			}
		}

		if (ok && state == CompileState.IN_DEFAULT_RULE) {
			// All AND nodes must have at least one child
			// Explicit AND nodes must be completed (i.e. should NOT be
			// ancestor)
			while (currentNode.parent != null) {
				if (currentNode.type == NodeType.AndNode && (!currentNode.hasChildren() || !currentNode.value.equals(IMPLIED))) {
					ok = false;
					error = "Imcomplete ( or [";
					break;
				}
				currentNode = currentNode.parent;
			}

			if (ok) {
				grammar.rules.put(currentRuleName, currentNode);
				state = CompileState.BEFORE_RULE;
				grammar.rootRule = currentRuleName;
			}
		}

		if (state != CompileState.BEFORE_RULE) {
			ok = false;
		}

		if (!ok) {

			String message = error + " at token [" + ss.what + "] on line [" + ss.getLineNumber() + "]\n\n";
			errors.add(message);
		}

		ss.close();
		
		grammar.calculateNumberOfNodes();
		return grammar;
	}

	void printGrammar(AbstractGrammar grammar) {
		for (Map.Entry<String, Node> it : grammar.rules.entrySet()) {
			System.out.print(it.getKey() + " = ");
			printNode(it.getValue(), 0);
			System.out.print("\n;\n\n");
		}
	}

	void printNode(Node node, int depth) {

		if (Math.abs(node.getWeight() - 1.0F) > 0.00001F) {
			System.out.print("/" + node.getWeight() + "/ ");
		}

		if (node.type == NodeType.Atom || node.type == NodeType.RuleRef) {
			System.out.print(node.value);
			if (node.handle != null) {
				System.out.print(node.handle);
			}
		}
		if (node.type == NodeType.AndNode) {
			String andSymbolOpen = "";
			String andSymbolClose = "";
			if (node.value.equals(REGULAR)) {
				andSymbolOpen = "(";
				andSymbolClose = ")";
			}
			if (node.value.equals(OPTION)) {
				andSymbolOpen = "[";
				andSymbolClose = "]";
			}

			System.out.print(andSymbolOpen);
			boolean first = true;
			for (Node it : node.getChildren()) {
				if (!first) {
					System.out.print(" ");
				}
				first = false;
				printNode(it, depth);
			}
			System.out.print(andSymbolClose);

		}
		if (node.type == NodeType.OrNode) {
			String indent = "";
			String alternative = " | ";
			String firstAlternative = "";
			if (node.getChildren().size() > 5) {
				indent = "\n";
				for (int i = 0; i <= depth; i++) {
					indent += "\t";
				}
				alternative = "| ";
				firstAlternative = "  ";
				depth++;
			}

			boolean first = true;
			for (Node it : node.getChildren()) {
				System.out.print(indent);
				System.out.print(first ? firstAlternative : alternative);
				first = false;

				printNode(it, depth);
			}
		}

		if (node.isPassThru || node.minRepeats != 1 || node.maxRepeats != 1) {
			int minRepeats = node.isPassThru ? 0 : node.minRepeats;
			System.out.print(" <" + minRepeats);
			if (minRepeats < node.maxRepeats) {
				System.out.print("-");
				if (node.maxRepeats < Node.MAX_REPEATS) {
					System.out.print(node.maxRepeats);
				}
			}
			System.out.print("> ");
		}

		if (node.hasTags()) {
			for (Tag t : node.getTags()) {
				System.out.print("\t{" + t.tag + "} ");
			}
		}

	}

}
