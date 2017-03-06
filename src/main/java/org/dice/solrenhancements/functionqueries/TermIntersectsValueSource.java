package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.dice.solrenhancements.TermExtractionHelper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by simon.hughes on 2/20/17.
 */
public class TermIntersectsValueSource extends ValueSource {

    private final String indexedField;
    private final Analyzer analyzer;
    private final Set<String> valuesToIntersect;
    private final String similarity;

    public TermIntersectsValueSource(String indexedField, Analyzer analyzer, Set<String> valuesToIntersect, String similarity) {
        this.indexedField = indexedField;
        this.analyzer = analyzer;
        this.valuesToIntersect = valuesToIntersect;
        this.similarity = similarity;
    }

    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException {
        final IndexReader ir = readerContext.reader();
        final String indexedField = this.indexedField;

        return new FloatDocValues(this) {
            public float floatVal(int docNum) {
                if(valuesToIntersect.size() == 0){
                    return 0;
                }

                HashSet<String> fieldValues = null;
                try {
                    final Fields vectors= ir.getTermVectors(docNum);
                    if (vectors != null) {
                        if (vectors != null) {
                            Terms vector = vectors.terms(indexedField);
                            if (vector != null) {
                                fieldValues = new HashSet<String>(TermExtractionHelper.getTermsFromTermVectorField(vector));
                            }
                        }
                    }  
                    if(fieldValues == null){
                        Set<String> fields = new HashSet<String>();
                        fields.add(indexedField);
                        Document d = ir.document(docNum, fields);

                        IndexableField field = d.getField(indexedField);
                        if (field != null) {
                            fieldValues = new HashSet<String>(TermExtractionHelper.getTermsFromField(analyzer, field));
                        }
                    }
                    if(fieldValues == null || fieldValues.size() == 0){
                        return 0;
                    }

                    // store field size before modifying set
                    int fieldSize = fieldValues.size();
                    if(similarity.equals(SimilarityType.JACCARD)){
                        HashSet<String> union = new HashSet<String>(fieldValues);
                        union.addAll(valuesToIntersect);

                        // intersection
                        fieldValues.retainAll(valuesToIntersect);
                        float intersection = fieldValues.size();
                        // no divide by zero as asserted field size is > 0, thus union > 0
                        return intersection / union.size();
                    }

                    fieldValues.retainAll(valuesToIntersect);
                    float intersectionSize = fieldValues.size();
                    if(intersectionSize == 0){
                        return 0;
                    }

                    if(similarity.equals(SimilarityType.DOC_LEN)){
                        return intersectionSize / fieldSize;
                    }

                    if(similarity.equals(SimilarityType.PARAM_LEN)){
                        return intersectionSize / valuesToIntersect.size();
                    }
                    else if(similarity.equals(SimilarityType.DICE)) {
                        return (2*intersectionSize) / (fieldSize + valuesToIntersect.size());
                    }

                    return 0;
                }
                catch(Exception e){
                    e.printStackTrace();
                }
                return 0;
            };
        };
    };

    public String name() {
        return "termintersect";
    }

    @Override
    public String description() {
        return name() + '(' + indexedField + ')';
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
