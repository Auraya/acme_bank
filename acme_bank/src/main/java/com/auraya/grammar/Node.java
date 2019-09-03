package com.auraya.grammar;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.FastMath;



/**
 * This represents a Node in the grammar, such as Atom, Repeat, Serial, Parallel, etc.
 * Together, a network of Nodes defines a Grammar.
 * 
 * @author Jamie Lister
 * @version 1.0
 */
public class Node implements Iterable<Node>, Serializable {
	
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 1L;
	public static final Node finalNode = new Node(NodeType.Generic,"FINAL",0);
	public static final short MAX_REPEATS = Short.MAX_VALUE;
	
	private Collection<Tag> tags; // = new ArrayList<>();
	Node parent = null;
	private  Collection<Node> children; // = new ArrayList<>();
	NodeType type;
	String value;

	private float weight = 1.0F;
	float logWeight = 0.0F;
	String handle = null;
	short minRepeats = 1;
	short maxRepeats = 1;
	boolean isPassThru = false; 

	transient int lineNumber;
	transient private AttachedData attachedData;
	 
	public interface AttachedData {
		AttachedData deepCopy();
		Set<Integer> getList();
	}
	
	public Collection<Node> getChildren() {
		if (children == null) {
			children = new ArrayList<>();
		}
		return children;
	}
	
	public boolean hasChildren() {
		return children != null && !children.isEmpty();
	}
	
