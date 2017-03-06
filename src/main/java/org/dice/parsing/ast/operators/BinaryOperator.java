package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

public abstract class BinaryOperator implements Expression {
	protected Expression left, right;

    BinaryOperator(Expression left, Expression right){
        this.left = left;
        this.right = right;
    }

	@Override
	public String toString(){
		return this.evaluate();
	}
}
