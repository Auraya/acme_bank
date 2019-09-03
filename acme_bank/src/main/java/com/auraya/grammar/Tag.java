package com.auraya.grammar;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Represents a Tag on a grammar node. A tag contains arbitrary text to be interpreted during parsing.
 * Line and column information is provided for aide in debugging messages.
 * 
 * @author Jamie Lister
 * @version 1.0
 */
public class Tag implements Serializable {
	
	private static final long	serialVersionUID	= 1L;
	String tag;
	int lineNumber;
	short column;
	Object cachedTag;
	
	public Tag(String tag, int lineNumber, short column) {
		super();
		this.tag = tag;
		this.lineNumber = lineNumber;
		this.column = column;
	}

	public Tag(DataInput in) throws IOException {
		tag = in.readUTF();
	}

	public void serialise(DataOutput out) throws IOException {
		//out.writeBoolean(tag != null);
		//if (tag != null) {
			out.writeUTF(tag);
		//}
		//out.writeInt(lineNumber);
		//out.writeInt(column);
	}

	

}
