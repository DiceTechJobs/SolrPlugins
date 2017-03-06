package org.dice.solrenhancements.jointprobability.searchcomponents;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.*;
import org.dice.solrenhancements.JarVersion;
import org.dice.solrenhancements.jointprobability.JointCounts;
import org.dice.solrenhancements.jointprobability.JointProbabilityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by simon.hughes on 1/15/16.
 */
public class NaiveBayesComponent extends JointProbabilityComponentBase {

    private static final Logger log = LoggerFactory.getLogger( NaiveBayesComponent.class );

    private final String CACHE_DURATION = getPrefix() + "." + "cache_duration_ms";
    private final String CLASS_FIELD = getPrefix() + "." + "class";
    private final String TOPN  = getPrefix() + "." + "topn";
    private final String ALPHA = getPrefix() + "." + "alpha";

    private final String BINARY = getPrefix() + "." + "binary";
    private final String LOG_TFS = getPrefix() + "." + "logtfs";
    private final String INCLUDE_EXISTING = getPrefix() + "." + "include_existing";

    private final String BUILD = getPrefix() + "." + "build";
    private final String BUILD_QRY = BUILD + ".q";

    private String classField = null;
    private String dfltBuildQuery = "*:*";
    private int cacheDuration = -1;
    private int dfltTopN = 1;
    private long lastRefresh = -1L;
    private float dfltAlpha = 0.01f;
    private boolean dfltBinary = false;
    private boolean dfltLogTfs = false;
    private boolean dflIncludeExisting = false;

    private JointProbabilityModel model = null;
    private Set<String> classValues = null;

    @Override
    public void init( NamedList args) {
        super.init(args);
        Object oCacheDuration = args.get(CACHE_DURATION);
        if(oCacheDuration != null && oCacheDuration instanceof Integer){
            cacheDuration = (Integer)oCacheDuration;
        }

        Object topN = args.get(TOPN);
        if(topN != null && topN instanceof Integer){
            this.dfltTopN = (Integer)topN;
        }

        Object classField = args.get(CLASS_FIELD);
        if(classField != null){
            this.classField = classField.toString();
        }

        Object buildQuery = args.get(BUILD_QRY);
        if(buildQuery != null){
            this.dfltBuildQuery = buildQuery.toString();
        }

        Object alpha = args.get(ALPHA);
        if(alpha != null && alpha instanceof Float){
            this.dfltAlpha = (Float)alpha;
        }

        Object binary = args.get(BINARY);
        if(binary != null && binary instanceof Boolean){
            this.dfltBinary = (Boolean) binary;
        }

        Object logTfs = args.get(LOG_TFS);
        if(logTfs != null && logTfs instanceof Boolean){
            this.dfltLogTfs = (Boolean) logTfs;
        }

        Object includeExisting = args.get(INCLUDE_EXISTING);
        if(includeExisting != null && includeExisting instanceof Boolean){
            this.dflIncludeExisting= (Boolean) includeExisting;
        }
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        if (isEnabled(rb)) {
            long startTime = System.currentTimeMillis();
            long timeSinceRefresh = startTime - lastRefresh;

            SolrParams params = rb.req.getParams();
            Integer minCount = getMinCountParameter(params);
            Integer limit = getLimitParameter(params);

            if (classField == null) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        String.format("Unable to determine class field due to missing parameter: %s", getPrefix() + "." + CLASS_FIELD));
            }

            boolean build = getBuildParameter(params);
            String buildQuery = getBuildQuery(params);
            float alpha = getAlpha(params);

