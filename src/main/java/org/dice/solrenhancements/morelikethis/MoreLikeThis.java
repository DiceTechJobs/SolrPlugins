package org.dice.solrenhancements.morelikethis;

/**
 * Created by simon.hughes on 9/2/14.
 */
/**
 * Copyright 2004-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.*;
import org.apache.lucene.util.PriorityQueue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;


/**
 * Generate "more queryFromDocuments this" similarity queries.
 * Based on this mail:
 * <code><pre>
 * Lucene does let you access the document frequency of terms, with IndexReader.docFreq().
 * Term frequencies can be computed by re-tokenizing the text, which, for a single document,
 * is usually fast enough.  But looking up the docFreq() of every term in the document is
 * probably too slow.
 * <p/>
 * You can use some heuristics to prune the set of terms, to avoid calling docFreq() too much,
 * or at all.  Since you're trying to maximize a tf*idf score, you're probably most interested
 * in terms with a high tf. Choosing a tf threshold even as low as two or three will radically
 * reduce the number of terms under consideration.  Another heuristic is that terms with a
 * high idf (i.e., a low df) tend to be longer.  So you could threshold the terms by the
 * number of characters, not selecting anything less than, e.g., six or seven characters.
 * With these sorts of heuristics you can usually find small set of, e.g., ten or fewer terms
 * that do a pretty good job of characterizing a document.
 * <p/>
 * It all depends on what you're trying to do.  If you're trying to eek out that last percent
 * of precision and recall regardless of computational difficulty so that you can win a TREC
 * competition, then the techniques I mention above are useless.  But if you're trying to
 * provide a "more queryFromDocuments this" button on a search results page that does a decent job and has
 * good performance, such techniques might be useful.
 * <p/>
 * An efficient, effective "more-queryFromDocuments-this" query generator would be a great contribution, if
 * anyone's interested.  I'd imagine that it would take a Reader or a String (the document's
 * text), analyzer Analyzer, and return a set of representative terms using heuristics queryFromDocuments those
 * above.  The frequency and length thresholds could be parameters, etc.
 * <p/>
 * Doug
 * </pre></code>
 * <p/>
 * <p/>
 * <p/>
 * <h3>Initial Usage</h3>
 * <p/>
 * This class has lots of options to try to make it efficient and flexible.
 * The simplest possible usage is as follows. The bold
 * fragment is specific to this class.
 * <p/>
 * <pre class="prettyprint">
 * <p/>
 * IndexReader ir = ...
 * IndexSearcher is = ...
 * <p/>
 * MoreLikeThis mlt = new MoreLikeThis(ir);
 * Reader target = ... // orig source of doc you want to find similarities to
 * Query query = mlt.queryFromDocuments( target);
 * <p/>
 * Hits hits = is.search(query);
 * // now the usual iteration thru 'hits' - the only thing to watch for is to make sure
 * //you ignore the doc if it matches your 'target' document, as it should be similar to itself
 * <p/>
 * </pre>
 * <p/>
 * Thus you:
 * <ol>
 * <li> do your normal, Lucene setup for searching,
 * <li> create a MoreLikeThis,
 * <li> get the text of the doc you want to find similarities to
 * <li> then call one of the queryFromDocuments() calls to generate a similarity query
 * <li> call the searcher to find the similar docs
 * </ol>
 * <p/>
 * <h3>More Advanced Usage</h3>
 * <p/>
 * You may want to use {@link #setFieldNames setFieldNames(...)} so you can examine
 * multiple fields (e.g. body and title) for similarity.
 * <p/>
 * <p/>
 * Depending on the size of your index and the size and makeup of your documents you
 * may want to call the other set methods to control how the similarity queries are
 * generated:
 * <ul>
 * <li> {@link #setMinTermFreq setMinTermFreq(...)}
 * <li> {@link #setMinDocFreq setMinDocFreq(...)}
 * <li> {@link #setMaxDocFreq setMaxDocFreq(...)}
 * <li> {@link #setMaxDocFreqPct setMaxDocFreqPct(...)}
 * <li> {@link #setMinWordLen setMinWordLen(...)}
 * <li> {@link #setMaxWordLen setMaxWordLen(...)}
 * <li> {@link #setMaxQueryTermsPerField setMaxQueryTermsPerField(...)}
 * <li> {@link #setMaxNumTokensParsedPerField setMaxNumTokensParsedPerField(...)}
 * <li> {@link #setStopWords setStopWord(...)}
 * </ul>
 * <p/>
 * <hr>
 * <pre>
 * Changes: Mark Harwood 29/02/04
 * Some bugfixing, some refactoring, some optimisation.
 * - bugfix: retrieveTerms(int docNum) was not working for indexes without a termvector -added missing code
 * - bugfix: No significant terms being created for fields with a termvector - because
 * was only counting one occurrence per term/field pair in calculations(ie not including frequency info from TermVector)
 * - refactor: moved common code into isNoiseWord()
 * - optimise: when no termvector support available - used maxNumTermsParsed to limit amount of tokenization
 * </pre>
 */
public final class MoreLikeThis {

    /**
     * Default maximum number of tokens to parse in each example doc field that is not stored with TermVector support.
     *
     * @see #getMaxNumTokensParsedPerField
     */
    public static final int DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD = 5000;

