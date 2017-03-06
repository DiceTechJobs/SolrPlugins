package org.dice.solrenhancements.functionqueries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.solr.common.SolrException;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.dice.solrenhancements.TermExtractionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by simon.hughes on 6/19/15.
 */
public class TermIntersectsValueSourceParser extends ValueSourceParser {

    private static final Logger log = LoggerFactory.getLogger( TermIntersectsValueSourceParser.class );

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        ParamInfo paramInfo = parseTerm(fp);
        if(paramInfo == null){
            return new DoubleConstValueSource(-1.0d);
        }

        if(paramInfo.terms.size() == 0){
            return new DoubleConstValueSource(0.0d);
        }

        return new TermIntersectsValueSource(paramInfo.field, paramInfo.analyzer, paramInfo.terms, paramInfo.similarity);
    }

    private static ParamInfo parseTerm(FunctionQParser fp) throws SyntaxError {
        ParamInfo paramInfo = new ParamInfo();

        paramInfo.field = fp.parseArg();
        String textVal = fp.parseArg();
        if(textVal == null || textVal.trim().length() == 0){
            return paramInfo;
        }

        if(fp.hasMoreArguments()){
            String similarity = fp.parseArg().toLowerCase().trim();
            if( !similarity.equals(SimilarityType.DOC_LEN) &&
                !similarity.equals(SimilarityType.PARAM_LEN) &&
                !similarity.equals(SimilarityType.DICE) &&
                !similarity.equals(SimilarityType.JACCARD)){

                log.error(String.format("Invalid similarity class: %s. Defaulting to %s", similarity, SimilarityType.DOC_LEN));
                similarity = SimilarityType.DOC_LEN;
            }
            paramInfo.similarity = similarity;
        }

        // need to do analysis on the term
        Analyzer analyzer = fp.getReq().getSchema().getIndexAnalyzer();
        paramInfo.analyzer = analyzer;
        try {
            List<String> terms = TermExtractionHelper.getTermsFromString(analyzer, paramInfo.field, textVal);
            paramInfo.terms = new HashSet<String>(terms);
        } catch (IOException e) {
            SolrException.log(log, "Exception during debug", e);
            return null;
        }
        return paramInfo;
    }

    private static class ParamInfo {
        String field;
        Set<String> terms = null;
        Analyzer analyzer = null;
        String similarity = SimilarityType.DOC_LEN;
    }
}
