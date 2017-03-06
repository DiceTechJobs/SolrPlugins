package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * Created by simon.hughes on 6/5/15.
 */
public class ConcatenateTokenFilterFactory extends TokenFilterFactory {

    private String separator = " ";
    private final String SEPARATOR_KEY = "separator";

    public ConcatenateTokenFilterFactory(Map<String, String> args) {
        super(args);
        if (args.containsKey(SEPARATOR_KEY)){
            this.separator = args.get(SEPARATOR_KEY);
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ConcatenateTokenFilter(tokenStream, this.separator);
    }

}