    /**
     * Ignore terms with less than this frequency in the source doc.
     *
     * @see #getMinTermFreq
     * @see #setMinTermFreq
     */
    public static final int DEFAULT_MIN_TERM_FREQ = 1;

    /**
     * Ignore words which do not occur in at least this many docs.
     *
     * @see #getMinDocFreq
     * @see #setMinDocFreq
     */
    public static final int DEFAULT_MIN_DOC_FREQ = 5;

    /**
     * Ignore words which occur in more than this many docs.
     *
     * @see #getMaxDocFreq
     * @see #setMaxDocFreq
     * @see #setMaxDocFreqPct
     */
    public static final int DEFAULT_MAX_DOC_FREQ = Integer.MAX_VALUE;

    /**
     * Boost terms in query based on score.
     *
     * @see #isBoost
     * @see #setBoost
     */
    public static final boolean DEFAULT_BOOST = true;

    /**
     * Normalize field boosts
     *
     * @see #isNormalizeFieldBoosts
     * @see #setNormalizeFieldBoosts
     */
    public static final boolean DEFAULT_NORMALIZE_FIELD_BOOSTS = true;

    /**
     * Log the term frequency of use the raw frequency?
     *
     * @see #isLogTf
     * @see #setLogTf
     */
    public static final boolean DEFAULT_IS_LOG_TF = false;

    /**
     * Default field names. Null is used to specify that the field names should be looked
     * up at runtime from the provided reader.
     */
    public static final String[] DEFAULT_FIELD_NAMES = new String[]{"contents"};

    /**
     * Ignore words less than this length or if 0 then this has no effect.
     *
     * @see #getMinWordLen
     * @see #setMinWordLen
     */
    public static final int DEFAULT_MIN_WORD_LENGTH = 0;

    /**
     * Ignore words greater than this length or if 0 then this has no effect.
     *
     * @see #getMaxWordLen
     * @see #setMaxWordLen
     */
    public static final int DEFAULT_MAX_WORD_LENGTH = 0;

    /**
     * Default set of stopwords.
     * If null means to allow stop words.
     *
     * @see #setStopWords
     * @see #getStopWords
     */
    public static final Set<?> DEFAULT_STOP_WORDS = null;

    /**
     * Current set of stop words.
     */
    private Set<?> stopWords = DEFAULT_STOP_WORDS;

    /**
     * Return a Query with no more than this many terms.
     *
     * @see org.apache.lucene.search.BooleanQuery#getMaxClauseCount
     * @see #getMaxQueryTermsPerField
     * @see #setMaxQueryTermsPerField
     */
    public static final int DEFAULT_MAX_QUERY_TERMS_PER_FIELD = 100;

    /**
     * Analyzer that will be used to parse the doc.
     */
    private Analyzer analyzer = null;

    /**
     * Ignore words less frequent that this.
     */
    private int minTermFreq = DEFAULT_MIN_TERM_FREQ;

    /**
     * Ignore words which do not occur in at least this many docs.
     */
    private int minDocFreq = DEFAULT_MIN_DOC_FREQ;

    /**
     * Ignore words which occur in more than this many docs.
     */
    private int maxDocFreq = DEFAULT_MAX_DOC_FREQ;

    /**
     * Should we apply a boost to the Query based on the scores?
     */
    private boolean boost = DEFAULT_BOOST;

    /**
     * Should we normalized the field boosts per field?
     */
    private boolean normalizeFieldBoosts = DEFAULT_NORMALIZE_FIELD_BOOSTS;

    /**
     * Should we normalized the field boosts per field?
     */
    private boolean isLogTf = DEFAULT_IS_LOG_TF;

    /**
     * Field name we'll analyze.
     */
    private String[] fieldNames = DEFAULT_FIELD_NAMES;
    private String[] matchFieldNames = new String[]{};
    private String[] differentFieldNames = new String[]{};

    private String streamHead = null;

    private String[] streamBodyfieldNames = new String[0];
    private String[] streamHeadfieldNames = new String[0];

    private HashSet<String> payloadFields = new HashSet<String>();

    private Map<String,Float> boostFields;
    private Map<String,Float> streamBoostFields;


    /**
     * The maximum number of tokens to parse in each example doc field that is not stored with TermVector support
     */
    private int maxNumTokensParsedPerField = DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD;

    /**
     * Ignore words if less than this len.
     */
    private int minWordLen = DEFAULT_MIN_WORD_LENGTH;

    /**
     * Ignore words if greater than this len.
     */
    private int maxWordLen = DEFAULT_MAX_WORD_LENGTH;

    /**
     * Don't return a query longer than this.
     */
    private int maxQueryTermsPerField = DEFAULT_MAX_QUERY_TERMS_PER_FIELD;

    /**
     * For idf() calculations.
     */
    private TFIDFSimilarity similarity;// = new DefaultSimilarity();

    /**
     * IndexReader to use
     */
    private final IndexReader ir;

    /**
     * Tie Breaker used in DisjunctionMaxQuery
     **/
    private String boostFn = "";

