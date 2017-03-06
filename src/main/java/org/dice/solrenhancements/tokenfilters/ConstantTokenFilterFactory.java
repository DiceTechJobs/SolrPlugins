package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * Created by simon.hughes on 6/5/15.
 */
public class ConstantTokenFilterFactory extends TokenFilterFactory{
    private final String token;

    public ConstantTokenFilterFactory(Map<String, String> args) {
        super(args);
        token = args.get("token");
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ConstantTokenFilter(tokenStream, token);
    }


}
