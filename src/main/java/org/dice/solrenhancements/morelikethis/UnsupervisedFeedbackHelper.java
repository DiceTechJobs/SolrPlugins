package org.dice.solrenhancements.morelikethis;

/**
 * Created by simon.hughes on 9/2/14.
 */

import org.apache.lucene.index.IndexReader;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for MoreLikeThis that can be called from other request handlers
 */
public class UnsupervisedFeedbackHelper
{
    // Pattern is thread safe -- TODO? share this with general 'fl' param
    private static final Pattern splitList = Pattern.compile(",| ");

    final SolrIndexSearcher searcher;
    final QParser qParser;
    final MoreLikeThis moreLikeThis;
    final IndexReader reader;
    final SchemaField uniqueKeyField;
    final boolean needDocSet;

    public UnsupervisedFeedbackHelper(SolrParams params, SolrIndexSearcher searcher, SchemaField uniqueKeyField, QParser qParser)
    {
        this.searcher = searcher;
        this.qParser = qParser;
        this.reader = searcher.getIndexReader();
        this.uniqueKeyField = uniqueKeyField;
        this.needDocSet = params.getBool(FacetParams.FACET,false);

        SolrParams required = params.required();
        String[] fields = splitList.split( required.get(UnsupervisedFeedbackParams.SIMILARITY_FIELDS) );
        if( fields.length < 1 ) {
            throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,
                    "MoreLikeThis requires at least one similarity field: "+ UnsupervisedFeedbackParams.SIMILARITY_FIELDS );
        }

        //this.moreLikeThis = new MoreLikeThis()
        this.moreLikeThis = new MoreLikeThis( reader ); // TODO -- after LUCENE-896, we can use , searcher.getSimilarity() );
        moreLikeThis.setFieldNames(fields);

        final String sPayloadFieldList = params.get(UnsupervisedFeedbackParams.PAYLOAD_FIELDS);
        if(sPayloadFieldList != null && sPayloadFieldList.trim().length() > 0) {
            String[] payloadFields = splitList.split(sPayloadFieldList);
            moreLikeThis.setPayloadFields(payloadFields);
        }
        moreLikeThis.setAnalyzer(searcher.getSchema().getIndexAnalyzer());

        // configurable params

        moreLikeThis.setMinTermFreq(params.getInt(UnsupervisedFeedbackParams.MIN_TERM_FREQ, MoreLikeThis.DEFAULT_MIN_TERM_FREQ));
        moreLikeThis.setMinDocFreq(params.getInt(UnsupervisedFeedbackParams.MIN_DOC_FREQ, MoreLikeThis.DEFAULT_MIN_DOC_FREQ));
        moreLikeThis.setMaxDocFreq(params.getInt(UnsupervisedFeedbackParams.MAX_DOC_FREQ, MoreLikeThis.DEFAULT_MAX_DOC_FREQ));
        moreLikeThis.setMinWordLen(params.getInt(UnsupervisedFeedbackParams.MIN_WORD_LEN, MoreLikeThis.DEFAULT_MIN_WORD_LENGTH));
        moreLikeThis.setMaxWordLen(params.getInt(UnsupervisedFeedbackParams.MAX_WORD_LEN, MoreLikeThis.DEFAULT_MAX_WORD_LENGTH));

        // new parameters
        moreLikeThis.setBoostFn(params.get(UnsupervisedFeedbackParams.BOOST_FN));
        moreLikeThis.setNormalizeFieldBoosts(params.getBool(UnsupervisedFeedbackParams.NORMALIZE_FIELD_BOOSTS, MoreLikeThis.DEFAULT_NORMALIZE_FIELD_BOOSTS));
        // new versions of previous parameters moved to the field level
        moreLikeThis.setMaxQueryTermsPerField(params.getInt(UnsupervisedFeedbackParams.MAX_QUERY_TERMS_PER_FIELD, MoreLikeThis.DEFAULT_MAX_QUERY_TERMS_PER_FIELD));
        moreLikeThis.setMaxNumTokensParsedPerField(params.getInt(UnsupervisedFeedbackParams.MAX_NUM_TOKENS_PARSED_PER_FIELD, MoreLikeThis.DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD));
        moreLikeThis.setLogTf(params.getBool(UnsupervisedFeedbackParams.IS_LOG_TF, MoreLikeThis.DEFAULT_IS_LOG_TF));

        moreLikeThis.setBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(UnsupervisedFeedbackParams.QF)));
    }

    private Query getBoostedFunctionQuery(Query q) throws SyntaxError{

        if (moreLikeThis.getBoostFn() == null || moreLikeThis.getBoostFn().trim().length() == 0) {
            return q;
        }

        Query boost = this.qParser.subQuery(moreLikeThis.getBoostFn(), FunctionQParserPlugin.NAME).getQuery();
        ValueSource vs;
        if (boost instanceof FunctionQuery) {
            vs = ((FunctionQuery) boost).getValueSource();
        } else {
            vs = new QueryValueSource(boost, 1.0f);
        }
        return new BoostedQuery(q, vs);
    }

    public MLTResult expandQueryAndReExecute(DocIterator iterator, Query seedQuery, int start, int rows, List<Query> filters, int flags, Sort lsort) throws IOException, SyntaxError
    {
        List<Integer> ids = new ArrayList<Integer>();
        while(iterator.hasNext()) {
            ids.add(iterator.nextDoc());
        }

        // start to build final query
        // add a must clause on the original query, meaning we need it to be matched (likely one a single term or more)
        BooleanQuery.Builder rawUFQuery = new BooleanQuery.Builder();
        rawUFQuery.add(seedQuery, BooleanClause.Occur.MUST);

        // expand original query from matched documents, and add as a should query for re-ranking purposes

        MLTQuery mltQuery = moreLikeThis.like(ids);
        Query expansionQuery  = mltQuery.getOrQuery();

        rawUFQuery.add(expansionQuery, BooleanClause.Occur.SHOULD);

        // only boost final query, not seed query (don't want to filter expansion query)
        Query finalUfQuery = getBoostedFunctionQuery(rawUFQuery.build());

        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results = searcher.getDocListAndSet(finalUfQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList(finalUfQuery, filters, lsort, start, rows, flags);
        }

        return new MLTResult(mltQuery.getMltTerms(), finalUfQuery, results);
    }
}


