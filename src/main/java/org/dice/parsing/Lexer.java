package org.dice.parsing;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Created by simon.hughes on 4/12/16.
 */
public class Lexer {

    private Scanner input;
    private String inputString;

    private int symbol = NONE;
    private String currentToken   = "";

    public static final int EOF   = -1;
    public static final int TOKEN = 999;
    public static final int FIELD = 9;

    public static final int NONE  = 0;

    public static final int OR    = 1;
    public static final int AND   = 2;
    public static final int NOT   = 3;

    public static final int LEFT  = 6;
    public static final int RIGHT = 7;
    public static final int QUOTE = 8;

    private static final Pattern SEPARATOR = Pattern.compile("[ ,;]");

    private static final String sLPAREN = "(";
    private static final String sRPAREN = ")";
    private static final String sAND = "and";
    private static final String sOR = "or";
    private static final String sNOT = "not";
    private static final String sQUOTE = "\"";

    private static HashMap<String, Integer> stringToCode = generateStringToCode();
    private static HashMap<String, Integer> generateStringToCode(){
        HashMap<String, Integer> hm = new HashMap<String, Integer>();

        hm.put(sLPAREN, LEFT);
        hm.put(sRPAREN, RIGHT);

        hm.put(sOR,  OR);
        hm.put(sAND, AND);
        hm.put(sNOT, NOT);
        hm.put(sQUOTE, QUOTE);
        return hm;
    }

    public Lexer(String s) {
        inputString = s;
        input = new Scanner(processInputString(s));
        input.useDelimiter(SEPARATOR);
    }

    public int nextSymbol() {
        if(!input.hasNext()){
            this.currentToken = "";
            return EOF;
        }

        this.currentToken = input.next();
        // consume empty tokens
        while (StringUtils.isBlank(this.currentToken)){
            this.currentToken = input.next();
            if(!input.hasNext()){
                this.currentToken = "";
                return EOF;
            }
        }

        String lcToken = this.currentToken.toLowerCase();
        if(stringToCode.containsKey(lcToken)){
            symbol = stringToCode.get(lcToken);
        }
        else{
            if(lcToken.endsWith(":")){
                symbol = FIELD;
            }
            else{
                symbol = TOKEN;
            }
        }
        return symbol;
    }

    public static List<Integer> tokenize(String inputString){
        // create a new lexer so as not to reset this one
        Lexer temp = new Lexer(inputString);
        List<Integer> symbols = new ArrayList<Integer>();
        int symbol;
        while ( (symbol = temp.nextSymbol()) != Lexer.EOF){
            symbols.add(symbol);
        }
        return symbols;
    }

    public String toString() {
        return this.currentToken;
    }

    private String processInputString(String s) {
        if(s == null){
            return "";
        }
        else{
            // force parens are separate tokens
            return s.trim()
                    // ensure field queries are processed as a single expression
                    .replaceAll("(\\s+:\\s+|\\s+:|:\\s+|:)", ": ")
                    // add spaces around quotes and parens to ensure parsed as separate tokens
                    .replaceAll("([\\(\\)\"])", " $1 ")
                    .replaceAll("\\s+", " ");
        }
    }

    public static void main(String[] args) {
        //String expression = "title:senior title: ruby title :python  title : xml java,or (.net AND SQL)";
        String expression = "java or ruby AND xml oR sql or (.net And developer)";
        Lexer l = new Lexer(expression);
        Integer s;
        while ( (s = l.nextSymbol()) != Lexer.EOF){
            System.out.printf("%s -> %s\n", StringUtils.leftPad(s.toString(),3), l);
        }
    }
}