    /**
     * Gets the text for the Multiplicative Boost Function
     *
     * @return the multiplicative boostFunction used in the MLT query
     * @see #setBoostFn(String)
     **/
    public String getBoostFn() {
        return boostFn;
    }

    /**
     * Sets the text for the Multiplicative Boost Function
     *
     * @see #getBoostFn()
     **/
    public void setBoostFn(String boostFn) {
        this.boostFn = boostFn;
    }

    /**
     * Constructor requiring an IndexReader.
     */
    public MoreLikeThis(IndexReader ir) {
        this(ir, new DefaultSimilarity());
    }

    public MoreLikeThis(IndexReader ir, TFIDFSimilarity sim) {
        this.ir = ir;
        this.similarity = sim;

    }


    public TFIDFSimilarity getSimilarity() {
        return similarity;
    }

    public void setSimilarity(TFIDFSimilarity similarity) {
        this.similarity = similarity;
    }

    /**
     * Returns an analyzer that will be used to parse source doc with. The default analyzer
     * is not set.
     *
     * @return the analyzer that will be used to parse source doc with.
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * Sets the analyzer to use. An analyzer is not required for generating a query with the
     * {@link #like(List<Integer>)} method, all other 'queryFromDocuments' methods require an analyzer.
     *
     * @param analyzer the analyzer to use to tokenize text.
     */
    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Returns the frequency below which terms will be ignored in the source doc. The default
     * frequency is the {@link #DEFAULT_MIN_TERM_FREQ}.
     *
     * @return the frequency below which terms will be ignored in the source doc.
     */
    public int getMinTermFreq() {
        return minTermFreq;
    }

    /**
     * Sets the frequency below which terms will be ignored in the source doc.
     *
     * @param minTermFreq the frequency below which terms will be ignored in the source doc.
     */
    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    /**
     * Returns the frequency at which words will be ignored which do not occur in at least this
     * many docs. The default frequency is {@link #DEFAULT_MIN_DOC_FREQ}.
     *
     * @return the frequency at which words will be ignored which do not occur in at least this
     *         many docs.
     */
    public int getMinDocFreq() {
        return minDocFreq;
    }

    /**
     * Sets the frequency at which words will be ignored which do not occur in at least this
     * many docs.
     *
     * @param minDocFreq the frequency at which words will be ignored which do not occur in at
     * least this many docs.
     */
    public void setMinDocFreq(int minDocFreq) {
        this.minDocFreq = minDocFreq;
    }

    /**
     * Returns the maximum frequency in which words may still appear.
     * Words that appear in more than this many docs will be ignored. The default frequency is
     * {@link #DEFAULT_MAX_DOC_FREQ}.
     *
     * @return get the maximum frequency at which words are still allowed,
     *         words which occur in more docs than this are ignored.
     */
    public int getMaxDocFreq() {
        return maxDocFreq;
    }

    /**
     * Set the maximum frequency in which words may still appear. Words that appear
     * in more than this many docs will be ignored.
     *
     * @param maxFreq the maximum count of documents that a term may appear
     * in to be still considered relevant
     */
    public void setMaxDocFreq(int maxFreq) {
        this.maxDocFreq = maxFreq;
    }

    /**
     * Set the maximum percentage in which words may still appear. Words that appear
     * in more than this many percent of all docs will be ignored.
     *
     * @param maxPercentage the maximum percentage of documents (0-100) that a term may appear
     * in to be still considered relevant
     */
    public void setMaxDocFreqPct(int maxPercentage) {
        this.maxDocFreq = maxPercentage * ir.numDocs() / 100;
    }

    /**
     * Returns whether to boost terms in query based on "score" or not. The default is
     * {@link #DEFAULT_BOOST}.
     *
     * @return whether to boost terms in query based on "score" or not.
     * @see #setBoost
     */
    public boolean isBoost() {
        return boost;
    }

    /**
     * Sets whether to boost terms in query based on "score" or not.
     *
     * @param boost true to boost terms in query based on "score", false otherwise.
     * @see #isBoost
     */
    public void setBoost(boolean boost) {
        this.boost = boost;
    }

    /**
     * Returns whether to normalize the size of field level boosts across all field terms
     * {@Link #DEFAULT_NORMALIZE_FIELD_BOOSTS}
     *
     * @return whether to normalize field boosts to unit length, or not
     * @see #setNormalizeFieldBoosts(boolean)
     */
    public boolean isNormalizeFieldBoosts() {
        return normalizeFieldBoosts;
    }

    /**
     * Sets whether to normalize the size of field level boosts across all field terms or not
     *
     * @param normalizeFieldBoosts true to field boosts to unit length, or false otherwise.
     * @see #isNormalizeFieldBoosts
     */
    public void setNormalizeFieldBoosts(boolean normalizeFieldBoosts) {
        this.normalizeFieldBoosts = normalizeFieldBoosts;
    }

    /**
     * Returns whether to log the term frequency of the fields
     * {@Link #DEFAULT_IS_LOG_TF}
     *
     * @return whether to take the logarithm of the term frequency or not
     * @see #setLogTf(boolean)
     */
    public boolean isLogTf() {
        return isLogTf;
    }

    /**
     * Sets whether to log the term frequency of the fields
     *
     * @param isLogTf true to take the logarithm of the term frequency or not, false otherwise
     * @see #isLogTf
     */
    public void setLogTf(boolean isLogTf) {
        this.isLogTf = isLogTf;
    }

    /**
     * Returns the field names that will be used when generating the 'More Like This' query.
     * The default field names that will be used is {@link #DEFAULT_FIELD_NAMES}.
     *
     * @return the field names that will be used when generating the 'More Like This' query.
     */
    public String[] getFieldNames() {
        if (fieldNames == null) {
            // gather list of all valid fields from lucene, if none specified
            Collection<String> fields = MultiFields.getIndexedFields(ir);
            fieldNames = fields.toArray(new String[fields.size()]);
        }

        return fieldNames;
    }

    /**
     * Returns the field names must be matched in the target document
     *
     * @return the field names that must be matched in the target document
     */
    public String[] getMatchFieldNames() {
        return matchFieldNames;
    }

    /**
     * Returns the field names must NOT be matched in the target document
     *
     * @return the field names that must NOT be matched in the target document
     */
    public String[] getDifferentFieldNames() {
        return differentFieldNames;
    }

    /**
     * Sets the field names that will be used when generating the 'More Like This' query.
     * Set this to null for the field names to be determined at runtime from the IndexReader
     * provided in the constructor.
     *
     * @param fieldNames the field names that will be used when generating the 'More Like This'
     * query.
     */
    public void setFieldNames(String[] fieldNames) {
        this.fieldNames = fieldNames;
    }

    /**
     * Sets the field names that must match the target document in the MLT query
     *
     * @param fieldNames the field names that will be used
     */
    public void setMatchFieldNames(String[] fieldNames) {
        this.matchFieldNames = fieldNames;
    }

    /**
     * Sets the field names that must match the target document in the MLT query
     *
     * @param fieldNames the field names that will be used
     */
    public void setDifferentFieldNames(String[] fieldNames) {
        this.differentFieldNames = fieldNames;
    }

    /*
    * Returns the field names for processing the stream body.
    *
    * @return the field names used when parsing terms from the stream.body parameter
    */
    public String[] getStreamBodyfieldNames() {
        if(streamBodyfieldNames.length == 0){
            // don't potentially return every field by calling the getter
            return fieldNames;
        }
        return streamBodyfieldNames;
    }

    /*
    * Sets the field names used for processing the stream body.
    *
    * @param streamBodyfieldNames the field names used when parsing terms from the stream.body parameter
    */
    public void setStreamBodyfieldNames(String[] streamBodyfieldNames) {
        this.streamBodyfieldNames = streamBodyfieldNames;
    }

    /*
    * Gets the field names used for processing the stream head.
    *
    * @return the field names used when parsing terms from the stream.head parameter
    */
    public String[] getStreamHeadfieldNames() {
        if(streamHeadfieldNames.length == 0){
            return fieldNames;
        }
        return streamHeadfieldNames;
    }

    /*
    * Sets the field names used for processing the stream.head parameter.
    *
    * @param streamHeadfieldNames the field names used when parsing terms from the stream.head parameter
    */
    public void setStreamHeadfieldNames(String[] streamHeadfieldNames) {
        this.streamHeadfieldNames = streamHeadfieldNames;
    }

    /*
    * Gets the stream.head value, if specified. This is a string to be parsed if the q parameter is null
    * (assumes a document stream as input from stream.body and optionally from stream.head)
    *
    * @return stream.head value
    */
    public String getStreamHead() {
        return streamHead;
    }

    /*
    * Sets the stream.head value, if specified. This is a string to be parsed if the q parameter is null
    * (assumes a document stream as input from stream.body and optionally from stream.head)
    *
    * @param streamHead stream.head value
    */
    public void setStreamHead(String streamHead) {
        this.streamHead = streamHead;
    }


    /**
     * Returns the minimum word length below which words will be ignored. Set this to 0 for no
     * minimum word length. The default is {@link #DEFAULT_MIN_WORD_LENGTH}.
     *
     * @return the minimum word length below which words will be ignored.
     */
    public int getMinWordLen() {
        return minWordLen;
    }

    /**
     * Sets the minimum word length below which words will be ignored.
     *
     * @param minWordLen the minimum word length below which words will be ignored.
     */
    public void setMinWordLen(int minWordLen) {
        this.minWordLen = minWordLen;
    }

    /**
     * Returns the maximum word length above which words will be ignored. Set this to 0 for no
     * maximum word length. The default is {@link #DEFAULT_MAX_WORD_LENGTH}.
     *
     * @return the maximum word length above which words will be ignored.
     */
    public int getMaxWordLen() {
        return maxWordLen;
    }

    /**
     * Sets the maximum word length above which words will be ignored.
     *
     * @param maxWordLen the maximum word length above which words will be ignored.
     */
    public void setMaxWordLen(int maxWordLen) {
        this.maxWordLen = maxWordLen;
    }

    /**
     * Set the set of stopwords.
     * Any word in this set is considered "uninteresting" and ignored.
     * Even if your Analyzer allows stopwords, you might want to tell the MoreLikeThis code to ignore them, as
     * for the purposes of document similarity it seems reasonable to assume that "a stop word is never interesting".
     *
     * @param stopWords set of stopwords, if null it means to allow stop words
     * @see #getStopWords
     */
    public void setStopWords(Set<?> stopWords) {
        this.stopWords = stopWords;
    }

    /**
     * Get the current stop words being used.
     *
     * @see #setStopWords
     */
    public Set<?> getStopWords() {
        return stopWords;
    }


    /**
     * Returns the maximum number of query terms that will be included in any generated query.
     * The default is {@link #DEFAULT_MAX_QUERY_TERMS_PER_FIELD}.
     *
     * @return the maximum number of query terms that will be included in any generated query.
     */
    public int getMaxQueryTermsPerField() {
        return maxQueryTermsPerField;
    }

    /**
     * Sets the maximum number of query terms that will be included in any generated query.
     *
     * @param maxQueryTermsPerField the maximum number of query terms that will be included in any
     * generated query.
     */
    public void setMaxQueryTermsPerField(int maxQueryTermsPerField) {
        this.maxQueryTermsPerField = maxQueryTermsPerField;
    }

    /**
     * @return The maximum number of tokens to parse in each example doc field that is not stored with TermVector support
     * @see #DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD
     */
    public int getMaxNumTokensParsedPerField() {
        return maxNumTokensParsedPerField;
    }

    /**
     * @param i The maximum number of tokens to parse in each example doc field that is not stored with TermVector support
     */
    public void setMaxNumTokensParsedPerField(int i) {
        maxNumTokensParsedPerField = i;
    }

    /**
     * Gets the field level boosts specified in the request
     *
     * @return The field level boosts specified in the request
     */
    public Map<String, Float> getBoostFields() {
        return this.boostFields;
    }

    private float getFieldBoost(String fieldName) {
        Float boost = this.boostFields.get(fieldName);
        return boost == null? 1.0f: boost;
    }

    private float getStreamFieldBoost(String fieldName) {
        Float streamBodyBoost = this.streamBoostFields.get(fieldName);
        if(streamBodyBoost == null)
        {
            streamBodyBoost = this.boostFields.get(fieldName);
        }
        return streamBodyBoost == null? 1.0f: streamBodyBoost;
    }

    /**
     * Sets the field level boosts
     *
     * @param boostFields The field level boosts specified in the request
     */
    public void setBoostFields(Map<String, Float> boostFields) {
        this.boostFields = boostFields;
    }

    /**
     * Sets the field level boosts
     *
     * @param boostFields The field level boosts specified in the request
     */
    public void setStreamBoostFields(Map<String, Float> boostFields) {
        this.streamBoostFields = boostFields;
    }

    /**
     * Gets the payload fields, if specified
     *
     * @return array of payload fields
     */
    public String[] getPayloadFields() {
        String[] arr = new String[this.payloadFields.size()];
        return this.payloadFields.toArray(arr);
    }

    /**
     * Sets the payload fields. These fields use the stored payload value to apply a multiplicative boost to the term values
     *
     * @param payloadFields the array of payload field names
     */
    public void setPayloadFields(String[] payloadFields) {
        if(payloadFields == null) {
            return;
        }
        for(String fieldname: payloadFields){
            this.payloadFields.add(fieldname.trim().toLowerCase());
        }
    }

    /**
     * Return a query that will return docs queryFromDocuments the passed lucene document ID.
     *
     * @param docNums the documentIDs of the lucene docs to generate the 'More Like This" query for.
     * @return a query that will return docs queryFromDocuments the passed lucene document ID.
     */
    public MLTResult like(List<Integer> docNums) throws IOException {

        Map<String,Map<String, Flt>> fieldTermFreq = new HashMap<String, Map<String, Flt>>();
        Map<String,Map<String, Flt>> mustMatchTerms = new HashMap<String, Map<String, Flt>>();
        Map<String,Map<String, Flt>> mustNOTMatchTerms = new HashMap<String, Map<String, Flt>>();
        // don't go over duplicate documents
        for(Integer docNum: new HashSet<Integer>(docNums)){
            retrieveTerms(docNum, getFieldNames(), fieldTermFreq);
            retrieveTerms(docNum, getMatchFieldNames(), mustMatchTerms);
            retrieveTerms(docNum, getDifferentFieldNames(), mustNOTMatchTerms);
        }

        MLTResult mltResult = buildQueryFromFieldTermFrequencies(fieldTermFreq, false);
        if(mustMatchTerms.size() > 0){
            mltResult.setMustMatchQuery(buildMustMatchQuery(mustMatchTerms, true));
        }
        if(mustNOTMatchTerms.size() > 0){
            mltResult.setMustNOTMatchQuery(buildMustMatchQuery(mustNOTMatchTerms, false));
        }
        return mltResult;
    }

    /**
     * Return a query that will return docs queryFromDocuments the passed Reader.
     * Used by MoreLikethisQuery.
     *
     * @param fields a list of fields to use to process the document stream
     * @param reader a stream reader for the document stream (from the stream.body parameter)*
     * @return a query that will return docs queryFromDocuments the passed Reader.
     */
    public MLTResult like(String[] fields, Reader reader) throws IOException {

        return like(null, fields, reader);
    }

    /**
     * Return a query that will return docs queryFromDocuments the passed Reader.
     *
     * @param reader a stream reader for the document stream (from the stream.body parameter)
     * @return a query that will return docs queryFromDocuments the passed Reader.
     */
    public MLTResult like(Reader reader) throws IOException {

        return like(getStreamHeadfieldNames(), getStreamBodyfieldNames(), reader);
    }

    private MLTResult like(String[] streamHeadfields, String[] streamBodyfields, Reader reader) throws IOException {

        if(streamBodyfields == null){
            throw new UnsupportedOperationException(
                    String.format("To use MoreLikeThis to process a document stream, a field list must be specified "
                                + "using either the %s parameter or the %s parameter",
                        MoreLikeThisParams.SIMILARITY_FIELDS, MoreLikeThisParams.STREAM_BODY_FL));
        }

        Map<String,Map<String, Flt>> fieldTermFreq = new HashMap<String, Map<String, Flt>>();
        String streamBody = org.apache.commons.io.IOUtils.toString(reader);
        for(String fieldName: streamBodyfields){
            Map<String, Flt> words = new HashMap<String, Flt>();
            fieldTermFreq.put(fieldName, words);
            addTermWeights(new StringReader(streamBody), words, fieldName);
        }
        if(getStreamHead() != null){
            if(streamHeadfields == null){
                throw new UnsupportedOperationException(
                        String.format("To use MoreLikeThis to process a document stream using the stream.head as input,"
                                    +"a field list must be specified using either the %s parameter or the %s parameter",
                        MoreLikeThisParams.SIMILARITY_FIELDS, MoreLikeThisParams.STREAM_HEAD_FL));
            }
            for(String fieldName: streamHeadfields){
                Map<String, Flt> words = null;
                if(fieldTermFreq.containsKey(fieldName)) {
                    words = fieldTermFreq.get(fieldName);
                }
                else{
                    words = new HashMap<String, Flt>();
                    fieldTermFreq.put(fieldName, words);
                }
                addTermWeights(new StringReader(getStreamHead()), words, fieldName);
            }
        }
        return buildQueryFromFieldTermFrequencies(fieldTermFreq, true);
    }

    private MLTResult buildQueryFromFieldTermFrequencies(Map<String, Map<String, Flt>> fieldTermFreq, boolean contentStreamQuery) throws IOException {
        BooleanQuery query = new BooleanQuery();
        List<MLTTerm> interestingTerms = new ArrayList<MLTTerm>();
        MLTResult mltResult = new MLTResult(interestingTerms, query);

        for(String fieldName: fieldTermFreq.keySet()){
            Map<String,Flt> words = fieldTermFreq.get(fieldName);
            PriorityQueue<MLTTerm> queue = createQueue(fieldName, words, contentStreamQuery);

            MLTResult fieldMltResult = buildQueryForField(fieldName, queue, query, contentStreamQuery);
            mltResult.mltTerms.addAll(fieldMltResult.mltTerms);
        }
        return mltResult;
    }

    /**
     * Build the More queryFromDocuments query from a PriorityQueue and an initial Boolean query
     */
    private MLTResult buildQueryForField(String fieldName, PriorityQueue<MLTTerm> q, BooleanQuery query, boolean contentStreamQuery) {

        List<MLTTerm> interestingTerms = new ArrayList<MLTTerm>();
        int qterms = 0;
        int maxTerms = maxQueryTermsPerField;
        if(maxTerms <= 0){
            maxTerms = Integer.MAX_VALUE;
        }

        // to store temporary query so we can later normalize
        BooleanQuery tmpQuery = new BooleanQuery();
        double sumQuaredBoost = 0.0f;
        MLTTerm cur;
        // build temp subquery while computing vector length of query for fields from boosts
        while ((cur = q.pop()) != null) {

            Query tq = null;
            final Term term = new Term(cur.getFieldName(), cur.getWord());
            if(isPayloadField(cur.getFieldName())){
                tq = new PayloadTermQuery(term, new AveragePayloadFunction(), true);
            }
            else{
                tq = new TermQuery(term);
            }

            if (boost) {
                float boost = cur.getScore();
                tq.setBoost(boost);
                sumQuaredBoost += boost * boost;
            }
            else{
                sumQuaredBoost += 1.0;
            }

            try {
                tmpQuery.add(tq, BooleanClause.Occur.SHOULD);
                interestingTerms.add(cur);
                qterms++;
            }
            catch (BooleanQuery.TooManyClauses ignore) {
                break;
            }

            if (qterms >= maxTerms) {
                break;
            }
        }

        double vectorLength = Math.sqrt(sumQuaredBoost);
        if(vectorLength <= 0.0){
            return new MLTResult(interestingTerms, query);
        }

        buildBoostedNormalizedQuery(fieldName, tmpQuery, query, vectorLength, contentStreamQuery);
        return new MLTResult(interestingTerms, query);
    }

    private void buildBoostedNormalizedQuery(String fieldName, BooleanQuery tmpQuery, BooleanQuery outQuery, double vectorLength, boolean contentStreamQuery) {
        double denominator = (this.isNormalizeFieldBoosts()? vectorLength : 1.0d);
        float termBoost = 0.0f;
        if(contentStreamQuery){
            termBoost = this.getStreamFieldBoost(fieldName);
        }
        else {
            termBoost = this.getFieldBoost(fieldName);
        }

        // only add in field boost here as we need to normalize first
        for(BooleanClause clause: tmpQuery.clauses()){
            Query termQuery = (clause).getQuery();
            // note that this needs to be applied here, so that the length of the query equals the term boost

            Float boost = null;
            if(this.isNormalizeFieldBoosts()){
                boost = ((float) (termBoost * termQuery.getBoost() / denominator));
            }
            else{
                boost = termBoost * termQuery.getBoost();
            }
            termQuery.setBoost(boost);
            outQuery.add(termQuery, BooleanClause.Occur.SHOULD);
        }
    }

    /**
     * Create a PriorityQueue from a word->tf map.
     *
     * @param words a map of words keyed on the word(String) with Int objects as the values.
     */
    private PriorityQueue<MLTTerm> createQueue(String fieldName, Map<String, Flt> words, boolean contentStreamQuery) throws IOException {
        // have collected all words in doc and their freqs
        int numDocs = ir.numDocs();
        FreqQ res = new FreqQ(words.size()); // will order words by score

        for (String word : words.keySet()) { // for every word
            if(word.trim().length() == 0)
            {
                continue;
            }

            float tf = words.get(word).x; // term freq in the source doc
            float originalTf = tf;

            if (minTermFreq > 0 && tf < minTermFreq) {
                continue; // filter out words that don't occur enough times in the source
            }

            int docFreq = ir.docFreq(new Term(fieldName, word));
            if (minDocFreq > 0 && docFreq < minDocFreq) {
                continue; // filter out words that don't occur in enough docs
            }

            //if (docFreq == 0 || docFreq > maxDocFreq) {
            if (docFreq > maxDocFreq) {
                continue; // filter out words that occur in too many docs
            }

            float idf = similarity.idf(docFreq, numDocs);
            // log it, after the validation checks
            float score = 0;
            if(isLogTf()){
                tf = (float)Math.log(tf + 1.0d);
            }
            score = (tf * idf);

            MLTTerm mltTerm;
            if(isPayloadField(fieldName)){
                mltTerm = new MLTTerm(
                        word,        // the word
                        fieldName,   // the field name
                        score,       // overall score
                        tf,          // tf
                        idf,         // idf
                        docFreq,     // freq in all docs
                        this.isLogTf(),
                        contentStreamQuery? this.getStreamFieldBoost(fieldName): this.getFieldBoost(fieldName),
                        originalTf
                );
            }
            else{
                mltTerm = new MLTTerm(
                        word,        // the word
                        fieldName,   // the field name
                        score,       // overall score
                        tf,          // tf
                        idf,         // idf
                        docFreq,     // freq in all docs
                        this.isLogTf(),
                        contentStreamQuery? this.getStreamFieldBoost(fieldName): this.getFieldBoost(fieldName)
                );
            }
            res.insertWithOverflow(mltTerm);
        }
        return res;
    }

    private Query buildMustMatchQuery(Map<String,Map<String, Flt>> fieldValues, boolean match){
        BooleanQuery query = new BooleanQuery();
        for(Map.Entry<String,Map<String,Flt>> entry: fieldValues.entrySet()){
            String fieldName = entry.getKey();
            for(Map.Entry<String,Flt> fieldValue: entry.getValue().entrySet()){
                String value = fieldValue.getKey();
                TermQuery tq = new TermQuery(new Term(fieldName, value));
                if(match) {
                    query.add(tq, BooleanClause.Occur.MUST);
                }
                else{
                    query.add(tq, BooleanClause.Occur.MUST_NOT);
                }
            }
        }
        return query;
    }

    /**
     * Describe the parameters that control how the "more queryFromDocuments this" query is formed.
     */
    public String describeParams() {
        StringBuilder sb = new StringBuilder();
        sb.append("\t").append("maxQueryTermsPerField  : ").append(maxQueryTermsPerField).append("\n");
        sb.append("\t").append("minWordLen     : ").append(minWordLen).append("\n");
        sb.append("\t").append("maxWordLen     : ").append(maxWordLen).append("\n");
        sb.append("\t").append("fieldNames     : ");
        String delim = "";
        for (String fieldName : getFieldNames()) {
            sb.append(delim).append(fieldName);
            delim = ", ";
        }
        sb.append("\n");
        sb.append("\t").append("boost          : ").append(boost).append("\n");
        sb.append("\t").append("minTermFreq    : ").append(minTermFreq).append("\n");
        sb.append("\t").append("minDocFreq     : ").append(minDocFreq).append("\n");
        return sb.toString();
    }

    /**
     * Find words for a more-queryFromDocuments-this query former.
     *
     * @param docNum the id of the lucene document from which to find terms
     * @param fields the list of field of the lucene document from which to extract terms
     * @param fieldToTermFreqMap data structure to populate with term frequencies
     */
    public Map<String, Map<String, Flt>> retrieveTerms(int docNum, String[] fields, Map<String, Map<String, Flt>> fieldToTermFreqMap) throws IOException {

        if(fieldToTermFreqMap == null) {
            fieldToTermFreqMap = new HashMap<String, Map<String, Flt>>();
        }

        if(fields == null || fields.length == 0){
            return fieldToTermFreqMap;
        }

        final Fields vectors = ir.getTermVectors(docNum);
        final Document document = ir.document(docNum);

        for (String fieldName : fields) {

            Map<String, Flt> termFreqMap = null;
            if(fieldToTermFreqMap.containsKey(fieldName)){
                termFreqMap = fieldToTermFreqMap.get(fieldName);
            }
            else{
                termFreqMap = new HashMap<String, Flt>();
                fieldToTermFreqMap.put(fieldName, termFreqMap);
            }

            Terms vector = null;
            if (vectors != null) {
                vector = vectors.terms(fieldName);
            }

            // field does not store term vector info
            // even if term vectors enabled, need to extract payload from regular field reader
            if (vector == null || isPayloadField(fieldName)) {
                IndexableField docFields[] = document.getFields(fieldName);
                for (IndexableField field : docFields) {
                    final String stringValue = field.stringValue();
                    if (stringValue != null) {
                        addTermWeights(new StringReader(stringValue), termFreqMap, fieldName);
                    }
                }
            } else {
                addTermWeights(termFreqMap, vector);
            }
        }

        return fieldToTermFreqMap;
    }

    /**
     * Adds terms and frequencies found in vector into the Map termWeightMap
     *
     * @param termWeightMap a Map of terms and their weights
     * @param vector List of terms and their weights for a doc/field
     */
    private void addTermWeights(Map<String, Flt> termWeightMap, Terms vector) throws IOException {
        final TermsEnum termsEnum = vector.iterator();
        CharsRefBuilder spare = new CharsRefBuilder();
        BytesRef text;
        while((text = termsEnum.next()) != null) {
            spare.copyUTF8Bytes(text);
            final String term = spare.toString();
            if (isNoiseWord(term)) {
                continue;
            }
            final int freq = (int) termsEnum.totalTermFreq();

            //TODO try this
            //termsEnum.docsAndPositions(.....).getPayload()

            // increment frequency
            Flt cnt = termWeightMap.get(term);
            if (cnt == null) {
                termWeightMap.put(term, new Flt(freq));
            } else {
                cnt.x += freq;
            }
        }
    }

    /**
     * Adds term weights found by tokenizing text from reader into the Map words
     *
     * @param reader a source of text to be tokenized
     * @param termWeightMap a Map of terms and their weights
     * @param fieldName Used by analyzer for any special per-field analysis
     */
    private void addTermWeights(Reader reader, Map<String, Flt> termWeightMap, String fieldName)
            throws IOException {
        if (analyzer == null) {
            throw new UnsupportedOperationException("To use MoreLikeThis without " +
                    "term vectors, you must provide an Analyzer");
        }

        TokenStream ts = analyzer.tokenStream(fieldName, reader);
        try {
            int tokenCount = 0;
            // for every token
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            PayloadAttribute payloadAttr = ts.addAttribute(PayloadAttribute.class);
            TypeAttribute typeAttr = ts.addAttribute(TypeAttribute.class);

            ts.reset();
            while (ts.incrementToken()) {
                String word = termAtt.toString();
                tokenCount++;
                if (tokenCount > maxNumTokensParsedPerField) {
                    break;
                }
                if(word.trim().length() == 0){
                    continue;
                }
                if (isNoiseWord(word)) {
                    continue;
                }

                BytesRef payload = payloadAttr.getPayload();
                float tokenWeight = 1.0f; // 1.0 or payload if set and a payload field
                if(isPayloadField(fieldName) && payload != null){
                    tokenWeight = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
                }
                // increment frequency
                Flt termWeight = termWeightMap.get(word);
                if (termWeight == null) {
                    termWeightMap.put(word, new Flt(tokenWeight));
                } else {
                    termWeight.x += tokenWeight;
                }
            }
            ts.end();
        } finally {
            IOUtils.closeWhileHandlingException(ts);
        }
    }

    /**
     * determines if the passed term is likely to be of interest in "more queryFromDocuments" comparisons
     *
     * @param term The word being considered
     * @return true if should be ignored, false if should be used in further analysis
     */
    private boolean isNoiseWord(String term) {
        int len = term.length();
        if (minWordLen > 0 && len < minWordLen) {
            return true;
        }
        if (maxWordLen > 0 && len > maxWordLen) {
            return true;
        }
        return stopWords != null && stopWords.contains(term);
    }

    private boolean isPayloadField(String fieldName){
        return this.payloadFields.contains(fieldName.trim().toLowerCase());
    }

    /**
     * PriorityQueue that orders words by score.
     */
    private static class FreqQ extends PriorityQueue<MLTTerm> {
        FreqQ(int s) {
            super(s);
        }

        @Override
        protected boolean lessThan(MLTTerm aa, MLTTerm bb) {
            return aa.getScore() > bb.getScore();
        }
    }

    /**
     * Use for frequencies and to avoid renewing Integers.
     */

    private static class Flt {
        float x;

        Flt(float x) {
            this.x = x;
        }
    }
}