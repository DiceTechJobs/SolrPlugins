package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

public class Or extends BinaryOperator {

	public Or(Expression left, Expression right){
		super(left, right);
	}

	public String evaluate() {
		return String.format("(%s OR %s)", left.evaluate(), right.evaluate());
	}
}
