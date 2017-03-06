package org.dice.parsing;

import org.apache.commons.lang.StringUtils;
import org.dice.parsing.ast.Expression;
import org.dice.parsing.ast.operands.Operand;
import org.dice.parsing.ast.operators.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by simon.hughes on 4/12/16.
 */
public class RecursiveDescentParser {

    private Lexer lexer;
    private int symbol;
    // used whenever a malformed expression is missing an expected token
    private final String missingTokenValue;
    private Expression root;
    private Set<Integer> errors;

    private static final String SPACE = " ";

    public RecursiveDescentParser(Lexer lexer, String missingTokenValue) {
        this.lexer  = lexer;
        this.missingTokenValue = missingTokenValue;
        this.symbol = Lexer.NONE;
        // don't reset parse errors
        this.errors = new HashSet<Integer>();
    }

    public Expression parse() {
        orExpression();
        if(symbol != Lexer.EOF){
            // unbalanced parens
            if(symbol == Lexer.RIGHT){
                this.errors.add(ParserErrors.MissingLeftParen.value);
            }
            else{
                this.errors.add(ParserErrors.MalFormedExpression.value);
            }
        }
        return root;
    }

    public Set<Integer> getErrors(){
        return this.errors;
    }

    public boolean hasErrors(){
        return this.errors.size() > 0;
    }

    private void orExpression() {
        andExpression();
        // treat TOKEN TOKEN as (TOKEN OR TOKEN)
        while (symbol == Lexer.OR) {
            Expression left = root;
            andExpression();
            Expression right = root;
            root = new Or(left, right);
        }
    }

    private void andExpression() {
        sequenceExpression();
        while (symbol == Lexer.AND) {
            Expression left = root;
            sequenceExpression();
            Expression right = root;
            root = new And(left, right);
        }
    }

    private void sequenceExpression(){
        term();
        while (symbol == Lexer.TOKEN || symbol == Lexer.QUOTE || symbol == Lexer.FIELD) {
            Expression left = root;
            processTerminal();
            Expression right = root;
            root = new Or(left, right);
        }
    }

    private boolean isNotQuoteOrEOF(int symbol){
        return symbol != Lexer.QUOTE && symbol != Lexer.EOF;
    }

    private void quotedExpression() {
        StringBuilder quotedPhrase = new StringBuilder();
        // read all symbols (including boolean operators and parens) until a quote is reached.
        // if no quote reached, eat the rest of the expression as quoted phrase (what else can we do?)
        while(isNotQuoteOrEOF(symbol = lexer.nextSymbol())){
            // use just the string representations
            quotedPhrase.append(lexer.toString()).append(SPACE);
        }

        String phrase = quotedPhrase.toString().trim();
        if(StringUtils.isBlank(phrase)) {
            errors.add(ParserErrors.MissingQuoteCharacter.value);
        }
        // insert with empty string if needed (otherwise you have a hanging AND or OR
        // - (java AND "") is better than (java AND )
        root = new Quote(new Operand(phrase));
        symbol = lexer.nextSymbol();
    }

    private void term() {
        symbol = lexer.nextSymbol();
        processTerminal();
    }

    private void processTerminal() {
        switch (symbol){
            case Lexer.TOKEN:
                root = new Operand(lexer.toString());
                symbol = lexer.nextSymbol();
                break;

            case Lexer.FIELD:
                final String fieldName = lexer.toString();
                term();
                root = new FieldQuery(fieldName, root);
                break;

            case Lexer.LEFT:
                orExpression();
                if(symbol == Lexer.EOF){
                    this.errors.add(ParserErrors.MissingRightParen.value);
                    // missing parentheses, ignore, thus inserting one or more at the end
                    break;
                }
                if(symbol != Lexer.RIGHT){
                    this.errors.add(ParserErrors.MissingRightParen.value);
                }
                symbol = lexer.nextSymbol();
                break;

            case Lexer.NOT:
                term();
                root = new Not(root);;
                break;

            case Lexer.QUOTE:
                quotedExpression();
                break;

            default:
                // if mal formed, insert an empty quote
                root = new Operand(this.missingTokenValue);
                errors.add(ParserErrors.MalFormedExpression.value);
        }
    }

}
