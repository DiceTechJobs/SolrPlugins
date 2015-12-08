package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
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
    public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
        final IndexReader ir = readerContext.reader();
        final String indexedField = this.indexedField;

        return new IntDocValues(this) {
            @Override
            public int intVal(int doc) {
                TokenStream ts = null;
                try {
                    //Document d = ir.document(doc);
                    Set<String> fields = new HashSet<String>();
                    fields.add(indexedField);
                    Document d = ir.document(doc, fields);
                    IndexableField field = d.getField(indexedField);
                    if(field == null){
                        return 0;

                    }
                    ts = field.tokenStream(analyzer, ts);

                    int length = 0;
                    ts.reset();
                    while (ts.incrementToken()) {
                        length++;
                    }
                    ts.end();
                    return length;

                }
                catch (Exception ex)
                {
                    throw new RuntimeException("caught exception in function " + description()+" : doc=" + doc, ex);
                }
                 finally {
                    if(ts != null)
                    {
                        IOUtils.closeWhileHandlingException(ts);
                    }
                }
            }
        };
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
