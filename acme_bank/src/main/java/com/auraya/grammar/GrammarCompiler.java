package com.auraya.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface GrammarCompiler {

	/**
	 * This method compiles the contents of the InputStream to produce a Grammar object.
	 * Any errors are returned in the error List.
	 * 
	 * @param is The InputStream containing the grammar.
	 * @param errors A list of errors.
	 * @return A compiled Grammar object.
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public abstract AbstractGrammar compile(InputStream is, List<String> errors) throws NumberFormatException, IOException;

}