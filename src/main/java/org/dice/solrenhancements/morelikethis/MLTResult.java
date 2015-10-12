package org.dice.solrenhancements.morelikethis;

import org.apache.lucene.search.Query;
import org.apache.solr.search.DocListAndSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 11/25/14.
 */
public class MLTResult {

    public final List<MLTTerm> mltTerms;
    public final Query rawMLTQuery;
    private Query mustMatchQuery = null;
    private Query mustNOTMatchQuery = null;

    private DocListAndSet doclist;

    public MLTResult(List<MLTTerm> mltTerms, Query rawMLTQuery){

        this.mltTerms = mltTerms == null? new ArrayList<MLTTerm>() : mltTerms;
        this.rawMLTQuery = rawMLTQuery;
    }

    public DocListAndSet getDoclist() {
        return doclist;
    }

    public void setDoclist(DocListAndSet doclist) {
        this.doclist = doclist;
    }

    public Query getMustMatchQuery(){
        return this.mustMatchQuery;
    }

    public void setMustMatchQuery(Query query){
        this.mustMatchQuery = query;
    }

    public Query getMustNOTMatchQuery(){
        return this.mustNOTMatchQuery;
    }

    public void setMustNOTMatchQuery(Query query){
        this.mustNOTMatchQuery = query;
    }
}