            refreshCache(rb, startTime, timeSinceRefresh, params, minCount, limit, build, buildQuery, alpha);
        }
    }

    private void refreshCache(ResponseBuilder rb, long startTime, long timeSinceRefresh, SolrParams params,
                              Integer minCount, Integer limit, boolean build, String buildQuery, float alpha) throws IOException {

        if (shouldRefreshCache(build, timeSinceRefresh)) {

            // need to update these on a build, as model must match these parameters
            setFieldsParameter(params);
            setClassField(params);

            refreshModel(rb, buildQuery, minCount, limit, classField, fields, alpha);
            long duration = this.lastRefresh - startTime;
            if (build) {
                rb.rsp.add("command", "build");
            }
            NamedList<Object> stats = new NamedList<Object>();
            stats.add("BuildTime", duration);
            stats.add("Joints", this.model.getNumJointCounts());
            stats.add("Priors", this.model.getNumPriors());
            rb.rsp.add(BUILD, stats);
        }

    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        if(isEnabled(rb)){
            long startTime = System.currentTimeMillis();

            SolrParams params = rb.req.getParams();

            int topN = getTopN(params);
            boolean binary = getBinary(params);
            boolean logTfs = getLogTfs(params);
            boolean includeExisting = getIncludeExisting(params);

            final SolrIndexSearcher searcher = rb.req.getSearcher();
            IndexReader ir = searcher.getIndexReader();
            Analyzer analyzer = searcher.getSchema().getIndexAnalyzer();

            DocListAndSet docs   = rb.getResults();
            DocIterator iterator = docs.docList.iterator();
            String uniqueKeyField = searcher.getSchema().getUniqueKeyField().getName();

            NamedList<NamedList<Double>> topPredictions = new NamedList<NamedList<Double>>();
            while(iterator.hasNext()) {
                int docNum = iterator.nextDoc();

                Map<String, Map<String,Integer>> tf = getFieldTermFrequencyCounts(fields, ir, analyzer, docNum);
                NamedList<Double> predictions = predict(tf, topN, binary, logTfs, includeExisting);

                String uniqueFieldValue = getUniqueKeyFieldValue(ir, analyzer, uniqueKeyField, docNum);
                topPredictions.add(String.format("%s:%s", uniqueKeyField, uniqueFieldValue), predictions);
            }

            long duration = System.currentTimeMillis() - startTime;

            NamedList<Object> results = new NamedList<Object>();
            results.add("Time", duration);
            results.add("values", topPredictions);
            rb.rsp.add(getPrefix(), results);
        }
    }

    private NamedList<Double> predict(Map<String, Map<String,Integer>> xs, int topN,
                                     boolean binary, boolean logTfs, boolean includeExisting){

        // don't predict tokens that are in the document already if NOT includeExisting
        // and one of the training fields is also the class field (using it as a true generative model)
        boolean removeExisting = !includeExisting && xs.containsKey(this.classField);

        final LargestFirstPriorityQueue queue = new LargestFirstPriorityQueue(topN);
        //TODO if predicting a value that's also in the XS, we need to ignore values already present
        for(String classToken: this.classValues){
            // initialize to the prior
            addScoreForClass(xs, binary, logTfs, removeExisting, queue, classToken);
        }

        ClassScore next = null;
        final NamedList<Double> topScores = new NamedList<Double>();
        while((next = queue.pop()) != null){
            topScores.add(next.value, next.score);
        }
        return topScores;
    }

    private void addScoreForClass(Map<String, Map<String, Integer>> xs,
                                  boolean binary, boolean logTfs, boolean removeExisting,
                                  LargestFirstPriorityQueue queue, String classToken) {
        // use addition in the log domain to avoid numerical underflow
        double score = Math.log(this.model.getPrior(this.classField, classToken));
        for(Map.Entry<String, Map<String,Integer>> fieldCounts: xs.entrySet()){

            final String fieldName = fieldCounts.getKey();
            final Map<String, Integer> tokenCounts = fieldCounts.getValue();
            if(removeExisting && fieldName.equals(classField) && tokenCounts.containsKey(classToken)){
                return;
            }

            for(Map.Entry<String, Integer> tokenCount: tokenCounts.entrySet()){
                String token = tokenCount.getKey();
                double termFreq = tokenCount.getValue();
                if(binary){
                    termFreq = 1d;
                }
                else if(logTfs){
                    termFreq = Math.log(termFreq + 1);
                }
                if(termFreq == 0){
                    continue;
                }
                score += Math.log(termFreq * this.model.getConditional(fieldName, token, this.classField, classToken));
            }
        }
        queue.insertWithOverflow(new ClassScore(classToken, score));
    }

    private String getUniqueKeyFieldValue(IndexReader ir, Analyzer analyzer, String uniqueKeyField, int docNum) throws IOException {
        Map<String, Map<String,Integer>> mapUniqueValue = getFieldTermFrequencyCounts(new String[]{uniqueKeyField}, ir, analyzer, docNum);
        if(mapUniqueValue.size() == 0){
            return "-1";
        }
        Map<String,Integer> tf = mapUniqueValue.get(uniqueKeyField);
        if(tf.size() == 0){
            return "-1";
        }
        return tf.keySet().iterator().next();
    }

    private Map<String, Map<String,Integer>> getFieldTermFrequencyCounts(
            String[] fields, IndexReader ir, Analyzer analyzer, int docNum) throws IOException {

        final Fields vectors = ir.getTermVectors(docNum);
        final Document document = ir.document(docNum);


        Map<String, Map<String,Integer>> fieldValues = new HashMap<String, Map<String,Integer>>();

        for (String fieldName: fields) {

            Terms vector = null;
            if (vectors != null) {
                vector = vectors.terms(fieldName);
            }

            // field does not store term vector info
            // even if term vectors enabled, need to extract payload from regular field reader
            if (vector == null) {
                IndexableField docFields[] = document.getFields(fieldName);
                for (IndexableField field : docFields) {
                    final String stringValue = field.stringValue();
                    if (stringValue != null) {
                        Map<String,Integer> tf = readTokensFromField(analyzer, field, fieldName);
                        if(tf.size() > 0){
                            fieldValues.put(fieldName, tf);
                        }
                    }
                }

            } else {
                Map<String,Integer> tf = readTokensFromVector(vector);
                if(tf.size() > 0){
                    fieldValues.put(fieldName, tf);
                }
            }
        }
        return fieldValues;
    }

    private Map<String,Integer> readTokensFromVector(Terms vector) throws IOException {
        Map<String,Integer> tokenCounts = new HashMap<String,Integer>();
        final TermsEnum termsEnum = vector.iterator();
        CharsRefBuilder spare = new CharsRefBuilder();
        BytesRef text;
        while((text = termsEnum.next()) != null) {
            spare.copyUTF8Bytes(text);
            final String term = spare.toString();
            Integer count = tokenCounts.get(term);
            tokenCounts.put(term, count==null? 1: count+1 );
        }
        return tokenCounts;
    }

    private Map<String,Integer> readTokensFromField(Analyzer analyzer, IndexableField ixFIeld, String fieldName) throws IOException {
        Map<String,Integer> tokenCounts = new HashMap<String,Integer>();
        final String stringValue = ixFIeld.stringValue();
        if (stringValue != null) {
            TokenStream ts = analyzer.tokenStream(fieldName, new StringReader(stringValue));
            try {
                // for every token
                CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
                ts.reset();
                while (ts.incrementToken()) {
                    final String token = termAtt.toString();
                    Integer count = tokenCounts.get(token);
                    tokenCounts.put(token, count == null? 1: count+1);
                }
                ts.end();
            } finally {
                IOUtils.closeWhileHandlingException(ts);
            }
        }
        return tokenCounts;
    }

    @Override
    public String getDescription() {
        return "Naive Bayes classifier built on top of solr and lucene";
    }

    @Override
    protected String getPrefix() {
        return "nb";
    }

    private boolean getBuildParameter(SolrParams params) {
        return params.getBool(BUILD, false);
    }

    private void refreshModel(ResponseBuilder rb, final String buildQuery, Integer minCount, Integer limit,
                              String classField, String[] fields, float alpha) throws IOException {
        try {
            // re-structure fields list for the JointCounts class
            String[] jointProbFields = new String[fields.length];
            for(int i = 0; i<fields.length; i++){
                jointProbFields[i] = classField +"," + fields[i];
            }

            DocSet docs = getDocSet(rb, buildQuery);

            // get joint counts
            JointCounts jointProb = new JointCounts(rb.req, docs, minCount, limit, jointProbFields);
            NamedList<Object> counts = jointProb.process();

            // read data structure, convert to probabilities
            JointProbabilityModel newModel = new JointProbabilityModel(counts, alpha);

            // get unique list of class values to predict
            this.classValues = newModel.getFieldValues(classField);

            this.model = newModel;
            // update cache variables
            this.lastRefresh = System.currentTimeMillis();


        } catch (SyntaxError e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }
    }

    private DocSet getDocSet(ResponseBuilder rb, String buildQuery) throws SyntaxError, IOException {
        SolrParams params = rb.req.getParams();
        String defType = params.get(QueryParsing.DEFTYPE, QParserPlugin.DEFAULT_QTYPE);
        QParser parser = QParser.getParser(buildQuery, defType, rb.req);
        Query query = parser.getQuery();

        return rb.req.getSearcher().getDocSet(query);
    }

    private boolean shouldRefreshCache(boolean build, long timeSinceRefresh) {
        return build || timeSinceRefresh >= this.cacheDuration || this.model == null;
    }

    private String getBuildQuery(SolrParams params) {
        return params.get(BUILD_QRY, this.dfltBuildQuery);
    }

    private int getTopN(SolrParams params) {
        return params.getInt(TOPN, this.dfltTopN);
    }

    private void setClassField(SolrParams params) {
        this.classField = params.get(CLASS_FIELD, this.classField);
    }

    private void setFieldsParameter(SolrParams params){
        this.fields = getFieldsParameter(params);
    }

    private Float getAlpha(SolrParams params){
        return params.getFloat(ALPHA, this.dfltAlpha);
    }

    private Boolean getBinary(SolrParams params){
        return params.getBool(BINARY, this.dfltBinary);
    }

    private Boolean getLogTfs(SolrParams params){
        return params.getBool(LOG_TFS, this.dfltLogTfs);
    }

    private Boolean getIncludeExisting(SolrParams params){
        return params.getBool(INCLUDE_EXISTING, this.dflIncludeExisting);
    }

    private class ClassScore {
        public final String value;
        public final double score;

        public ClassScore(String value, double score){
            this.value = value;
            this.score = score;
        }
    }

    private static class LargestFirstPriorityQueue extends org.apache.lucene.util.PriorityQueue<ClassScore> {
        LargestFirstPriorityQueue(int s) {
            super(s);
        }

        @Override
        protected boolean lessThan(ClassScore aa, ClassScore bb) {
            return aa.score < bb.score;
        }
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
