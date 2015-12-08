package org.dice.solrenhancements.morelikethis;

/**
 * Created by simon.hughes on 9/2/14.
 */

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for MoreLikeThis that can be called from other request handlers
 */
public class MoreLikeThisHelper
{
    // Pattern is thread safe -- TODO? share this with general 'fl' param
    private static final Pattern splitList = Pattern.compile(",| ");

    final SolrIndexSearcher searcher;
    final QParser qParser;
    final MoreLikeThis mlt;
    final IndexReader reader;
    final SchemaField uniqueKeyField;
    final boolean needDocSet;


    public MoreLikeThisHelper( SolrParams params, SolrIndexSearcher searcher, SchemaField uniqueKeyField, QParser qParser )
    {
        this.searcher = searcher;
        this.qParser = qParser;
        this.reader = searcher.getIndexReader();
        this.uniqueKeyField = uniqueKeyField;
        this.needDocSet = params.getBool(FacetParams.FACET, false);

        SolrParams required = params.required();
        String[] fields = splitList.split(required.get(MoreLikeThisParams.SIMILARITY_FIELDS));
        if( fields.length < 1 ) {
            throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,
                    "MoreLikeThis requires at least one similarity field: "+MoreLikeThisParams.SIMILARITY_FIELDS );
        }

        this.mlt = new MoreLikeThis( reader );
        mlt.setFieldNames(fields);

        final String flMustMatch = params.get(MoreLikeThisParams.FL_MUST_MATCH);
        if( flMustMatch != null && flMustMatch.trim().length() > 0 ) {
            String[] mustMatchFields = splitList.split(flMustMatch.trim());
            mlt.setMatchFieldNames(mustMatchFields);
        }

        final String flMustNOTMatch = params.get(MoreLikeThisParams.FL_MUST_NOT_MATCH);
        if( flMustNOTMatch != null && flMustNOTMatch.trim().length() > 0 ) {
            String[] differntMatchFields = splitList.split(flMustNOTMatch.trim());
            mlt.setDifferentFieldNames(differntMatchFields);
        }

        String[] payloadFields = getFieldList(MoreLikeThisParams.PAYLOAD_FIELDS, params);
        if(payloadFields != null){
            mlt.setPayloadFields(payloadFields);
        }
        mlt.setAnalyzer( searcher.getSchema().getIndexAnalyzer() );

        // configurable params

        mlt.setMinTermFreq(       params.getInt(MoreLikeThisParams.MIN_TERM_FREQ,         MoreLikeThis.DEFAULT_MIN_TERM_FREQ));
        mlt.setMinDocFreq(        params.getInt(MoreLikeThisParams.MIN_DOC_FREQ,          MoreLikeThis.DEFAULT_MIN_DOC_FREQ));
        mlt.setMaxDocFreq(        params.getInt(MoreLikeThisParams.MAX_DOC_FREQ,          MoreLikeThis.DEFAULT_MAX_DOC_FREQ));
        mlt.setMinWordLen(        params.getInt(MoreLikeThisParams.MIN_WORD_LEN,          MoreLikeThis.DEFAULT_MIN_WORD_LENGTH));
        mlt.setMaxWordLen(        params.getInt(MoreLikeThisParams.MAX_WORD_LEN,          MoreLikeThis.DEFAULT_MAX_WORD_LENGTH));

        mlt.setBoost(             params.getBool(MoreLikeThisParams.BOOST, true ) );

        // new parameters
        mlt.setBoostFn(params.get(MoreLikeThisParams.BOOST_FN));
        mlt.setNormalizeFieldBoosts(params.getBool(MoreLikeThisParams.NORMALIZE_FIELD_BOOSTS, MoreLikeThis.DEFAULT_NORMALIZE_FIELD_BOOSTS));
        // new versions of previous parameters moved to the field level
        mlt.setMaxQueryTermsPerField(params.getInt(MoreLikeThisParams.MAX_QUERY_TERMS_PER_FIELD, MoreLikeThis.DEFAULT_MAX_QUERY_TERMS_PER_FIELD));
        mlt.setMaxNumTokensParsedPerField(params.getInt(MoreLikeThisParams.MAX_NUM_TOKENS_PARSED_PER_FIELD, MoreLikeThis.DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD));
        mlt.setLogTf(params.getBool(MoreLikeThisParams.IS_LOG_TF, MoreLikeThis.DEFAULT_IS_LOG_TF));

