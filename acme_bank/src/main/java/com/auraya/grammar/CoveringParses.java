package com.auraya.grammar;

import java.util.ArrayDeque;
import java.util.Iterator;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.auraya.grammar.AbstractGrammar.Parse;


/**
 * A list of parses that 'cover' a string of words. All sub parses do not overlap with each other.
 * 
 * @author Jamie Lister
 * @version 1.0
 *
 */
public class CoveringParses implements Comparable<CoveringParses> {
	ArrayDeque<Parse> parses = new ArrayDeque<>();
	short numWords;
	float logWeight;
	int numNodes;
	int hashcode = 0;
	
	@Override
	public String toString() {
		String s = "";
		for (Parse parse : parses) {
			s += ("start="+parse.start + ", finish="+parse.finish);
		}
		return s;
	}


	@Override
	public int compareTo(CoveringParses other) {
		int v = Short.compare(numWords, other.numWords);
		if (v != 0) {
			return v;
		}
		v = Integer.compare(other.parses.size(), parses.size());
		if (v != 0) {
			return v;
		}
		v =  Float.compare(logWeight, other.logWeight);
		if (v != 0) {
			return v;
		}
		
		return Integer.compare(other.numNodes, numNodes);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CoveringParses) {
			CoveringParses cp = (CoveringParses) obj;
			if (parses.size() != cp.parses.size()) {
				return false;
			}
			if (parses.isEmpty()) {
				return true;
			}
			Iterator<Parse> i1 = parses.iterator();
			Iterator<Parse> i2 = cp.parses.iterator();
			while (i1.hasNext() && i2.hasNext()) {
				if (!i1.next().equals(i2.next())) {
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
			for (Parse parse : parses) {
				hcb.append(parse);
			}
			hashcode = hcb.toHashCode();
		}
		return hashcode;
	}
}