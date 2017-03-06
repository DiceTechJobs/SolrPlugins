package org.dice.solrenhancements.queryparsers;

import org.dice.solrenhancements.JarVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger Log = LoggerFactory.getLogger(PayloadAwareExtendedDismaxQParserPlugin.class);

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