package org.dice.parsing;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by simon.hughes on 4/14/16.
 */
public class TestQueryLexer {

    @Test
    public void identifiesAndQuery(){
        assertTrue(isAndQuery("java And .net"));
        assertTrue(isAndQuery("java AnD .net"));
        assertTrue(isAndQuery("java anD .net"));
        assertTrue(isAndQuery("java AND .net"));
        assertTrue(isAndQuery("java AND .net sql"));
        assertTrue(isAndQuery("java AND .net AND ruby"));
    }

    @Test
    public void identifiesAdvancedQuery() {
        assertTrue(isAdvancedQuery("\".net developer\""));
        assertTrue(isAdvancedQuery(".net or developer"));
        assertTrue(isAdvancedQuery(".net And developer"));
        assertTrue(isAdvancedQuery("(.net And php) or developer"));
        assertTrue(isAdvancedQuery("(.net And php)"));
        assertTrue(isAdvancedQuery("\"java"));
        assertTrue(isAdvancedQuery("java)"));
        assertTrue(isAdvancedQuery("not java)"));
        assertTrue(isAdvancedQuery(".net developer\""));
        assertTrue(isAdvancedQuery("java And \".net developer\""));
        assertTrue(isAdvancedQuery("java And \".net developer\" or (sql and ruby)"));
    }

    @Test
    public void doesNotIdentifyAndWhenAbsent(){
        assertFalse(isAndQuery("java Or .net"));
        assertFalse(isAndQuery("java OR .net"));
        assertFalse(isAndQuery("java oR .net"));
        assertFalse(isAndQuery("java or .net"));
        assertFalse(isAndQuery("java OR .net sql"));
        assertFalse(isAndQuery("java OR .net Or ruby"));
        assertFalse(isAndQuery("java"));
        assertFalse(isAndQuery("NOT java"));
        assertFalse(isAndQuery("\"java developer\""));
        assertFalse(isAndQuery("java OR (sql)"));
        assertFalse(isAndQuery("java OR (sql or ruby)"));
    }

    @Test
    public void doesNotIdentifyBasicQueryAsAdvanced() {
        assertFalse(isAdvancedQuery("java"));
        assertFalse(isAdvancedQuery("java .net"));
        assertFalse(isAdvancedQuery("java developer"));
        assertFalse(isAdvancedQuery("java developer with .net"));
    }

    private boolean isAndQuery(String query){
        QueryLexer lexer = new QueryLexer(query);
        return lexer.isAndQuery();
    }

    private boolean isAdvancedQuery(String query){
        QueryLexer lexer = new QueryLexer(query);
        return lexer.isAdvancedQuery();
    }

}
