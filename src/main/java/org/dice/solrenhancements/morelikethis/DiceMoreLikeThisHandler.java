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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.ContentStream;
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
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Solr MoreLikeThis --
 *
 * Return similar documents either based on a single document or based on posted text.
 *
 * @since solr 1.3
 */
public class DiceMoreLikeThisHandler extends RequestHandlerBase
{
    private final static String EDISMAX = ExtendedDismaxQParserPlugin.NAME;
    private String version = null;

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception
    {
        // set and override parameters
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
        // note: set in configureSolrParameters
        String defType = params.get(QueryParsing.DEFTYPE, EDISMAX);
        String q = params.get( CommonParams.Q );
        Query query = null;
        SortSpec sortSpec = null;
        QParser parser = null;

        List<Query> targetFqFilters = null;
        List<Query> mltFqFilters    = null;

        try {
            if (q != null) {
                parser = QParser.getParser(q, defType, req);
                query = parser.getQuery();
                sortSpec = parser.getSort(true);
            }
            else{
                parser = QParser.getParser(null, defType, req);
                sortSpec = parser.getSort(true);
            }

            targetFqFilters = getFilters(req, CommonParams.FQ);
            mltFqFilters    = getFilters(req, MoreLikeThisParams.FQ);
        } catch (SyntaxError e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }

        MoreLikeThisHelper mlt = new MoreLikeThisHelper( params, searcher, uniqueKeyField, parser );

        // Hold on to the interesting terms if relevant
        MoreLikeThisParams.TermStyle termStyle = MoreLikeThisParams.TermStyle.get(params.get(MoreLikeThisParams.INTERESTING_TERMS));

        MLTResult mltResult = null;
        DocListAndSet mltDocs = null;

        // Parse Required Params
        // This will either have a single Reader or valid query
        Reader reader = null;
        try {
            int start = params.getInt(CommonParams.START, 0);
            int rows  = params.getInt(CommonParams.ROWS, 10);

            // for use when passed a content stream
            if (q == null || q.trim().length() < 1) {
                reader = getContentStreamReader(req, reader);
            }
            // Find documents MoreLikeThis - either with a reader or a query
            // --------------------------------------------------------------------------------
            if (reader != null) {
                // this will only be initialized if used with a content stream (see above)
                mltResult = mlt.getMoreLikeThisFromContentSteam(reader, start, rows, mltFqFilters, flags, sortSpec.getSort());
            } else if (q != null) {
                // Matching options
                mltResult = getMoreLikeTheseFromQuery(rsp, params, flags, q, query, sortSpec,
                        targetFqFilters, mltFqFilters, searcher, mlt,  start, rows);
            } else {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        "MoreLikeThis requires either a query (?q=) or text to find similar documents.");
            }
            if(mltResult != null)
            {
                mltDocs = mltResult.getDoclist();
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        if( mltDocs == null ) {
            mltDocs = new DocListAndSet(); // avoid NPE
        }
        rsp.add( "response", mltDocs.docList );

        if( mltResult != null && termStyle != MoreLikeThisParams.TermStyle.NONE) {
            addInterestingTerms(rsp, termStyle, mltResult);
        }

        // maybe facet the results
        if (params.getBool(FacetParams.FACET,false)) {
            addFacet(req, rsp, params, mltDocs);
        }

        addDebugInfo(req, rsp, q, mltFqFilters, mlt, mltResult);
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

    private Reader getContentStreamReader(SolrQueryRequest req, Reader reader) throws IOException {
        Iterable<ContentStream> streams = req.getContentStreams();
        if (streams != null) {
            Iterator<ContentStream> iter = streams.iterator();
            if (iter.hasNext()) {
                reader = iter.next().getReader();
            }
            if (iter.hasNext()) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        "MoreLikeThis does not support multiple ContentStreams");
            }
        }
        return reader;
    }

    private MLTResult getMoreLikeTheseFromQuery(SolrQueryResponse rsp, SolrParams params, int flags, String q, Query query, SortSpec sortSpec, List<Query> targetFqFilters, List<Query> mltFqFilters, SolrIndexSearcher searcher, MoreLikeThisHelper mlt, int start, int rows) throws IOException, SyntaxError {

        boolean includeMatch = params.getBool(MoreLikeThisParams.MATCH_INCLUDE, true);
        int matchOffset = params.getInt(MoreLikeThisParams.MATCH_OFFSET, 0);
        // Find the base match
        DocList match = searcher.getDocList(query, targetFqFilters, null, matchOffset, 10000, flags); // only get the first one...
        if(match.matches() == 0){
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    String.format("MoreLikeThis was unable to find any documents matching the query: '%s'.", q));
        }

