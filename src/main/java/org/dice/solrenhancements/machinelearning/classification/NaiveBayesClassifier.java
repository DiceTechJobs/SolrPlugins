package org.dice.solrenhancements.machinelearning.classification;

import org.apache.lucene.index.IndexReader;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.SolrIndexSearcher;
import org.dice.solrenhancements.JarVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by simon.hughes on 12/17/15.
 */
public class NaiveBayesClassifier extends SearchComponent{

    private static final Logger LOG = LoggerFactory.getLogger(NaiveBayesClassifier.class);
    protected IndexReader reader;


    public NaiveBayesClassifier(){}

    @Override
    public void prepare(ResponseBuilder responseBuilder) throws IOException {

    }

    @Override
    public void process(ResponseBuilder responseBuilder) throws IOException {

    }

    @Override
    public String getDescription() {
        return NaiveBayesClassifier.class.getName();
    }


    @Override
    public void init(NamedList args) {
        super.init(args);

    }

    public void build(SolrCore core, SolrIndexSearcher searcher) throws IOException {
        LOG.info("build()");

        reader = searcher.getIndexReader();
        //dictionary = new HighFrequencyDictionary(reader, field, threshold);

        // first, use class above to get all terms above the frequency
        // then execute a pivot facet
    }

    private String version = null;

    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        }

        version = JarVersion.getVersion(LOG);
        return version;
    }
}

