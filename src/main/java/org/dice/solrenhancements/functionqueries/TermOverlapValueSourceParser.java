package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DivFloatFunction;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by simon.hughes on 6/19/15.
 */
public class TermOverlapValueSourceParser extends ValueSourceParser {
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
        final DoubleConstValueSource length = new DoubleConstValueSource(fieldInfo.terms.size());
        return new DivFloatFunction(sum, length);
    }

    private static FieldInfo parseTerm(FunctionQParser fp) throws SyntaxError {
        FieldInfo fieldInfo = new FieldInfo();

        fieldInfo.indexedField = fieldInfo.field = fp.parseArg();
        String textVal = fp.parseArg();
        if(textVal == null || textVal.trim().length() == 0){
            return fieldInfo;
        }

        FieldType ft = fp.getReq().getSchema().getFieldTypeNoEx(fieldInfo.field);
        if (ft == null) ft = new StrField();

        // ensure unique terms are parsed
        Set<String> parsedTerms = new HashSet<String>();
        if (ft instanceof TextField) {
            // need to do analysis on the term
            Query q = ft.getFieldQuery(fp, fp.getReq().getSchema().getFieldOrNull(fieldInfo.field), textVal);
            if (q instanceof PhraseQuery) {
                Term[] terms = ((PhraseQuery)q).getTerms();
                for(Term term: terms) {
                    final String value = term.text().trim();
                    if(parsedTerms.contains(value)){
                        continue;
                    }
                    parsedTerms.add(value);
                    final TermInfo termInfo = new TermInfo(value);
                    fieldInfo.terms.add(termInfo);
                }
            }
        } else {
            return null;
        }
        return fieldInfo;
    }

    private static class FieldInfo {
        String field;
        String indexedField;
        List<TermInfo> terms = new ArrayList<TermInfo>();
    }

    private static class TermInfo {
        public final BytesRef indexedBytes;
        public TermInfo(String s){
               indexedBytes = new BytesRef(s);
        }
    }
}
