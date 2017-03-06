package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DivFloatFunction;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by simon.hughes on 6/19/15.
 */
public class TermOverlapValueSourceParser extends ValueSourceParser {

    private static final Logger log = LoggerFactory.getLogger( TermOverlapValueSourceParser.class );

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        FieldInfo fieldInfo = parseTerm(fp);
        if(fieldInfo == null){
            return new DoubleConstValueSource(-1.0d);
        }

        if(fieldInfo.terms.size() == 0){
            return new DoubleConstValueSource(0.0d);
        }

        ValueSource[] matches = new ValueSource[fieldInfo.terms.size()];
        for(int i = 0; i < fieldInfo.terms.size(); i++){
            TermInfo termInfo = fieldInfo.terms.get(i);
            matches[i] = new BinaryTermExistsValueSource(fieldInfo.field, termInfo.indexedBytes.utf8ToString(), fieldInfo.indexedField, termInfo.indexedBytes);
        }

        final SumFloatFunction sum = new SumFloatFunction(matches);
        ValueSource length = null;
        if(fieldInfo.useDocLength){
            Analyzer analyzer = fp.getReq().getSchema().getIndexAnalyzer();
            length = new FieldLenValueSource(fieldInfo.indexedField, analyzer);
        }
        else{
            length = new DoubleConstValueSource(fieldInfo.terms.size());
        }
        return new DivFloatFunction(sum, length);
    }

    private static FieldInfo parseTerm(FunctionQParser fp) throws SyntaxError {
        FieldInfo fieldInfo = new FieldInfo();

        fieldInfo.indexedField = fieldInfo.field = fp.parseArg();
        String textVal = fp.parseArg();
        if(textVal == null || textVal.trim().length() == 0){
            return fieldInfo;
        }

        // allows specifying if the document length (from the index) or the length of the parsed field is used
        if(fp.hasMoreArguments()){
            fieldInfo.useDocLength = fp.parseArg().toLowerCase().equals("true");
        }

        // ensure unique terms are parsed
        Set<String> parsedTerms = new HashSet<String>();
        // need to do analysis on the term

        Analyzer analyzer = fp.getReq().getSchema().getIndexAnalyzer();
        TokenStream ts = analyzer.tokenStream(fieldInfo.field, new StringReader(textVal));
        try {
            // for every token
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();

            while (ts.incrementToken()) {
                String value = termAtt.toString();
                if(parsedTerms.contains(value)){
                    continue;
                }
                parsedTerms.add(value);
                final TermInfo termInfo = new TermInfo(value);
                fieldInfo.terms.add(termInfo);
            }
            ts.end();
        } catch (IOException e) {
            SolrException.log(log, "Exception during debug", e);
            return null;
        }
        finally {
            IOUtils.closeWhileHandlingException(ts);
        }

        return fieldInfo;
    }

    private static class FieldInfo {
        String field;
        String indexedField;
        boolean useDocLength = true;
        List<TermInfo> terms = new ArrayList<TermInfo>();
    }

    private static class TermInfo {
        public final BytesRef indexedBytes;
        public TermInfo(String s){
               indexedBytes = new BytesRef(s);
        }
    }
}
