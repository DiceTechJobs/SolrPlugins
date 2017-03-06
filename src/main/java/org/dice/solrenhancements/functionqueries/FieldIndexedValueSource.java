package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.IntDocValues;
import org.apache.lucene.queries.function.docvalues.StrDocValues;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.IOUtils;
import org.dice.solrenhancements.TermExtractionHelper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by simon.hughes on 6/2/15.
 *
 * DON'T Use this as it is really slow
 */
public class FieldIndexedValueSource extends ValueSource {

    public static final String DELIM = "||";
    private final String indexedField;
    private final Analyzer analyzer;

    public FieldIndexedValueSource(String indexedField, Analyzer analyzer) {

        this.indexedField = indexedField;
        this.analyzer = analyzer;
    }

    public String name() {
        return "fieldval";
    }

    @Override
    public String description() {
        return name() + '(' + indexedField + ')';
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException{
        final IndexReader ir = readerContext.reader();
        final String indexedField = this.indexedField;

        return new StrDocValues(this) {
            public String strVal(int doc) {
                return getIndexValuesForField(doc, indexedField, ir);
            };
        };
    }

    private String getIndexValuesForField(int docNum, String indexedField, IndexReader ir) {
        StringBuilder sb = new StringBuilder();

        try {
            if(!tryExtractTermsFromTermVector(docNum, indexedField, ir, sb)) {
                extractTermsFromField(docNum, indexedField, ir, sb);
            }
            String values = sb.toString().trim();
            if(values.length() == 0){
                return "";
            }
            return values.substring(0, sb.length()-DELIM.length());

        }
        catch (Exception ex)
        {
            throw new RuntimeException("caught exception in function " + description()+" : doc=" + docNum, ex);
        }
    }

    private void extractTermsFromField(int docNum, String indexedField, IndexReader ir, StringBuilder sb) throws IOException {

        Set<String> fields = new HashSet<String>();
        fields.add(indexedField);
        Document d = ir.document(docNum, fields);

        IndexableField field = d.getField(indexedField);
        if (field == null) {
            return;
        }

        List<String> terms = TermExtractionHelper.getTermsFromField(analyzer, field);
        for(String word: terms){
            sb.append(word).append(DELIM);
        }
    }

    private boolean tryExtractTermsFromTermVector(int docNum, String indexedField, IndexReader ir, StringBuilder sb) throws IOException {
        final Fields vectors = ir.getTermVectors(docNum);
        if(vectors != null){
            if (vectors != null) {
                Terms vector = vectors.terms(indexedField);
                if(vector == null){
                    return false;
                }
                List<String> terms = TermExtractionHelper.getTermsFromTermVectorField(vector);
                for(String term: terms){
                    sb.append(term).append(DELIM);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
