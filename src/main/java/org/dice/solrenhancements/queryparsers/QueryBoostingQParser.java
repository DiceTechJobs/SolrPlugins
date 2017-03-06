package org.dice.solrenhancements.queryparsers;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SyntaxError;

/**
 * Created by simon.hughes on 3/29/14.
 */

public class QueryBoostingQParser extends ExtendedDismaxQParser {

    public QueryBoostingQParser(
            String qstr,
            SolrParams localParams,
            SolrParams params,
            SolrQueryRequest req)
    {
        super(preProcessQuery(qstr,localParams, params, req), localParams, params, req);
    }

    private static String preProcessQuery(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req){
        // prevent white space tokenization
        ExtendedDismaxQParser qParser = new ExtendedDismaxQParser(qstr.replace(" ",","), localParams, params, req);
        try{
            Query query = qParser.parse();
            return query.toString();
        }
        catch (Exception ex){
            return "ERROR";
        }
    }

    @Override
    protected org.apache.solr.search.ExtendedDismaxQParser.ExtendedSolrQueryParser createEdismaxQueryParser(org.apache.solr.search.QParser qParser, java.lang.String field)
    {
        return new TermQueryOnlyExtendedSolrQueryParser(qParser, field);
    }

    public static class TermQueryOnlyExtendedSolrQueryParser extends ExtendedDismaxQParser.ExtendedSolrQueryParser {

        public TermQueryOnlyExtendedSolrQueryParser(org.apache.solr.search.QParser parser, java.lang.String defaultField) {
            super(parser, defaultField);
        }

        @Override
        protected Query getFieldQuery(String field, String queryText, boolean quoted) throws SyntaxError {

            if(queryText.contentEquals("|")){
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                return builder.build();
            }

            return new TermQuery(new Term(field, queryText));
        }
    }
}
