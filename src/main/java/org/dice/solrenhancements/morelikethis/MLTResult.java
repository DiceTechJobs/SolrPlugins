package org.dice.solrenhancements.morelikethis;

import org.apache.lucene.search.Query;
import org.apache.solr.search.DocListAndSet;

import java.util.List;

/**
 * Created by simon.hughes on 1/6/17.
 */
public class MLTResult {
    private final List<MLTTerm> mltTerms;
    private final Query finalMltQuery;
    private DocListAndSet results;

    public MLTResult(List<MLTTerm> mltTerms, Query finalMltQuery, DocListAndSet results){
        this.mltTerms = mltTerms;
        this.finalMltQuery = finalMltQuery;
        this.results = results;
    }

    public DocListAndSet getResults() {
        return results;
    }

    public List<MLTTerm> getMltTerms(){
        return mltTerms;
    }

    public Query getQuery() {
        return finalMltQuery;
    }
}
