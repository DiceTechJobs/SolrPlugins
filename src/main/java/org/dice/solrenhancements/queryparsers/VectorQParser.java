package org.dice.solrenhancements.queryparsers;

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.ExtendedDismaxQParser;

/**
 * Created by simon.hughes on 3/29/14.
 */

public class VectorQParser extends PayloadAwareExtendDismaxQParser {

    public VectorQParser(
            java.lang.String qstr,
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
}
