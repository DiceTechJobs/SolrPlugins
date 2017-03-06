package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

/**
 * Created by simon.hughes on 4/14/16.
 */
public class FieldQuery implements Expression {

    private final String field;
    private final Expression expression;

    public FieldQuery(String field, Expression expression){

        this.field = field;
        this.expression = expression;
    }

    public String evaluate() {
        return String.format("%s(%s)", this.field, this.expression.evaluate());
    }
}