        mlt.setBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(MoreLikeThisParams.QF)));
        mlt.setStreamBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(MoreLikeThisParams.STREAM_QF)));

        String streamHead = params.get(MoreLikeThisParams.STREAM_HEAD);
        if(streamHead != null) {
            mlt.setStreamHead(streamHead);
        }

        // Set stream fields
        String[] streamHeadFields = getFieldList(MoreLikeThisParams.STREAM_HEAD_FL, params);
        if(streamHeadFields != null){
            mlt.setStreamHeadfieldNames(streamHeadFields);
        }

        String[] streamBodyFields = getFieldList(MoreLikeThisParams.STREAM_BODY_FL, params);
        if(streamBodyFields != null){
            mlt.setStreamBodyfieldNames(streamBodyFields);
        }
    }

    private String[] getFieldList(String key, SolrParams params) {
        final String fieldList = params.get(key);
        if(fieldList != null && fieldList.trim().length() > 0) {
            String[] fields = splitList.split(fieldList);
            if(fields != null){
                return fields;
            }
        }
        return null;
    }

    private Query rawMLTQuery;
    private Query boostedMLTQuery;
    private BooleanQuery realMLTQuery;

    public Query getRawMLTQuery(){
        return rawMLTQuery;
    }

    public Query getBoostedMLTQuery(){
        return boostedMLTQuery;
    }

    public Query getRealMLTQuery(){
        return realMLTQuery;
    }

    private Query getBoostedFunctionQuery(Query q) throws SyntaxError{

        if (mlt.getBoostFn() == null || mlt.getBoostFn().trim().length() == 0) {
            return q;
        }

        Query boost = this.qParser.subQuery(mlt.getBoostFn(), FunctionQParserPlugin.NAME).getQuery();
        ValueSource vs;
        if (boost instanceof FunctionQuery) {
            vs = ((FunctionQuery) boost).getValueSource();
        } else {
            vs = new QueryValueSource(boost, 1.0f);
        }
        return new BoostedQuery(q, vs);
    }

    public MLTResult getMoreLikeTheseFromDocs(DocIterator iterator, int start, int rows, List<Query> filters, int flags, Sort lsort) throws IOException, SyntaxError
    {
        realMLTQuery = new BooleanQuery();
        List<Integer> ids = new ArrayList<Integer>();

        while(iterator.hasNext()) {
            int id = iterator.nextDoc();
            Document doc = reader.document(id);
            ids.add(id);

            // add exclusion filters to prevent matching seed documents
            TermQuery tq = new TermQuery(new Term(uniqueKeyField.getName(), uniqueKeyField.getType().storedToIndexed(doc.getField(uniqueKeyField.getName()))));
            realMLTQuery.add(tq, BooleanClause.Occur.MUST_NOT);
        }

        MLTResult mltResult = mlt.like(ids);
        rawMLTQuery = mltResult.rawMLTQuery;
        if(mltResult.getMustMatchQuery() != null){
            filters.add(mltResult.getMustMatchQuery());
        }
        if(mltResult.getMustNOTMatchQuery() != null){
            filters.add(mltResult.getMustNOTMatchQuery());
        }

        boostedMLTQuery = getBoostedFunctionQuery(rawMLTQuery);
        realMLTQuery.add(boostedMLTQuery, BooleanClause.Occur.MUST);

        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results = searcher.getDocListAndSet(realMLTQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList(realMLTQuery, filters, lsort, start, rows, flags);
        }
        mltResult.setDoclist(results);
        return mltResult;
    }

    public MLTResult getMoreLikeThisFromContentSteam(Reader reader, int start, int rows, List<Query> filters, int flags, Sort lsort) throws IOException, SyntaxError
    {
        MLTResult mltResult = mlt.like(reader);
        rawMLTQuery = mltResult.rawMLTQuery;

        boostedMLTQuery = getBoostedFunctionQuery( rawMLTQuery );
        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results =   searcher.getDocListAndSet( boostedMLTQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList( boostedMLTQuery, filters, lsort, start, rows, flags);
        }
        mltResult.setDoclist(results);
        return mltResult;
    }

    public MoreLikeThis getMoreLikeThis()
    {
        return mlt;
    }
}


