package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

/**
 * Created by simon.hughes on 6/2/15.
 */
public class FieldLenValueSourceParser extends ValueSourceParser {
    public void init(NamedList namedList) {
    }

    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        String indexedField = fp.parseArg();
        Analyzer analyzer = fp.getReq().getSchema().getAnalyzer();

        return new FieldLenValueSource(indexedField, analyzer);
    }
}