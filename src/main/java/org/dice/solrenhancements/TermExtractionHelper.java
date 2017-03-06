package org.dice.solrenhancements;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.IOUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 2/20/17.
 */
public class TermExtractionHelper {

    public static List<String> getTermsFromField(Analyzer analyzer, IndexableField field) throws IOException {
        TokenStream ts = null;
        try {
            ArrayList<String> terms = new ArrayList<String>();

            ts = field.tokenStream(analyzer, ts);
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                terms.add(termAtt.toString());
            }
            ts.end();
            return terms;
        }
        finally {
            if(ts != null){
                IOUtils.closeWhileHandlingException(ts);
            }
        }
    }

    public static List<String> getTermsFromString(Analyzer analyzer, String fieldName, String fieldValue)
            throws IOException {

        final StringReader reader = new StringReader(fieldValue);
        ArrayList<String> terms = new ArrayList<String>();
        TokenStream ts = analyzer.tokenStream(fieldName, reader);
        try {
            // for every token
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String term = termAtt.toString();
                terms.add(term);
            }
            ts.end();
        } finally {
            IOUtils.closeWhileHandlingException(ts);
        }
        return terms;
    }

    public static List<String> getTermsFromTermVectorField(Terms vector) throws IOException {
        ArrayList<String> terms = new ArrayList<String>();

        final TermsEnum termsEnum = vector.iterator();
        CharsRefBuilder spare = new CharsRefBuilder();
        BytesRef text;

        while((text = termsEnum.next()) != null) {
            spare.copyUTF8Bytes(text);
            final String term = spare.toString();
            terms.add(term);
        }
        return terms;
    }
}
