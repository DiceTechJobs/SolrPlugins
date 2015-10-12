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

package org.dice.solrenhancements.unsupervisedfeedback;

import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
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
        int maxDocumentsToMatch = params.getInt(UnsupervisedFeedbackParams.MAX_DOCUMENTS_TO_PROCESS, UnsupervisedFeedback.DEFAULT_MAX_NUM_DOCUMENTS_TO_PROCESS);
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

        UnsupervisedFeedbackHelper mlt = new UnsupervisedFeedbackHelper( params, searcher, uniqueKeyField, parser );

        // Hold on to the interesting terms if relevant
        UnsupervisedFeedbackParams.TermStyle termStyle = UnsupervisedFeedbackParams.TermStyle.get(params.get(UnsupervisedFeedbackParams.INTERESTING_TERMS));
        List<InterestingTerm> interesting =
                (termStyle == UnsupervisedFeedbackParams.TermStyle.NONE )?
                        null : new ArrayList<InterestingTerm>( mlt.uf.getMaxQueryTermsPerField() );

        DocListAndSet uffDocs = null;

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

                uffDocs = expandQueryAndReExecute(rsp, params, maxDocumentsToMatch, flags, q, query, sortSpec,
                        targetFqFilters, mltFqFilters, searcher, mlt, interesting, uffDocs, start, rows);
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        if( uffDocs == null ) {
            uffDocs = new DocListAndSet(); // avoid NPE
        }
        rsp.add( "response", uffDocs.docList );

        if( interesting != null ) {
            addInterestingTerms(rsp, termStyle, interesting);
        }

        // maybe facet the results
        if (params.getBool(FacetParams.FACET,false)) {
            addFacet(req, rsp, params, uffDocs);
        }

        addDebugInfo(req, rsp, q, mltFqFilters, mlt, uffDocs);
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

    private DocListAndSet expandQueryAndReExecute(SolrQueryResponse rsp, SolrParams params, int maxDocumentsToMatch, int flags, String q, Query seedQuery, SortSpec sortSpec, List<Query> targetFqFilters, List<Query> mltFqFilters, SolrIndexSearcher searcher, UnsupervisedFeedbackHelper uff, List<InterestingTerm> interesting, DocListAndSet mltDocs, int start, int rows) throws IOException, SyntaxError {

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
        if (iterator.hasNext()) {
            // do a MoreLikeThis query for each document in results
            mltDocs = uff.expandQueryAndReExecute(iterator, seedQuery, start, rows, mltFqFilters, interesting, flags, sortSpec.getSort());
        }
        return mltDocs;
    }

    private void addInterestingTerms(SolrQueryResponse rsp, UnsupervisedFeedbackParams.TermStyle termStyle, List<InterestingTerm> interesting) {
        if( termStyle == UnsupervisedFeedbackParams.TermStyle.DETAILS ) {
            NamedList<Float> it = new NamedList<Float>();
            for( InterestingTerm t : interesting ) {
                it.add( t.term.toString(), t.boost );
            }
            rsp.add( "interestingTerms", it );
        }
        else {
            List<String> it = new ArrayList<String>( interesting.size() );
            for( InterestingTerm t : interesting ) {
                it.add( t.term.text());
            }
            rsp.add( "interestingTerms", it );
        }
    }

    private void addFacet(SolrQueryRequest req, SolrQueryResponse rsp, SolrParams params, DocListAndSet mltDocs) {
        if( mltDocs.docSet == null ) {
            rsp.add( "facet_counts", null );
        }
        else {
            SimpleFacets f = new SimpleFacets(req, mltDocs.docSet, params );
            rsp.add( "facet_counts", f.getFacetCounts() );
        }
    }

    private void addDebugInfo(SolrQueryRequest req, SolrQueryResponse rsp, String q, List<Query> mltFqFilters, UnsupervisedFeedbackHelper mlt, DocListAndSet mltDocs) {
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
                NamedList<Object> dbgInfo = SolrPluginUtils.doStandardDebug(req, q, mlt.getRawUFQuery(), mltDocs.docList, dbgQuery, dbgResults);
                if (null != dbgInfo) {
                    if (null != mltFqFilters) {
                        dbgInfo.add("filter_queries",req.getParams().getParams(CommonParams.FQ));
                        List<String> fqs = new ArrayList<String>(mltFqFilters.size());
                        for (Query fq : mltFqFilters) {
                            fqs.add(QueryParsing.toString(fq, req.getSchema()));
                        }
                        dbgInfo.add("mlt_filter_queries",fqs);
                    }
                    rsp.add("debug", dbgInfo);
                }
            } catch (Exception e) {
                SolrException.log(SolrCore.log, "Exception during debug", e);
                rsp.add("exception_during_debug", SolrException.toStr(e));
            }
        }
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
}