        if (includeMatch) {
            rsp.add("match", match);
        }

        // This is an iterator, but we only handle the first match
        DocIterator iterator = match.iterator();
        if (iterator.hasNext()) {
            // do a MoreLikeThis query for each document in results
            return mlt.getMoreLikeTheseFromDocs(iterator, start, rows, mltFqFilters, flags, sortSpec.getSort());
        }
        return null;
    }

    private List<InterestingTerm> extractInterestingTerms(Query query){
        List<InterestingTerm> terms = new ArrayList<InterestingTerm>();
        List clauses = ((BooleanQuery)query).clauses();
        for( Object o : clauses ) {
            Query q = ((BooleanClause)o).getQuery();
            InterestingTerm it = new InterestingTerm();
            it.boost = q.getBoost();
            if(q instanceof TermQuery) {
                TermQuery tq = (TermQuery)q;
                it.term = tq.getTerm();
            }
            else if(q instanceof PayloadTermQuery){
                PayloadTermQuery ptq = (PayloadTermQuery)q;
                it.term = ptq.getTerm();
            }
            terms.add(it);
        }
        Collections.sort(terms, InterestingTerm.BOOST_ORDER);

        return terms;
    }

    private void addInterestingTerms(SolrQueryResponse rsp, MoreLikeThisParams.TermStyle termStyle, MLTResult mltResult) {

        List<MLTTerm> mltTerms = mltResult.mltTerms;
        Collections.sort(mltTerms, MLTTerm.FLD_BOOST_X_SCORE_ORDER);

        if( termStyle == MoreLikeThisParams.TermStyle.DETAILS ) {
            List<InterestingTerm> interesting = extractInterestingTerms(mltResult.rawMLTQuery);

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

    private void addFacet(SolrQueryRequest req, SolrQueryResponse rsp, SolrParams params, DocListAndSet mltDocs) {
        if( mltDocs.docSet == null ) {
            rsp.add( "facet_counts", null );
        }
        else {
            SimpleFacets f = new SimpleFacets(req, mltDocs.docSet, params );
            rsp.add( "facet_counts", f.getFacetCounts() );
        }
    }

    private void addDebugInfo(SolrQueryRequest req, SolrQueryResponse rsp, String q, List<Query> mltFqFilters, MoreLikeThisHelper mlt, MLTResult mltResult) {
        DocListAndSet mltDocs = mltResult.getDoclist();

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

                NamedList<String> it = getMltTermsForDebug(mltResult);

                NamedList<Object> dbgInfo = new NamedList<Object>();
                NamedList<Object> stdDbg = SolrPluginUtils.doStandardDebug(req, q, mlt.getRealMLTQuery(), mltDocs.docList, dbgQuery, dbgResults);
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
                SolrException.log(SolrCore.log, "Exception during debug", e);
                rsp.add("exception_during_debug", SolrException.toStr(e));
            }
        }
    }

    private NamedList<String> getMltTermsForDebug(MLTResult mltResult) {
        List<MLTTerm> mltTerms = mltResult.mltTerms;
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
        return new ArrayList<Query>();
    }

    //////////////////////// SolrInfoMBeans methods //////////////////////

    @Override
    public String getDescription() {
        return "Dice custom MoreLikeThis handler";
    }

    @Override
    public String getSource() {
        return "$URL$";
    }

    @Override
    public String getVersion(){

        if (version != null) return version;
        Enumeration<URL> resources;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                /* let's not read other jar's manifests */
                if (!url.toString().contains("DiceSolrEnhancements-1.0.jar")) continue;
                InputStream reader = url.openStream();
                while(reader.available() > 0) {
                    char c = (char) reader.read();
                    stringBuilder.append(c);
                    /* skip lines that don't contain the built-date */
                    if (stringBuilder.toString().contains(System.getProperty("line.separator")) &&
                            !stringBuilder.toString().contains("Built-Date")) stringBuilder.setLength(0);
                }
            }
        } catch (Exception e) {
            return "Error reading manifest!";
        }
        version = stringBuilder.toString();
        return stringBuilder.toString();
    };


    @Override
    public URL[] getDocs() {
        try {
            return new URL[] { new URL("http://wiki.apache.org/solr/MoreLikeThis") };
        }
        catch( MalformedURLException ex ) { return null; }
    }
}