package org.dice.solrenhancements.queryparsers;

/**
 * Created by simon.hughes on 3/29/14.
 */
public class PayloadAwareExtendedDismaxQParserPlugin extends org.apache.solr.search.ExtendedDismaxQParserPlugin {
    public static final java.lang.String NAME = "payloadEdismax";

    public PayloadAwareExtendedDismaxQParserPlugin() {
        super();
    }

    public org.apache.solr.search.QParser createParser(
            java.lang.String qstr,
            org.apache.solr.common.params.SolrParams localParams,
            org.apache.solr.common.params.SolrParams params,
            org.apache.solr.request.SolrQueryRequest req)
    {
        return new PayloadAwareExtendDismaxQParser(qstr, localParams, params, req);
    }
}