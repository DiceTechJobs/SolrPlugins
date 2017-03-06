package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

public class And extends BinaryOperator {
	public And(Expression left, Expression right){
		super(left, right);
	}

	public String evaluate() {
		return String.format("(%s AND %s)", left.evaluate(), right.evaluate());
	}
}
