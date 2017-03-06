package org.dice.solrenhancements.queryparsers;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.dice.solrenhancements.JarVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by simon.hughes on 7/31/15.
 */
public class QueryBoostingQParserPlugin extends QParserPlugin {
    public static String NAME = "queryboost";

    @Override
    public void init(NamedList args) {
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new QueryBoostingQParser(qstr, localParams, params, req);
    }

    private static final Logger Log = LoggerFactory.getLogger(QueryBoostingQParserPlugin.class);

    private String version = null;

    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        }

        version = JarVersion.getVersion(Log);
        return version;
    }
}
