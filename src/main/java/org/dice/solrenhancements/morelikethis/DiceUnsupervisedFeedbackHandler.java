/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dice.solrenhancements.morelikethis;

import com.google.common.base.Strings;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.FacetComponent;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;
import org.dice.solrenhancements.JarVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Solr MoreLikeThis --
 *
 * Return similar documents either based on a single document or based on posted text.
 *
 * @since solr 1.3
 */
public class DiceUnsupervisedFeedbackHandler extends RequestHandlerBase
{
    private final static String EDISMAX = ExtendedDismaxQParserPlugin.NAME;

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DEFAULT_MAX_NUM_DOCUMENTS_TO_PROCESS = 5;

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception
    {
        SolrIndexSearcher searcher = req.getSearcher();
        SchemaField uniqueKeyField = searcher.getSchema().getUniqueKeyField();
        ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
        configureSolrParameters(req, params, uniqueKeyField.getName());

        // Set field flags
        ReturnFields returnFields = new SolrReturnFields( req );
        rsp.setReturnFields( returnFields );
        int flags = 0;
        if (returnFields.wantsScore()) {
            flags |= SolrIndexSearcher.GET_SCORES;
        }

        String defType = params.get(QueryParsing.DEFTYPE, QParserPlugin.DEFAULT_QTYPE);
        int maxDocumentsToMatch = params.getInt(UnsupervisedFeedbackParams.MAX_DOCUMENTS_TO_PROCESS, DEFAULT_MAX_NUM_DOCUMENTS_TO_PROCESS);
        String q = params.get( CommonParams.Q );
        Query query = null;
        SortSpec sortSpec = null;
        QParser parser = null;

        List<Query> targetFqFilters = null;
        List<Query> mltFqFilters    = null;

        try {

            parser = QParser.getParser(q, defType, req);
            query = parser.getQuery();
            sortSpec = parser.getSort(true);

            targetFqFilters = getFilters(req, CommonParams.FQ);
            mltFqFilters    = getFilters(req, UnsupervisedFeedbackParams.FQ);
        } catch (SyntaxError e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }

        UnsupervisedFeedbackHelper usfdbkHelper = new UnsupervisedFeedbackHelper( params, searcher, uniqueKeyField, parser );

        // Hold on to the interesting terms if relevant
        UnsupervisedFeedbackParams.TermStyle termStyle = UnsupervisedFeedbackParams.TermStyle.get(params.get(UnsupervisedFeedbackParams.INTERESTING_TERMS));
        List<InterestingTerm> interesting =
                (termStyle == UnsupervisedFeedbackParams.TermStyle.NONE )?
                        null : new ArrayList<InterestingTerm>( usfdbkHelper.moreLikeThis.getMaxQueryTermsPerField() );

        MLTResult usfdbkResult = null;

        // Parse Required Params
        // This will either have a single Reader or valid query
        Reader reader = null;
        try {
            int start = params.getInt(CommonParams.START, 0);
            int rows  = params.getInt(CommonParams.ROWS, 10);

            // Find documents MoreLikeThis - either with a reader or a query
            // --------------------------------------------------------------------------------
            if (q == null) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        "Dice unsupervised feedback handler requires either a query (?q=) to find similar documents.");

            } else {

                usfdbkResult = expandQueryAndReExecute(rsp, params, maxDocumentsToMatch, flags, q, query, sortSpec,
                        targetFqFilters, mltFqFilters, searcher, usfdbkHelper, start, rows);
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        DocListAndSet results = new DocListAndSet();
        if( usfdbkResult != null ) {
            results = usfdbkResult.getResults();
        }
        rsp.add( "response", results );

        if( usfdbkResult!= null && termStyle != UnsupervisedFeedbackParams.TermStyle.NONE) {
            addInterestingTerms(rsp, termStyle, usfdbkResult);
        }

        // maybe facet the results
        if (params.getBool(FacetParams.FACET,false)) {
            addFacet(req, rsp, params, results);
        }

        addDebugInfo(req, rsp, q, mltFqFilters, usfdbkResult);
    }

