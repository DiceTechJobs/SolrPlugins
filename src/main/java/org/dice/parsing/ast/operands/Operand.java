package org.dice.parsing.ast.operands;

import org.dice.parsing.ast.Expression;

public class Operand implements Expression {
	protected String value;

	public Operand(String value) {
		this.value = value;
	}

	public String evaluate() {
		return String.format("%s", value);
	}

	@Override
	public String toString(){
		return this.evaluate();
	}
}
