package org.dice.parsing;

/**
 * Created by simon.hughes on 4/14/16.
 */
public enum  ParserErrors {
    MissingLeftParen(1),
    MissingRightParen(2),
    MissingQuoteCharacter(3),
    MalFormedExpression(4);

    public int value;
    ParserErrors(int value){
        this.value = value;
    }
}