	public Collection<Tag> getTags() {
		if (tags == null) {
			tags = new ArrayList<>();
		}
		return tags;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getAttachedData() {
		return (T) attachedData;
	}
	
	public <T extends AttachedData> void setAttachedData(T data) {
		attachedData = (AttachedData) data;
	}
	
	//private int wordId;
	//private List<Variant> wordVariants;
	//private Map<Object,Object> cachedTriphoneWordVariants;
	
	public Node(NodeType nodeType, String value, int lineNumber) {
		this.type = nodeType;
		this.value = value;
		this.lineNumber = lineNumber;
	}
	
	public Node(String value, int lineNumber, Comparator<Node> nodeComparator) {
		this.type = NodeType.OrNode;
		this.value = value;
		this.lineNumber = lineNumber;
		children = new TreeSet<Node>(nodeComparator);
	}
	
	private Node() {}
	

	/*
	public Node copy() {
		Node copy = new Node(type, value, lineNumber);
		copy.minRepeats = minRepeats;
		copy.maxRepeats = maxRepeats;
		copy.isPassThru = isPassThru;
		copy.handle = handle;
		copy.weight = weight;
		copy.parent = parent;
		Collection<Node> newChildren;
		if (children instanceof TreeSet<?>) {
			TreeSet<Node> childrenSet = (TreeSet<Node>) children;
			newChildren = new TreeSet<>(childrenSet.comparator());
		} else {
			newChildren = new ArrayList<>();
		} 
		newChildren.addAll(children);
		copy.children = newChildren;
		copy.tags = new ArrayList<>(tags);
		copy.attachedData = attachedData;
		return copy;
	}
	*/
	
	
	public Node deepCopy() {
		Node copy = new Node();
		copy.type = type;
		copy.value = value;
		copy.lineNumber = lineNumber;
		copy.minRepeats = minRepeats;
		copy.maxRepeats = maxRepeats;
		copy.isPassThru = isPassThru;
		copy.handle = handle;
		copy.weight = weight;
		copy.logWeight = logWeight;
		copy.parent = parent;
		if (children != null) {
			if (children instanceof TreeSet<?>) {
				TreeSet<Node> childrenSet = (TreeSet<Node>) children;
				copy.children = new TreeSet<>(childrenSet.comparator());
			} else {
				copy.children = new ArrayList<>();
			} 
			for (Node child : children) {
				copy.addChild(child.deepCopy());
			}
		}
		if (tags != null) {
			copy.tags = new ArrayList<>(tags);
		}
		if (attachedData != null) {
			copy.attachedData = attachedData.deepCopy();
		}
		return copy;
	}
	
	
	public void copyFrom(Node node, boolean combinePassThru) {

		type = node.type;
		value = node.value;
		lineNumber = node.lineNumber;
		
		minRepeats = node.minRepeats;
		maxRepeats = node.maxRepeats;
		if (combinePassThru) {
			isPassThru |= node.isPassThru;
		} else {
			isPassThru = node.isPassThru;
		}
		handle = node.handle;
		weight = node.weight;
		logWeight = node.logWeight;
		if (node.children != null) {
			if (node.children instanceof TreeSet<?>) {
				TreeSet<Node> childrenSet = (TreeSet<Node>) node.children;
				children = new TreeSet<>(childrenSet.comparator());
			} else {
				children = new ArrayList<>();
			} 
			children.addAll(node.children);
		}
		if (tags != null) {
			tags = new ArrayList<>(node.tags);
		}
		attachedData = node.attachedData;
	}
	
	
	
	public NodeType getType() {
		return type;
	}
	
	public short getMinRepeats() {
		return minRepeats;
	}
	
	public short getMaxRepeats() {
		return maxRepeats;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	public Node getParent() {
		return parent;
	}
	
	/**
	 * If the underlying child collection is a list, then the Ith child is returned. Otherwise, the first child is returned.
	 * 
	 * @param i
	 * @return
	 */
	public Node getChildI(int i) {
		if (children instanceof List) {
			return ((List<Node>)children).get(i);
		}
		return children.iterator().next();
	}
	
	//public Collection<Node> getChildren() {
	//	return children;
	//}
	
	public int getNumChildren() {
		if (children != null) {
			return  children.size();
		} else {
			return 0;
		}
		
	}
	
	public void removeAllChildren(Collection<Node> nodes) {
		if (children != null) {
			if (nodes == null) {
				children.clear();
			} else {
				children.removeAll(nodes);
			}
		}
	}

	
	public String getHandle() {
		return handle;
	}
	
	public float getWeight() {
		return weight;
	}
	
	public float getLogWeight() {
		return logWeight;
	}
	
	public boolean isPassThru() {
		return isPassThru;
	}

	public void setPassThru(boolean isPassThru) {
		this.isPassThru = isPassThru;
	}
	
	public void combinePassThru(boolean isPassThru) {
		this.isPassThru |= isPassThru;
	}

	public String getValue() {
		return value;
	}
	public enum NodeType {
		Generic,
		Repeat,
		RuleRef,
		Atom,
		OrNode,
		AndNode,
		LatticeNode
	}
	
	static public interface NodeVisitor {
		void visit(Node node);
	}
	
	public void visitChildren(NodeVisitor visitor) {
		visitor.visit(this);
		if (children != null) {
			for (Node child : children) {
				child.visitChildren(visitor);
			}
		}
	}

	public void addChild(Node node) {
		node.parent = this;
		getChildren().add(node);
	}
	
	public void addChildNodes(Collection<Node> nodes) {
		for (Node node : nodes) {
			addChild(node);
		}
	}
	
	
	void makeAsParentOf(Node currentNode) {
		Node currentNodeParent = currentNode.parent;
		
		// re-link node
		
		// 1) remove currentNode from it's parent
		currentNodeParent.getChildren().remove(currentNode);
		
				
		// 2) add nodes
		addChild(currentNode);
		currentNodeParent.addChild(this);
	}

	@Override
	public String toString() {
		return "Node [type=" + type + ", value="
				+ value + ", @="+hashCode()+"]";
	}
	
	/*
	public void linkRuleRefs(Map<String,Node> rules) {
		if (type == NodeType.RuleRef) {
			if (children.isEmpty()) {
				Node rule = rules.get(value);
				children.add(rule);
				rule.linkRuleRefs(rules);
			}
		} else {
			for (Node child : children) {
				child.linkRuleRefs(rules);
			}
		}
	}
*/
	public void setValue(String value) {
		this.value = value;
	}
	
	public void setWeight(float weight) {
		this.weight = weight;
		this.logWeight = (float) FastMath.log(weight);
	}

	public void setType(NodeType type) {
		this.type = type;
	}

	public void print(PrintStream out, Map<String,Node> rules, int depth) {
		String PT = isPassThru ? "*" : "";
		String repeats = type == NodeType.Repeat ? " <"+minRepeats+"-"+maxRepeats+">" : "";
		System.out.println(StringUtils.leftPad(type+PT, depth+1)+", value="+value + ","+repeats+" hash="+hashCode());
		if (type != NodeType.RuleRef) {
			for (Node child : getChildren()) {
				child.print(out, rules, depth+1);
			}
		} else {
			Node ruleNode = rules.get(value);
			ruleNode.print(out, rules, depth+1);
		}
		
	}
	

	public void setNodeComparator(Comparator<Node> nodeComparator) {
		if (type == NodeType.OrNode) {
			 TreeSet<Node> temp = new TreeSet<>(nodeComparator);
			 if (children != null) {
				 temp.addAll(children);
			 }
			 children = temp;
		}
	}

	public void setMaxRepeats(int maxRepeats) {
		this.maxRepeats = (short) maxRepeats;
	}
	
	public void setMinRepeats(int minRepeats) {
		this.minRepeats = (short) minRepeats;
	}

	@Override
	public Iterator<Node> iterator() {
		return getChildren().iterator();
	}

	void serialise(DataOutput out) throws IOException {

		out.writeByte(type.ordinal());
		//out.writeInt(lineNumber);
		boolean writeValue = false;
		switch (type) {
			case OrNode: writeValue = !"|".equals(value); break;
			case AndNode: writeValue = !"&".equals(value) && !"[".equals(value) && !"_".equals(value); break;
			default: writeValue = value != null;
		}
			
		out.writeBoolean(writeValue);
		if (writeValue) {
			out.writeUTF(value);
		} else {
			if (type == NodeType.AndNode) {
				out.writeByte(value.getBytes()[0]);
			}
		}
		
		boolean special = isPassThru || maxRepeats != 1 || minRepeats != 1 || weight != 1.0F || !tags.isEmpty();
		out.writeBoolean(special);
		if (special) {
			out.writeBoolean(isPassThru);
			out.writeShort(maxRepeats);
			out.writeShort(minRepeats);
			out.writeFloat(weight);
			
			if (hasTags()) {
				out.writeShort(tags.size());
				for (Tag tag : tags) {
					tag.serialise(out);
				}
			} else {
				out.writeShort(0);
			}
		}
		
		if (type == NodeType.RuleRef) {
			out.writeBoolean(handle != null);
			if (handle != null) {
				out.writeUTF(handle);
			}
		}
		
		if (type != NodeType.Atom && type != NodeType.RuleRef) {
			out.writeInt(getNumChildren());
			if (hasChildren()) {
				for (Node node : children) {
					node.serialise(out);
				}
			}
		} 
	}
	
	public Node(DataInput in) throws IOException {
		type = NodeType.values()[in.readByte()];
		boolean readValue = in.readBoolean();
		if (readValue) {
			value = in.readUTF();
		} else {
			if (type == NodeType.OrNode) {
				value = "|";
			}
			if (type == NodeType.AndNode) {
				value = new String(new byte[] {in.readByte()});
			}
		}
		boolean special = in.readBoolean();
		if (special) {
			isPassThru = in.readBoolean();
			maxRepeats = in.readShort();
			minRepeats = in.readShort();
			weight = in.readFloat();
			logWeight = (float) FastMath.log(weight);
			short numTags = in.readShort();
			if (numTags > 0) {
				tags = new ArrayList<Tag>(numTags);
				for (int i = 0; i < numTags; i++) {
					tags.add(new Tag(in));
				}
			}
		}
		
		if (type == NodeType.RuleRef) {
			if (in.readBoolean()) {
				handle = in.readUTF();
			}
		}
		
		if (type != NodeType.Atom && type != NodeType.RuleRef) {
			int childrenSize = in.readInt();
			if (childrenSize > 0) {
				children = new ArrayList<Node>(childrenSize);
			
				for (int i = 0; i < childrenSize; i++) {
					addChild(new Node(in));
				}
			}
		} 
	}

	public boolean hasTags() {
		return tags != null && !tags.isEmpty();
	}
	
}