    private void configureSolrParameters(SolrQueryRequest req, ModifiableSolrParams params, String uniqueKeyField){

        // default to the the edismax parser
        String defType = params.get(QueryParsing.DEFTYPE, EDISMAX);
        // allow useage of custom edismax implementations, such as our own
        if(defType.toLowerCase().contains(EDISMAX.toLowerCase())){
            params.set(DisMaxParams.MM, 0);
            // edismax blows up without df field, even if you specify the field to match on in the query
            params.set(CommonParams.DF, uniqueKeyField);
        }
        params.set(QueryParsing.DEFTYPE, defType);
        req.setParams(params);
    }

    private void addInterestingTerms(SolrQueryResponse rsp, UnsupervisedFeedbackParams.TermStyle termStyle, MLTResult mltResult) {

        List<MLTTerm> mltTerms = mltResult.getMltTerms();
        Collections.sort(mltTerms, MLTTerm.FLD_BOOST_X_SCORE_ORDER);

        if( termStyle == UnsupervisedFeedbackParams.TermStyle.DETAILS ) {
            List<InterestingTerm> interesting = extractInterestingTerms(mltResult.getMltTerms());

            int longest = 0;
            for( InterestingTerm t : interesting ) {
                longest = Math.max(t.term.toString().length(), longest);
            }

            NamedList<Float> it = new NamedList<Float>();
            for( InterestingTerm t : interesting ) {
                it.add( Strings.padEnd(t.term.toString(), longest, ' '), t.boost );
            }
            rsp.add( "interestingTerms", it );
        }
        else {
            List<String> it = new ArrayList<String>( mltTerms.size() );
            for( MLTTerm mltTerm : mltTerms) {
                it.add(mltTerm.getWord());
            }
            rsp.add( "interestingTerms", it );
        }
    }

    private List<InterestingTerm> extractInterestingTerms(List<MLTTerm> mltTerms){
        List<InterestingTerm> terms = new ArrayList<InterestingTerm>();
        for( MLTTerm term : mltTerms) {
            InterestingTerm it = new InterestingTerm();
            it.term = term.getTerm();
            it.boost = term.getFinalScore();
            terms.add(it);
        }
        Collections.sort(terms, InterestingTerm.BOOST_ORDER);
        return terms;
    }

    private MLTResult expandQueryAndReExecute(SolrQueryResponse rsp, SolrParams params, int maxDocumentsToMatch, int flags, String q, Query seedQuery, SortSpec sortSpec, List<Query> targetFqFilters, List<Query> mltFqFilters, SolrIndexSearcher searcher, UnsupervisedFeedbackHelper uff, int start, int rows) throws IOException, SyntaxError {

        boolean includeMatch = params.getBool(UnsupervisedFeedbackParams.MATCH_INCLUDE, true);
        int matchOffset = params.getInt(UnsupervisedFeedbackParams.MATCH_OFFSET, 0);
        // Find the base match
        DocList match = searcher.getDocList(seedQuery, targetFqFilters, null, matchOffset, maxDocumentsToMatch, flags); // only get the first one...
        if(match.matches() == 0){
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    String.format("Unsupervised feedback handler was unable to find any documents matching the seed query: '%s'.", q));
        }

        if (includeMatch) {
            rsp.add("match", match);
        }

