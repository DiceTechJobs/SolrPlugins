package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

public class Not extends UnaryOperator {
	public Not(Expression child){
		super(child);
	}

	public String evaluate() {
		return String.format("NOT %s", child.evaluate());
	}
}
