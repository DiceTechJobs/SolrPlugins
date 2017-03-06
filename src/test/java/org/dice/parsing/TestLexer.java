package org.dice.parsing;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by simon.hughes on 4/13/16.
 */
public class TestLexer {

    @Test
    public void ignoresDelimiters() {

        Assert.assertArrayEquals(
                new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN},
                tokenize("java,,,,aNd sql"));
        Assert.assertArrayEquals(
                new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN},
                tokenize("java,and;sql"));
        Assert.assertArrayEquals(
                new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN},
                tokenize("java,AND    sql"));
        Assert.assertArrayEquals(
                new Integer[]{Lexer.TOKEN, Lexer.OR, Lexer.TOKEN},
                tokenize("java   OR    sql"));
    }

    @Test
    public void recognizesQuotedPhrases() {

        Assert.assertArrayEquals(
                new Integer[]{Lexer.QUOTE, Lexer.TOKEN, Lexer.TOKEN, Lexer.QUOTE},
                tokenize("\"java developer\""));
        Assert.assertArrayEquals(
                new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.QUOTE, Lexer.TOKEN,Lexer.TOKEN, Lexer.QUOTE},
                tokenize("ruby AND \"java developer\""));
    }
    //text:"java developer"
    @Test
    public void tokenizesFieldQueries() {

        Assert.assertArrayEquals(
                new Integer[]{Lexer.FIELD, Lexer.TOKEN, Lexer.TOKEN},
                tokenize("text:java developer"));

        Assert.assertArrayEquals(
                new Integer[]{Lexer.FIELD, Lexer.QUOTE, Lexer.TOKEN, Lexer.TOKEN, Lexer.QUOTE},
                tokenize("text:\"java developer\""));
    }

    @Test
    public void ignoresCase() {

        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN}, tokenize("java and sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN}, tokenize("java And sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN}, tokenize("java aND sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.AND, Lexer.TOKEN}, tokenize("java AND sql"));

        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.OR, Lexer.TOKEN}, tokenize("java or sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.OR, Lexer.TOKEN}, tokenize("java OR sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.OR, Lexer.TOKEN}, tokenize("java oR sql"));
        Assert.assertArrayEquals(new Integer[]{Lexer.TOKEN, Lexer.OR, Lexer.TOKEN}, tokenize("java Or sql"));
    }

    private Integer[] tokenize(String input){
        List<Integer> lst = Lexer.tokenize(input);
        Integer[] a = new Integer[lst.size()];
        return lst.toArray(a);
    }
}