        // This is an iterator, but we only handle the first match
        DocIterator iterator = match.iterator();
        MLTResult mltResult = null;
        if (iterator.hasNext()) {
            // do a MoreLikeThis query for each document in results
            mltResult = uff.expandQueryAndReExecute(iterator, seedQuery, start, rows, mltFqFilters, flags, sortSpec.getSort());
        }
        return mltResult;
    }

    private void addFacet(SolrQueryRequest req, SolrQueryResponse rsp, SolrParams params, DocListAndSet mltDocs) {
        if( mltDocs.docSet == null ) {
            rsp.add( "facet_counts", null );
        }
        else {
            FacetComponent fct = new FacetComponent();
            rsp.add( "facet_counts", fct.getFacetCounts(new SimpleFacets(req, mltDocs.docSet, params )) );
        }
    }

    private void addDebugInfo(SolrQueryRequest req, SolrQueryResponse rsp, String q, List<Query> mltFqFilters, MLTResult mltResult) {
        DocListAndSet mltDocs = mltResult.getResults();

        boolean dbg = req.getParams().getBool(CommonParams.DEBUG_QUERY, false);
        boolean dbgQuery = false, dbgResults = false;
        if (dbg == false){//if it's true, we are doing everything anyway.
            String[] dbgParams = req.getParams().getParams(CommonParams.DEBUG);
            if (dbgParams != null) {
                for (int i = 0; i < dbgParams.length; i++) {
                    if (dbgParams[i].equals(CommonParams.QUERY)){
                        dbgQuery = true;
                    } else if (dbgParams[i].equals(CommonParams.RESULTS)){
                        dbgResults = true;
                    }
                }
            }
        } else {
            dbgQuery = true;
            dbgResults = true;
        }
        // Copied from StandardRequestHandler... perhaps it should be added to doStandardDebug?
        if (dbg == true) {
            try {
                NamedList<String> it = getMltTermsForDebug(mltResult.getMltTerms());

                NamedList<Object> dbgInfo = new NamedList<Object>();
                NamedList<Object> stdDbg = SolrPluginUtils.doStandardDebug(req, q, mltResult.getQuery(), mltDocs.docList, dbgQuery, dbgResults);
                if (null != dbgInfo) {
                    rsp.add("debug", dbgInfo);
                    dbgInfo.add( "mltTerms", it );
                    dbgInfo.addAll(stdDbg);

                    if (null != mltFqFilters) {
                        dbgInfo.add("filter_queries",req.getParams().getParams(CommonParams.FQ));
                        List<String> fqs = new ArrayList<String>(mltFqFilters.size());
                        for (Query fq : mltFqFilters) {
                            fqs.add(QueryParsing.toString(fq, req.getSchema()));
                        }
                        dbgInfo.add("mlt_filter_queries",fqs);
                    }
                }
            } catch (Exception e) {
                SolrException.log(log, "Exception during debug", e);
                rsp.add("exception_during_debug", SolrException.toStr(e));
            }
        }
    }

    private NamedList<String> getMltTermsForDebug(List<MLTTerm> mltTerms) {
        Collections.sort(mltTerms);
        NamedList<String> it = new NamedList<String>();
        int longestWd = 0;
        int longestFieldName = 0;
        for( MLTTerm mltTerm : mltTerms) {
            longestWd = Math.max(mltTerm.getWord().length(), longestWd);
            longestFieldName = Math.max(mltTerm.getFieldName().length(), longestFieldName);
        }
        for( MLTTerm mltTerm : mltTerms) {
            String paddedfieldName = Strings.padEnd(mltTerm.getFieldName(), longestFieldName, ' ');
            String paddedWd = Strings.padEnd(mltTerm.getWord(), longestWd, ' ');
            it.add(paddedfieldName, paddedWd + " - " + mltTerm.valuesToString() );
        }
        return it;
    }

    private List<Query> getFilters(SolrQueryRequest req, String param) throws SyntaxError {
        String[] fqs = req.getParams().getParams(param);
        if (fqs!=null && fqs.length!=0) {
            List<Query> filters = new ArrayList<Query>();
            for (String fq : fqs) {
                if (fq != null && fq.trim().length()!=0) {
                    QParser fqp = QParser.getParser(fq, null, req);
                    filters.add(fqp.getQuery());
                }
            }
            return filters;
        }
        return null;
    }

    //////////////////////// SolrInfoMBeans methods //////////////////////

    @Override
    public String getDescription() {
        return "Solr MoreLikeThis";
    }

    @Override
    public String getSource() {
        return "$URL$";
    }

    @Override
    public URL[] getDocs() {
        try {
            return new URL[] { new URL("http://wiki.apache.org/solr/MoreLikeThis") };
        }
        catch( MalformedURLException ex ) { return null; }
    }

    private String version = null;

    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        }

        version = JarVersion.getVersion(log);
        return version;
    }
}