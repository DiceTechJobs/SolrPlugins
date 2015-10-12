package org.dice.solrenhancements.spellchecker;
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
    *
    * SH: This doesn't do anything different to solr src it's currently just for testing the suggester functionality, so see why it's failing for
    * certain scenarios.
    */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.HighFrequencyDictionary;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.spelling.SolrSpellChecker;
import org.apache.solr.spelling.SpellingOptions;
import org.apache.solr.spelling.SpellingResult;
import org.apache.solr.spelling.suggest.LookupFactory;
import org.apache.solr.spelling.suggest.fst.FSTLookupFactory;
import org.apache.solr.spelling.suggest.jaspell.JaspellLookupFactory;
import org.apache.solr.spelling.suggest.tst.TSTLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class DiceMultipleCaseSuggester extends SolrSpellChecker {
    private static final Logger LOG = LoggerFactory.getLogger(DiceMultipleCaseSuggester.class);

    /** Location of the source data - either a path to a file, or null for the
     * current IndexReader.
     */
    public static final String LOCATION = "sourceLocation";

    public static final String SUGGESTION_ANALYZER_FIELDTYPE = "suggestionAnalyzerFieldTypeName";

    /** Fully-qualified class of the {@link Lookup} implementation. */
    public static final String LOOKUP_IMPL = "lookupImpl";
    /**
     * Minimum frequency of terms to consider when building the dictionary.
     */
    public static final String THRESHOLD_TOKEN_FREQUENCY = "threshold";
    /**
     * Name of the location where to persist the dictionary. If this location
     * is relative then the data will be stored under the core's dataDir. If this
     * is null the storing will be disabled.
     */
    public static final String STORE_DIR = "storeDir";

    protected String sourceLocation;
    protected File storeDir;
    protected float threshold;
    protected Dictionary dictionary;
    protected IndexReader reader;
    protected Lookup lookup;
    protected String lookupImpl;
    protected SolrCore core;

    private LookupFactory factory;

    private Analyzer suggestionAnalyzer = null;
    private String suggestionAnalyzerFieldTypeName = null;

    @Override
    public String init(NamedList config, SolrCore core) {
        LOG.info("init: " + config);
        String name = super.init(config, core);
        threshold = config.get(THRESHOLD_TOKEN_FREQUENCY) == null ? 0.0f
                : (Float)config.get(THRESHOLD_TOKEN_FREQUENCY);
        sourceLocation = (String) config.get(LOCATION);
        lookupImpl = (String)config.get(LOOKUP_IMPL);

        IndexSchema schema = core.getLatestSchema();
        suggestionAnalyzerFieldTypeName = (String)config.get(SUGGESTION_ANALYZER_FIELDTYPE);
        if (schema.getFieldTypes().containsKey(suggestionAnalyzerFieldTypeName))  {
            FieldType fieldType = schema.getFieldTypes().get(suggestionAnalyzerFieldTypeName);
            suggestionAnalyzer = fieldType.getQueryAnalyzer();
        }

        // support the old classnames without -Factory for config file backwards compatibility.
        if (lookupImpl == null || "org.apache.solr.spelling.suggest.jaspell.JaspellLookup".equals(lookupImpl)) {
            lookupImpl = JaspellLookupFactory.class.getName();
        } else if ("org.apache.solr.spelling.suggest.tst.TSTLookup".equals(lookupImpl)) {
            lookupImpl = TSTLookupFactory.class.getName();
        } else if ("org.apache.solr.spelling.suggest.fst.FSTLookup".equals(lookupImpl)) {
            lookupImpl = FSTLookupFactory.class.getName();
        }

        factory = core.getResourceLoader().newInstance(lookupImpl, LookupFactory.class);

        lookup = factory.create(config, core);
        String store = (String)config.get(STORE_DIR);
        if (store != null) {
            storeDir = new File(store);
            if (!storeDir.isAbsolute()) {
                storeDir = new File(core.getDataDir() + File.separator + storeDir);
            }
            if (!storeDir.exists()) {
                storeDir.mkdirs();
            } else {
                // attempt reload of the stored lookup
                try {
                    lookup.load(new FileInputStream(new File(storeDir, factory.storeFileName())));
                } catch (IOException e) {
                    LOG.warn("Loading stored lookup data failed", e);
                }
            }
        }
        return name;
    }

    @Override
    public void build(SolrCore core, SolrIndexSearcher searcher) throws IOException {
            LOG.info("build()");
        if (sourceLocation == null) {
            reader = searcher.getIndexReader();
            dictionary = new HighFrequencyDictionary(reader, field, threshold);
        } else {
            try {

                final String fileDelim = ",";
                if(sourceLocation.contains(fileDelim)){
                    String[] files = sourceLocation.split(fileDelim);
                    Reader[] readers = new Reader[files.length];
                    for(int i = 0; i < files.length; i++){
                        Reader reader = new InputStreamReader(
                                core.getResourceLoader().openResource(files[i]),IOUtils.CHARSET_UTF_8);
                        readers[i] = reader;
                    }
                    dictionary = new MultipleFileDictionary(readers);
                }
                else{
                    dictionary = new FileDictionary(new InputStreamReader(
                            core.getResourceLoader().openResource(sourceLocation), IOUtils.CHARSET_UTF_8));
                }
            } catch (UnsupportedEncodingException e) {
                // should not happen
                LOG.error("should not happen", e);
            }
        }

        lookup.build(dictionary);
        if (storeDir != null) {
            File target = new File(storeDir, factory.storeFileName());
            if(!lookup.store(new FileOutputStream(target))) {
                if (sourceLocation == null) {
                    assert reader != null && field != null;
                    LOG.error("Store Lookup build from index on field: " + field + " failed reader has: " + reader.maxDoc() + " docs");
                } else {
                    LOG.error("Store Lookup build from sourceloaction: " + sourceLocation + " failed");
                }
            } else {
                LOG.info("Stored suggest data to: " + target.getAbsolutePath());
            }
        }
    }

    @Override
    public void reload(SolrCore core, SolrIndexSearcher searcher) throws IOException {
        LOG.info("reload()");
        if (dictionary == null && storeDir != null) {
            // this may be a firstSearcher event, try loading it
            FileInputStream is = new FileInputStream(new File(storeDir, factory.storeFileName()));
            try {
                if (lookup.load(is)) {
                    return;  // loaded ok
                }
            } finally {
                IOUtils.closeWhileHandlingException(is);
            }
            LOG.debug("load failed, need to build Lookup again");
        }
        // loading was unsuccessful - build it again
        build(core, searcher);
    }

    static SpellingResult EMPTY_RESULT = new SpellingResult();

    @Override
    public SpellingResult getSuggestions(SpellingOptions options) throws IOException {
        LOG.debug("getSuggestions: " + options.tokens);
        if (lookup == null) {
            LOG.info("Lookup is null - invoke spellchecker.build first");
            return EMPTY_RESULT;
        }

        SpellingResult res = new SpellingResult();
        for (Token currentToken : options.tokens) {
            String tokenText = currentToken.toString();

            // we need to ensure that we combine matches for different cases, and take the most common
            // where multiple case versions exist
            final Hashtable<String, LookupResult> htSuggestions = new Hashtable<String, LookupResult>();
            final Hashtable<String, Integer> htSuggestionCounts = new Hashtable<String, Integer>();

            List<Token> tokensToTry = new ArrayList<Token>();
            tokensToTry.add(currentToken);
            tokensToTry.add(newToken(currentToken, toTitleCase(tokenText)));
            tokensToTry.add(newToken(currentToken, tokenText.toLowerCase()));
            tokensToTry.add(newToken(currentToken, tokenText.toUpperCase()));

            for(Token newToken: tokensToTry){

                if (newToken.toString().equals(tokenText) && newToken != currentToken){
                    continue;
                }
                // if matches current token, skip
                List<LookupResult> tmpSuggestions = getLookupResults(options, newToken);
                if(tmpSuggestions != null){
                    for(LookupResult lu: tmpSuggestions) {
                        final String key = lu.key.toString().toLowerCase();
                        LookupResult existing = htSuggestions.get(key);
                        if(existing != null) {
                            // replace if more frequent
                            if (lu.value > existing.value) {
                                htSuggestions.put(key, lu);
                            }
                            htSuggestionCounts.put(key, htSuggestionCounts.get(key) + (int)lu.value);
                        }
                        else{
                            htSuggestions.put(key, lu);
                            htSuggestionCounts.put(key, (int)lu.value);
                        }
                    }
                }
            }

            List<String> suggestions = new ArrayList<String>(htSuggestions.keySet());
            if (options.suggestMode != SuggestMode.SUGGEST_MORE_POPULAR) {
                Collections.sort(suggestions);
            }
            else{
                Collections.sort(suggestions, new Comparator<String>() {
                    public int compare(String sug1, String sug2) {
                        int sug1Count = htSuggestionCounts.get(sug1);
                        int sug2Count = htSuggestionCounts.get(sug2);
                        return sug2Count - sug1Count;
                    }
                });
            }

            for (String match : suggestions) {
                LookupResult lr = htSuggestions.get(match);
                res.add(currentToken, lr.key.toString(), (int)lr.value);
            }

        }
        return res;
    }

    private Token newToken(Token existing, String newText){
        return new Token(newText, existing.startOffset(), existing.endOffset(), existing.type());
    }

    private String toTitleCase(String s){
        if(s.length() == 0){
            return s;
        }
        if(s.length() == 1){
            return s.toUpperCase();
        }
        return String.valueOf(s.charAt(0)).toUpperCase() + s.substring(1).toLowerCase();
    }

    private List<LookupResult> getLookupResults(SpellingOptions options, Token currentToken) {
        CharsRef scratch = new CharsRef();
        scratch.chars = currentToken.buffer();
        scratch.offset = 0;
        scratch.length = currentToken.length();
        boolean onlyMorePopular = (options.suggestMode == SuggestMode.SUGGEST_MORE_POPULAR) &&
                !(lookup instanceof WFSTCompletionLookup) &&
                !(lookup instanceof AnalyzingSuggester);

        List<LookupResult> suggestions = lookup.lookup(scratch, onlyMorePopular, options.count);
        if (suggestions == null || suggestions.size() == 0) {
            return null;
        }

        return suggestions;
    }

    private boolean isStringNullOrEmpty(String s){
        return s == null || s.length() == 0;
    }

    private String getAnalyzerResult(String suggestion){
        TokenStream ts = null;
        try {
            Reader reader = new StringReader(suggestion);
            ts = this.suggestionAnalyzer.tokenStream("", reader);

            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String word = termAtt.toString();
                if(word != null && word.length() > 0) {
                    return word;
                }
            }
        }
        catch (Exception ex){
            if(this.field != null)
            {
                LOG.error(String.format("Error executing analyzer for field: {0} in DiceSuggester on suggestion: {1}",
                        this.field, suggestion), ex);
            }else if(this.fieldTypeName != null){
                LOG.error(String.format("Error executing analyzer for field type: {0} in DiceSuggester on suggestion: {1}",
                        this.fieldTypeName, suggestion), ex);
            }
        }
        finally {
            if(ts != null)
            {
                IOUtils.closeWhileHandlingException(ts);
            }
        }
        return null;
    }
}