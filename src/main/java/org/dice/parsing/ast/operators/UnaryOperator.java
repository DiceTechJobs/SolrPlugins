package org.dice.parsing.ast.operators;

import org.dice.parsing.ast.Expression;

/**
 * Created by simon.hughes on 4/14/16.
 */
public abstract class UnaryOperator implements Expression {
    protected Expression child;

    UnaryOperator(Expression child){
        this.child = child;
    }

    @Override
    public String toString(){
        return this.evaluate();
    }
}
