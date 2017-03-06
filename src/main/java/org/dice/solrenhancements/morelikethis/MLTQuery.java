package org.dice.solrenhancements.morelikethis;

import org.apache.lucene.queries.payloads.AveragePayloadFunction;
import org.apache.lucene.queries.payloads.PayloadScoreQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.solr.util.SolrPluginUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 11/25/14.
 */
public class MLTQuery {

    private final List<MLTTerm> mltTerms;
    private final String mm;
    private BooleanQuery mustMatchQuery = null;
    private BooleanQuery mustNOTMatchQuery = null;

    public MLTQuery(List<MLTTerm> mltTerms, String mm){
        this.mltTerms = mltTerms == null? new ArrayList<MLTTerm>() : mltTerms;
        this.mm = mm;
    }
    public BooleanQuery getMustMatchQuery(){
        return this.mustMatchQuery;
    }

    public void setMustMatchQuery(BooleanQuery query){
        this.mustMatchQuery = query;
    }

    public Query getMustNOTMatchQuery(){
        return this.mustNOTMatchQuery;
    }

    public void setMustNOTMatchQuery(BooleanQuery query){
        this.mustNOTMatchQuery = query;
    }

    public List<MLTTerm> getMltTerms(){
        return mltTerms;
    }

    public Query getOrQuery(){
        BooleanQuery.Builder qryBuilder = new BooleanQuery.Builder();
        for(MLTTerm mltTerm: this.mltTerms){
            qryBuilder.add(toBoostedQuery(mltTerm), BooleanClause.Occur.SHOULD);
        }
        SolrPluginUtils.setMinShouldMatch(qryBuilder, mm);
        return qryBuilder.build();
    }

    private Query toBoostedQuery(MLTTerm mltTerm){
        Query tq = toTermQuery(mltTerm);
        return new BoostQuery(tq, mltTerm.getFinalScore());
    }

    private Query toTermQuery(MLTTerm mltTerm) {
        if(mltTerm.hasPayload()) {
            return new PayloadScoreQuery(new SpanTermQuery(mltTerm.getTerm()), new AveragePayloadFunction(), false);
        }
        else{
            return new TermQuery(mltTerm.getTerm());
        }
    }
}
