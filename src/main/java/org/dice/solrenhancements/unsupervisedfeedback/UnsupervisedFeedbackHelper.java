package org.dice.solrenhancements.unsupervisedfeedback;

/**
 * Created by simon.hughes on 9/2/14.
 */

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.*;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
    final UnsupervisedFeedback uf;
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

        this.uf = new UnsupervisedFeedback( reader ); // TODO -- after LUCENE-896, we can use , searcher.getSimilarity() );
        uf.setFieldNames(fields);

        final String sPayloadFieldList = params.get(UnsupervisedFeedbackParams.PAYLOAD_FIELDS);
        if(sPayloadFieldList != null && sPayloadFieldList.trim().length() > 0) {
            String[] payloadFields = splitList.split(sPayloadFieldList);
            uf.setPayloadFields(payloadFields);
        }
        uf.setAnalyzer(searcher.getSchema().getIndexAnalyzer());

        // configurable params

        uf.setMinTermFreq(params.getInt(UnsupervisedFeedbackParams.MIN_TERM_FREQ, UnsupervisedFeedback.DEFAULT_MIN_TERM_FREQ));
        uf.setMinDocFreq(params.getInt(UnsupervisedFeedbackParams.MIN_DOC_FREQ, UnsupervisedFeedback.DEFAULT_MIN_DOC_FREQ));
        uf.setMaxDocFreq(params.getInt(UnsupervisedFeedbackParams.MAX_DOC_FREQ, UnsupervisedFeedback.DEFAULT_MAX_DOC_FREQ));
        uf.setMinWordLen(params.getInt(UnsupervisedFeedbackParams.MIN_WORD_LEN, UnsupervisedFeedback.DEFAULT_MIN_WORD_LENGTH));
        uf.setMaxWordLen(params.getInt(UnsupervisedFeedbackParams.MAX_WORD_LEN, UnsupervisedFeedback.DEFAULT_MAX_WORD_LENGTH));

        // new parameters
        uf.setBoostFn(params.get(UnsupervisedFeedbackParams.BOOST_FN));
        uf.setNormalizeFieldBoosts(params.getBool(UnsupervisedFeedbackParams.NORMALIZE_FIELD_BOOSTS, UnsupervisedFeedback.DEFAULT_NORMALIZE_FIELD_BOOSTS));
        // new versions of previous parameters moved to the field level
        uf.setMaxQueryTermsPerField(params.getInt(UnsupervisedFeedbackParams.MAX_QUERY_TERMS_PER_FIELD, UnsupervisedFeedback.DEFAULT_MAX_QUERY_TERMS_PER_FIELD));
        uf.setMaxNumTokensParsedPerField(params.getInt(UnsupervisedFeedbackParams.MAX_NUM_TOKENS_PARSED_PER_FIELD, UnsupervisedFeedback.DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD));
        uf.setLogTf(params.getBool(UnsupervisedFeedbackParams.IS_LOG_TF, UnsupervisedFeedback.DEFAULT_IS_LOG_TF));

        uf.setBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(UnsupervisedFeedbackParams.QF)));
    }

    private BooleanQuery rawUFQuery;
    private Query boostedUfQuery;
    private BooleanQuery realUFQuery;

    public Query getRawUFQuery(){
        return rawUFQuery;
    }

    private Query getBoostedFunctionQuery(Query q) throws SyntaxError{

        if (uf.getBoostFn() == null || uf.getBoostFn().trim().length() == 0) {
            return q;
        }

        Query boost = this.qParser.subQuery(uf.getBoostFn(), FunctionQParserPlugin.NAME).getQuery();
        ValueSource vs;
        if (boost instanceof FunctionQuery) {
            vs = ((FunctionQuery) boost).getValueSource();
        } else {
            vs = new QueryValueSource(boost, 1.0f);
        }
        return new BoostedQuery(q, vs);
    }

    public DocListAndSet expandQueryAndReExecute(DocIterator iterator, Query seedQuery, int start, int rows, List<Query> filters, List<InterestingTerm> terms, int flags, Sort lsort) throws IOException, SyntaxError
    {
        rawUFQuery = new BooleanQuery();
        rawUFQuery.add(seedQuery, BooleanClause.Occur.MUST);

        List<Integer> ids = new ArrayList<Integer>();
        while(iterator.hasNext()) {
            ids.add(iterator.nextDoc());
        }
        // expand original query from matched documents
        BooleanQuery expansionQuery = uf.queryFromDocuments(ids);
        rawUFQuery.add(expansionQuery, BooleanClause.Occur.SHOULD);

        // only boost final query, not seed query (don't want to filter expansion query)
        boostedUfQuery = getBoostedFunctionQuery(rawUFQuery);
        if( terms != null ) {
            fillInterestingTermsFromUfQuery(expansionQuery, terms);
        }
        realUFQuery = new BooleanQuery();
        // exclude current document from results
        realUFQuery.add(boostedUfQuery, BooleanClause.Occur.MUST);

        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results = searcher.getDocListAndSet(realUFQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList(realUFQuery, filters, lsort, start, rows, flags);
        }
        return results;
    }

    private void fillInterestingTermsFromUfQuery(Query query, List<InterestingTerm> terms)
    {
        List clauses = ((BooleanQuery)query).clauses();
        for( Object o : clauses ) {
            Query qry = ((BooleanClause)o).getQuery();
            InterestingTerm it = new InterestingTerm();
            if(qry instanceof TermQuery) {
                TermQuery tq = (TermQuery)qry;
                it.term = tq.getTerm();
            }
            else if(qry instanceof PayloadTermQuery) {
                PayloadTermQuery ptq = (PayloadTermQuery)qry;
                it.term = ptq.getTerm();
            }
            it.boost = qry.getBoost();
            terms.add(it);
        }
        // alternatively we could use
        // mltquery.extractTerms( terms );
        Collections.sort(terms, InterestingTerm.BOOST_ORDER);
    }

    public UnsupervisedFeedback getUnsupervisedFeedback()
    {
        return uf;
    }
}


