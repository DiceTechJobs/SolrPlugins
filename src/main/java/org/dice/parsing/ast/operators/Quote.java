package org.dice.parsing.ast.operators;


import org.dice.parsing.ast.Expression;

public class Quote extends UnaryOperator {
	public Quote(Expression expression){
		super(expression);
	}

	public String evaluate() {
		return String.format("\"%s\"", child.evaluate());
	}
}
