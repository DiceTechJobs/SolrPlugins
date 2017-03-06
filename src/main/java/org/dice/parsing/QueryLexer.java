package org.dice.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 4/14/16.
 */
public class QueryLexer extends Lexer {

    private List<Integer> symbols = new ArrayList<Integer>();
    private List<String> tokens   = new ArrayList<String>();
    private int currentIndex = -1;

    private boolean isAndQuery = false;
    private boolean isAdvancedQuery = false;

    public QueryLexer(String s) {
        super(s);

        int symbol;
        while ( (symbol = super.nextSymbol()) != Lexer.EOF){
            symbols.add(symbol);
            switch (symbol){
                case Lexer.AND:
                    this.isAndQuery = true;
                case Lexer.OR:
                case Lexer.NOT:
                case Lexer.LEFT:
                case Lexer.RIGHT:
                    this.isAdvancedQuery = true;
                    break;
                case Lexer.QUOTE:
                    this.isAdvancedQuery = true;
                    break;
            }
            tokens.add(super.toString());
        }
    }

    @Override
    public int nextSymbol(){
        currentIndex++;
        if(currentIndex >= this.symbols.size()){
            return Lexer.EOF;
        }
        if(currentIndex < 0){
            return Lexer.NONE;
        }
        return symbols.get(currentIndex);
    }

    public void reset(){
        currentIndex = -1;
    }

    @Override
    public String toString(){
        if(currentIndex >= this.symbols.size() || currentIndex < 0){
            return "";
        }
        return this.tokens.get(currentIndex);
    }

    public boolean isAdvancedQuery() {
        return isAdvancedQuery;
    }

    public boolean isAndQuery() {
        return isAndQuery;
    }
}
