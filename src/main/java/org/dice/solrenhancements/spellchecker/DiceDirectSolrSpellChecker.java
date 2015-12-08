package org.dice.solrenhancements.spellchecker;

/**
 * Created by simon.hughes on 12/17/14.
 *
 * This modifies the solr spell checker to allow loading and applying a file of common typos. Solr, for performance
 * reasons, will only look for corrections within an edit distance of 2. Using common typos adds a lot more corrections
 * to the mix.
 */

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

import org.apache.lucene.analysis.Token;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.spelling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spellchecker implementation that uses {@link DirectSpellChecker}
 * <p>
 * Requires no auxiliary index or data structure.
 * <p>
 * Supported options:
 * <ul>
 *   <li>field: Used as the source of terms.
 *   <li>distanceMeasure: Sets {@link DirectSpellChecker#setDistance(StringDistance)}.
 *       Note: to set the default {@link DirectSpellChecker#INTERNAL_LEVENSHTEIN}, use "internal".
 *   <li>accuracy: Sets {@link DirectSpellChecker#setAccuracy(float)}.
 *   <li>maxEdits: Sets {@link DirectSpellChecker#setMaxEdits(int)}.
 *   <li>minPrefix: Sets {@link DirectSpellChecker#setMinPrefix(int)}.
 *   <li>maxInspections: Sets {@link DirectSpellChecker#setMaxInspections(int)}.
 *   <li>comparatorClass: Sets {@link DirectSpellChecker#setComparator(Comparator)}.
 *       Note: score-then-frequency can be specified as "score" and frequency-then-score
 *       can be specified as "freq".
 *   <li>thresholdTokenFrequency: sets {@link DirectSpellChecker#setThresholdFrequency(float)}.
 *   <li>minQueryLength: sets {@link DirectSpellChecker#setMinQueryLength(int)}.
 *   <li>maxQueryFrequency: sets {@link DirectSpellChecker#setMaxQueryFrequency(float)}.
 * </ul>
 * @see DirectSpellChecker
 */
public class DiceDirectSolrSpellChecker extends SolrSpellChecker {
    private static final Logger LOG = LoggerFactory.getLogger(DiceDirectSolrSpellChecker.class);

    // configuration params shared with other spellcheckers
    public static final String COMPARATOR_CLASS = AbstractLuceneSpellChecker.COMPARATOR_CLASS;
    public static final String SCORE_COMP = AbstractLuceneSpellChecker.SCORE_COMP;
    public static final String FREQ_COMP = AbstractLuceneSpellChecker.FREQ_COMP;
    public static final String STRING_DISTANCE = AbstractLuceneSpellChecker.STRING_DISTANCE;
    public static final String ACCURACY = AbstractLuceneSpellChecker.ACCURACY;
    public static final String THRESHOLD_TOKEN_FREQUENCY = IndexBasedSpellChecker.THRESHOLD_TOKEN_FREQUENCY;

    public static final String INTERNAL_DISTANCE = "internal";
    public static final float DEFAULT_ACCURACY = 0.5f;
    public static final float DEFAULT_THRESHOLD_TOKEN_FREQUENCY = 0.0f;

    public static final String MAXEDITS = "maxEdits";
    public static final int DEFAULT_MAXEDITS = 2;

    // params specific to this implementation
    public static final String MINPREFIX = "minPrefix";
    public static final int DEFAULT_MINPREFIX = 1;

    public static final String MAXINSPECTIONS = "maxInspections";
    public static final int DEFAULT_MAXINSPECTIONS = 5;

    public static final String MINQUERYLENGTH = "minQueryLength";
    public static final int DEFAULT_MINQUERYLENGTH = 4;

    public static final String MAXQUERYFREQUENCY = "maxQueryFrequency";
    public static final float DEFAULT_MAXQUERYFREQUENCY = 0.01f;

    public static final String TYPOS_FILENAME_CFG = "typosFileName";


    public static String typosFile = null;
    private static final ConcurrentHashMap<String,String> mapTypos = new ConcurrentHashMap<String, String>();
    private boolean typosLoaded = false;

    public static final String DEFAULT_SOURCE_FILE_CHAR_ENCODING = "UTF-8";
    public static final String SOURCE_FILE_CHAR_ENCODING = "characterEncoding";
    private String characterEncoding;

    private DirectSpellChecker checker = new DirectSpellChecker();

    private List<String> readLines(SolrCore core, String fileName) throws IOException {

        return core.getResourceLoader().getLines(fileName, characterEncoding);
    }

    private static String normalize(String s){
        return s.trim().toLowerCase();
    }

    private void initTyposMap(SolrCore core, String fileName){
        synchronized (this.mapTypos) {
            if (fileName != null && mapTypos.size() == 0) {
                try {
                    List<String> lines = readLines(core, fileName);
                    for (String line : lines) {
                        String[] split = line.split("=>");
                        final String rhs = split[1].trim();
                        for (String lhs : split[0].split(",")) {
                            final String normalized = normalize(lhs);
                            this.mapTypos.put(normalized, rhs);
                        }
                    }
                    typosLoaded = true;
                } catch (FileNotFoundException e) {
                    throw new SolrException(
                            SolrException.ErrorCode.NOT_FOUND, String.format("Unable to find file %s", fileName), e);
                } catch (IOException e) {
                    throw new SolrException(
                            SolrException.ErrorCode.UNKNOWN, String.format("Error loading file %s", fileName), e);
                } catch (Exception e) {
                    throw new SolrException(
                            SolrException.ErrorCode.UNKNOWN, String.format("Error loading file %s", fileName), e);
                }
            }
        }
    }

    @Override
    public String init(NamedList config, SolrCore core) {
        LOG.info("init: " + config);
        String name = super.init(config, core);

        Comparator<SuggestWord> comp = SuggestWordQueue.DEFAULT_COMPARATOR;
        String compClass = (String) config.get(COMPARATOR_CLASS);
        if (compClass != null) {
            if (compClass.equalsIgnoreCase(SCORE_COMP))
                comp = SuggestWordQueue.DEFAULT_COMPARATOR;
            else if (compClass.equalsIgnoreCase(FREQ_COMP))
                comp = new SuggestWordFrequencyComparator();
            else //must be a FQCN
                comp = (Comparator<SuggestWord>) core.getResourceLoader().newInstance(compClass, Comparator.class);
        }

        characterEncoding = DEFAULT_SOURCE_FILE_CHAR_ENCODING;
        String charEncoding = (String) config.get(SOURCE_FILE_CHAR_ENCODING);
        if(charEncoding != null && charEncoding.length() != 0){
            characterEncoding = charEncoding;
        }

        StringDistance sd = DirectSpellChecker.INTERNAL_LEVENSHTEIN;
        String distClass = (String) config.get(STRING_DISTANCE);
        if (distClass != null && !distClass.equalsIgnoreCase(INTERNAL_DISTANCE))
            sd = core.getResourceLoader().newInstance(distClass, StringDistance.class);

        float minAccuracy = DEFAULT_ACCURACY;
        Float accuracy = (Float) config.get(ACCURACY);
        if (accuracy != null)
            minAccuracy = accuracy;

        int maxEdits = DEFAULT_MAXEDITS;
        Integer edits = (Integer) config.get(MAXEDITS);
        if (edits != null)
            maxEdits = edits;

        int minPrefix = DEFAULT_MINPREFIX;
        Integer prefix = (Integer) config.get(MINPREFIX);
        if (prefix != null)
            minPrefix = prefix;

        int maxInspections = DEFAULT_MAXINSPECTIONS;
        Integer inspections = (Integer) config.get(MAXINSPECTIONS);
        if (inspections != null)
            maxInspections = inspections;

        float minThreshold = DEFAULT_THRESHOLD_TOKEN_FREQUENCY;
        Float threshold = (Float) config.get(THRESHOLD_TOKEN_FREQUENCY);
        if (threshold != null)
            minThreshold = threshold;

        int minQueryLength = DEFAULT_MINQUERYLENGTH;
        Integer queryLength = (Integer) config.get(MINQUERYLENGTH);
        if (queryLength != null)
            minQueryLength = queryLength;

        float maxQueryFrequency = DEFAULT_MAXQUERYFREQUENCY;
        Float queryFreq = (Float) config.get(MAXQUERYFREQUENCY);
        if (queryFreq != null)
            maxQueryFrequency = queryFreq;

        Object oTyposFileName = config.get(TYPOS_FILENAME_CFG);
        if(oTyposFileName != null){
            this.typosFile = oTyposFileName.toString();
        }

        checker.setComparator(comp);
        checker.setDistance(sd);
        checker.setMaxEdits(maxEdits);
        checker.setMinPrefix(minPrefix);
        checker.setAccuracy(minAccuracy);
        checker.setThresholdFrequency(minThreshold);
        checker.setMaxInspections(maxInspections);
        checker.setMinQueryLength(minQueryLength);
        checker.setMaxQueryFrequency(maxQueryFrequency);
        checker.setLowerCaseTerms(false);

        return name;
    }

    @Override
    public synchronized void reload(SolrCore core, SolrIndexSearcher searcher) throws IOException {

        if(this.typosFile != null) {
            this.mapTypos.clear();
            this.typosLoaded = false;
            this.initTyposMap(core, this.typosFile);
        }
    }

    @Override
    public void build(SolrCore core, SolrIndexSearcher searcher) throws IOException {}

    @Override
    public SpellingResult getSuggestions(SpellingOptions options)
            throws IOException {

        LOG.debug("getSuggestions: " + options.tokens);
        // load the typos file if not loaded

        SpellingResult result = new SpellingResult();
        float accuracy = (options.accuracy == Float.MIN_VALUE) ? checker.getAccuracy() : options.accuracy;

        for (Token token : options.tokens) {
            String tokenText = token.toString();
            Term term = new Term(field, tokenText);
            int freq = options.reader.docFreq(term);
            int count = (options.alternativeTermCount >0 && freq > 0) ? options.alternativeTermCount: options.count;
            SuggestWord[] suggestions = checker.suggestSimilar(term, count,options.reader, options.suggestMode, accuracy);
            result.addFrequency(token, freq);

            // Dice functionality: Allow also loading of a list of spelling corrections to apply in addition
            // to the standard functionality. This allows us to configure common typos to correct that may exceed the
            // max edit distance used by solr
            if(this.typosLoaded){
                String normTokenText = normalize(tokenText);
                String match = this.mapTypos.get(normTokenText);
                if(match != null){
                    int matchFreq = options.reader.docFreq(new Term(field, match));
                    // only ever suggest values that are in the index and more frequent
                    // than the original word
                    if(matchFreq > 0 && matchFreq > freq) {
                        result.add(token, match, matchFreq);
                    }
                }
            }

            // If considering alternatives to "correctly-spelled" terms, then add the
            // original as a viable suggestion.
            if (options.alternativeTermCount > 0 && freq > 0) {
                boolean foundOriginal = false;
                SuggestWord[] suggestionsWithOrig = new SuggestWord[suggestions.length + 1];
                for (int i = 0; i < suggestions.length; i++) {
                    if (suggestions[i].string.equals(tokenText)) {
                        foundOriginal = true;
                        break;
                    }
                    suggestionsWithOrig[i + 1] = suggestions[i];
                }
                if (!foundOriginal) {
                    SuggestWord orig = new SuggestWord();
                    orig.freq = freq;
                    orig.string = tokenText;
                    suggestionsWithOrig[0] = orig;
                    suggestions = suggestionsWithOrig;
                }
            }
            if(suggestions.length==0 && freq==0) {
                List<String> empty = Collections.emptyList();
                result.add(token, empty);
            } else {
                for (SuggestWord suggestion : suggestions) {
                    result.add(token, suggestion.string, suggestion.freq);
                }
            }
        }
        return result;
    }

    @Override
    public float getAccuracy() {
        return checker.getAccuracy();
    }
    @Override
    public StringDistance getStringDistance() {
        return checker.getDistance();
    }
}