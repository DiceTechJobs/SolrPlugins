package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.IntDocValues;
import org.apache.lucene.util.IOUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by simon.hughes on 6/2/15.
 *
 * DON'T Use this as it is really slow
 */
public class FieldLenValueSource extends ValueSource {

    private final String indexedField;
    private final Analyzer analyzer;

    public FieldLenValueSource(String indexedField, Analyzer analyzer) {

        this.indexedField = indexedField;
        this.analyzer = analyzer;
    }

    public String name() {
        return "fieldlen";
    }

    @Override
    public String description() {
        return name() + '(' + indexedField + ')';
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException{
        final IndexReader ir = readerContext.reader();
        final String indexedField = this.indexedField;

        return new IntDocValues(this) {
            @Override
            public int intVal(int docNum) {

                try{
                    // SH:  Fastest method to do this is if the field has term vectors stored
                    //      else we have to re-analyze the field, which is not efficient
                    // http://stackoverflow.com/questions/3574106/how-to-count-the-number-of-terms-for-each-document-in-lucene-index
                    final Fields vectors = ir.getTermVectors(docNum);
                    if(vectors != null){
                        if (vectors != null) {
                            Terms vector = vectors.terms(indexedField);
                            if(vector != null) {
                                return (int) vector.size();
                            }
                        }
                    }
                } catch(java.io.IOException ex){
                    throw new RuntimeException("caught exception in function " + description()+" while reading term vectors for doc : doc=" + docNum, ex);
                }

                return getFieldLengthFromAnalysisChain(docNum, indexedField, ir);
            }
        };
    }

    private int getFieldLengthFromAnalysisChain(int docNum, String indexedField, IndexReader ir) {
        TokenStream ts = null;
        try {

            Set<String> fields = new HashSet<String>();
            fields.add(indexedField);
            Document d = ir.document(docNum, fields);

            IndexableField field = d.getField(indexedField);
            if(field == null){
                return -1;
            }

            ts = field.tokenStream(analyzer, ts);
            ts.reset();
            int length = 0;
            while (ts.incrementToken()) {
                length++;
            }
            ts.end();
            return length;

        }
        catch (Exception ex)
        {
            throw new RuntimeException("caught exception in function " + description()+" : doc=" + docNum, ex);
        }
        finally {
            if(ts != null)
            {
                IOUtils.closeWhileHandlingException(ts);
            }
        }
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
