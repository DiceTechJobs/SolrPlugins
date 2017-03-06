package org.dice.solrenhancements.machinelearning;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.dice.helper.DefaultHashTable;
import org.dice.solrenhancements.TermExtractionHelper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Created by simon.hughes on 2/1/17.
 */
public class FeatureExtractor {

    private final IndexReader ir;
    private final String[] fields;
    private Map<String, Map<String, Integer>> featureMap = null;
    private Analyzer analyzer = null;
    private final int minDf;

    public static class LABELS {
        public static final String POSITIVE = "POSITIVE";
        public static final String NEGATIVE = "NEGATIVE";
    }

    public FeatureExtractor(IndexReader ir, String[] fields, Analyzer analyzer, int minDf){
        this.ir = ir;
        this.fields = fields;
        this.analyzer = analyzer;
        this.minDf = minDf;
        this.featureMap = createFeatureMap();
    }

    public FeatureExtractor extractFeaturesFromDocuments(Set<Integer> docIds, String featureLabel) throws IOException {
        this.extractFeaturesFromDocuments(docIds, featureLabel, this.featureMap);
        return this;
    }

    public Map<String, Map<String, Integer>> getFeatureMap(){
        return featureMap;
    }

    public void resetFeatures(){
        this.featureMap = createFeatureMap();
    }

    private final static Map<String, Map<String, Integer>> createFeatureMap(){
        return new DefaultHashTable<String, Map<String, Integer>>(new Supplier<Map<String, Integer>>() {
            public Map<String, Integer> get() {
                return new DefaultHashTable<String, Integer>(new Supplier<Integer>() {
                    public Integer get() {
                        return 0;
                    }
                });
            }
        });
    }

    private void extractFeaturesFromDocuments(Set<Integer> docIds, String featureLabel, Map<String, Map<String,Integer>> featureMap) throws IOException {
        for(Integer id: docIds){
            extractFeaturesFromDocument(id, ir, featureLabel, featureMap);
        }
    }

    private void extractFeaturesFromDocument(int docNum, IndexReader ir, String featureLabel, Map<String, Map<String,Integer>> featureMap) throws IOException {

        if(fields == null || fields.length == 0){
            return;
        }

        final Fields vectors = ir.getTermVectors(docNum);
        final Document document = ir.document(docNum);

        for (String fieldName : fields) {

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
                        List<String> lstTerms = TermExtractionHelper.getTermsFromString(analyzer, fieldName, stringValue);
                        Set<String> newTerms = new HashSet<String>(lstTerms);
                        this.addFeaturesToMap(fieldName, featureLabel, newTerms, featureMap);
                    }
                }
            } else {
                List<String> lstTerms = TermExtractionHelper.getTermsFromTermVectorField(vector);
                Set<String> newTerms = new HashSet<String>(lstTerms);
                this.addFeaturesToMap(fieldName, featureLabel, newTerms, featureMap);
            }
        }
    }

    private void addFeaturesToMap(String fieldName, String featureLabel, Set<String> feats, Map<String, Map<String,Integer>> featureMap) throws IOException {
        for(String term: feats){
            int docFreq = ir.docFreq(new Term(fieldName, term));
            // ignore infrequent terms
            if(docFreq < this.minDf){
                continue;
            }

            //TODO: replace with a custom class?
            String feat = String.format("%s:\"%s\"", fieldName, term);
            // get feature counts
            Map<String, Integer> featureCounts = featureMap.get(feat);
            // update label count
            int count = featureCounts.get(featureLabel) + 1;
            featureCounts.put(featureLabel, count);
        }
    }

}
