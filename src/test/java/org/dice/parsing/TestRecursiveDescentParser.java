package org.dice.parsing;

import org.dice.parsing.ast.Expression;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.assertEquals;
/**
 * Created by simon.hughes on 4/13/16.
 */
public class TestRecursiveDescentParser {

    private static final String WILDCARD_TOKEN = "*:*";

    @Test
    public void handlesFieldQueries()
    {
        assertEquals("text:(java)", parseWithoutErrors("text:java"));
        assertEquals("text:(\"java developer\")", parseWithoutErrors("text:\"java developer\""));
        assertEquals("(text:(\"java developer\") OR title:(\".net developer\"))", parseWithoutErrors("text:\"java developer\" title:\".net developer\""));
        assertEquals("(text:(\"java developer\") OR title:(.net))", parseWithoutErrors("text:\"java developer\" title:.net"));
        assertEquals("(text:(java) OR developer)", parseWithoutErrors("text:java developer"));
        assertEquals("(text:(java) OR developer)", parseWithoutErrors("text:java Or developer"));
        assertEquals("((sql AND text:(java)) OR developer)", parseWithoutErrors("sql And text:java Or developer"));
        assertEquals("(text:(java) OR (developer AND sql))", parseWithoutErrors("text:java Or developer And sql"));

        assertEquals("(text:(java) OR developer)", parseWithoutErrors("text:java developer"));
        assertEquals("((ruby OR text:(java)) OR developer)", parseWithoutErrors("ruby text:java developer"));
        assertEquals("((text:(java) OR developer) OR ruby)", parseWithoutErrors("text:java developer ruby"));
        assertEquals("((text:(java) OR developer) OR ruby)", parseWithErrors("text:java developer ruby)"));
        assertEquals("((text:(java) OR developer) OR ruby)", parseWithErrors("(text:java developer ruby"));
        assertEquals("(text:(\"java developer\") OR title:(ruby))", parseWithoutErrors("text:\"java developer\" title:ruby"));
    }

    // ignore for now
    //@Test
    public void handlesRangeQueries()
    {
        // erlang AND (fYearlyRate:[20000 TO 50000] OR fYearlyRate:[60000 TO 120000])
        assertTrue(false);
    }

    @Test
    public void handlesQuotes()
    {
        assertEquals("(\"java developer\" OR ruby)", parseWithoutErrors("\"java developer\" or ruby"));
        assertEquals("(\"java developer\" OR ruby)", parseWithoutErrors("\"java developer\" or ruby"));
        assertEquals("\"java developer\"", parseWithoutErrors("\"java developer\""));
        assertEquals("(sql AND \"java developer\")", parseWithoutErrors("sql and \"java developer\""));
    }

    @Test
    public void handlesEmptyQuotes()
    {
        assertEquals("(java AND \"\")", parseWithErrors("java AND \"\""));
        assertEquals("(java OR \"\")", parseWithErrors("java OR \"\""));
    }

    @Test
    public void insertsMissingQuote()
    {
        assertEquals("\"java developer\"", parseWithoutErrors("\"java developer"));
        assertEquals("\"java developer aNd ruby\"", parseWithoutErrors("\"java developer aNd ruby"));
        assertEquals("\"java developer oR ruby\"", parseWithoutErrors("\"java developer oR ruby"));
        assertEquals("(java OR \"developer oR ruby\")", parseWithoutErrors("java oR \"developer oR ruby"));

        // This we won't handle, but that's ok as I don't think we should (tricky to auto-correct
        // - where was the quote supposed to start? beginning of line, end of line?)
        //assertEquals("\"java developer\"", parseWithoutErrors("java developer\""));
    }

    @Test
    public void insertsMissingLeftParen(){
        assertEquals("(java AND sql)", parseWithErrors("java aNd sql)"));
        assertEquals("(java AND sql)", parseWithErrors("java aNd sql))"));
        assertEquals("((java AND sql) OR ruby)", parseWithErrors("(java aNd sql) or ruby)"));
        assertEquals("(ruby OR (java AND sql))", parseWithErrors("ruby or (java aNd sql))"));
        assertEquals("(ruby OR (java AND sql))", parseWithErrors("ruby or java aNd sql))"));
    }

    @Test
    public void insertsMissingRightParen(){
        // should always insert additional right parens on the rhs
        assertEquals("(java AND sql)", parseWithErrors("(java aNd sql"));
        assertEquals("(sql OR (java AND sql))", parseWithErrors("sql or (java aNd sql"));
        assertEquals("(sql OR (java AND sql))", parseWithErrors("(sql or (java aNd sql"));
        assertEquals("(sql OR (java AND sql))", parseWithErrors("(sql or (java aNd sql)"));
        assertEquals("(sql OR ((java AND sql) OR ruby))", parseWithErrors("(sql or (java aNd sql or ruby"));
        assertEquals("((sql OR (java AND sql)) OR ruby)", parseWithErrors("(sql or (java aNd sql) or ruby"));
    }

    @Test
    public void insertsOrWhenOperatorsOmitted() {
        assertEquals("((java OR sql) OR ruby)", parseWithoutErrors("java sql ruby"));
        assertEquals("(java OR sql)", parseWithoutErrors("java sql"));
        assertEquals("(java AND (sql OR hadoop))", parseWithoutErrors("java And sql hadoop"));
        assertEquals("(java OR (sql OR hadoop))", parseWithoutErrors("java OR sql hadoop"));
        assertEquals("(((sql OR server) OR \"java developer\") OR hadoop)", parseWithoutErrors("sql server \"java developer\" hadoop"));
        assertEquals("(\"java developer\" OR hadoop)", parseWithoutErrors("\"java developer\" hadoop"));
        assertEquals("(hadoop OR \"java developer\")", parseWithoutErrors("hadoop OR \"java developer\""));
        assertEquals("(hadoop AND \"java developer\")", parseWithoutErrors("hadoop AnD \"java developer\""));
        assertEquals("(\"sql server\" OR \"java developer\")", parseWithoutErrors("\"sql server\" \"java developer\""));
        assertEquals("((ruby OR \"sql server\") OR \"java developer\")", parseWithoutErrors("ruby \"sql server\" \"java developer\""));
        assertEquals("((\"sql server\" OR \"java developer\") OR python)", parseWithoutErrors("\"sql server\" \"java developer\" python"));
    }

    @Test
    // if expression is malformed, insert quotation marks
    public void insertsWildcardWhenMissingOperand() {
        assertEquals(String.format("(java AND %s)", WILDCARD_TOKEN), parseWithErrors("java And"));
        assertEquals(String.format("(java OR %s)", WILDCARD_TOKEN) , parseWithErrors("java OR"));
        assertEquals(String.format("(%s AND java)", WILDCARD_TOKEN), parseWithErrors("AND java"));
        assertEquals(String.format("(%s OR java)", WILDCARD_TOKEN), parseWithErrors("OR java"));
    }

    @Test
    public void addsErrorMessageOnMissingLeftParen(){
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("java or hadoop)"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("(java or hadoop))"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("sql AND (java or hadoop))"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("hadoop)"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("hadoop))"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("hadoop)))"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("java hadoop)))"));
        assertEquals(ParserErrors.MissingLeftParen.value, getParserError("((java hadoop)))"));
    }

    @Test
    public void addsErrorMessageOnMissingRightParen(){
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(java or hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("((java or hadoop)"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("sql AND (java or hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(sql AND (java or hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("((hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(((hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(((java hadoop"));
        assertEquals(ParserErrors.MissingRightParen.value, getParserError("(((java hadoop))"));
    }

    @Test
    public void addsErrorMalformedExpression() {
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("java NOT"));
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("sql Or java NOT"));
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("java or"));
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("java AND"));
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("AND java"));
        assertEquals(ParserErrors.MalFormedExpression.value, getParserError("Or java"));
    }

    @Test
    public void ignoresDelimiters() {
        assertEquals("(java AND sql)", parseWithoutErrors("java,,,,aNd sql"));
        assertEquals("(java AND sql)", parseWithoutErrors("java,and;sql"));
        assertEquals("(java AND sql)", parseWithoutErrors("java;AND   sql"));
        assertEquals("(java AND sql)", parseWithoutErrors("java     AND   sql"));
    }

    @Test
    public void ignoresCase(){
        assertEquals("(java AND sql)", parseWithoutErrors("java and sql"));
        assertEquals("(java AND sql)", parseWithoutErrors("java aNd sql"));
        assertEquals("(java AND sql)", parseWithoutErrors("java AND sql"));

        assertEquals("(java OR sql)", parseWithoutErrors("java or sql"));
        assertEquals("(java OR sql)", parseWithoutErrors("java oR sql"));
        assertEquals("(java OR sql)", parseWithoutErrors("java Or sql"));
        assertEquals("(java OR sql)", parseWithoutErrors("java OR sql"));
    }

    @Test
    public void enforcesOperatorPrecedence(){
        assertEquals("((java AND sql) OR ruby)", parseWithoutErrors("java and sql or ruby"));
        assertEquals("(java OR (sql AND ruby))", parseWithoutErrors("java or sql AnD ruby"));
    }

    @Test
    // Note that solr always parses NOT correctly as it's a unary operator (and the reasons for the
    // issues with boolean operator precedence resolve around converting boolean to unary operators
    public void parsesNotOperator(){
        // Note - see comments above
        assertEquals("NOT java", parseWithoutErrors("not java"));
        assertEquals("(NOT java AND sql)", parseWithoutErrors("not java and sql"));
        assertEquals("((NOT java AND sql) OR ruby)", parseWithoutErrors("not java and sql or ruby"));
        assertEquals("(NOT (java AND sql) OR ruby)", parseWithoutErrors("not (java and sql) or ruby"));

    }

    private String parseWithoutErrors(String input){
        RecursiveDescentParser parser = new RecursiveDescentParser(new Lexer(input), WILDCARD_TOKEN);
        final Expression ast = parser.parse();
        assertFalse(parser.hasErrors());
        return ast.evaluate();
    }

    private String parseWithErrors(String input){

        RecursiveDescentParser parser = new RecursiveDescentParser(new Lexer(input), WILDCARD_TOKEN);
        final Expression ast = parser.parse();
        assertTrue(parser.hasErrors());
        return ast.evaluate();
    }

    private int getParserError(String input){
        RecursiveDescentParser parser = new RecursiveDescentParser(new Lexer(input),  WILDCARD_TOKEN);
        final Expression ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertEquals(1, parser.getErrors().size());
        return parser.getErrors().iterator().next();
    }
}
