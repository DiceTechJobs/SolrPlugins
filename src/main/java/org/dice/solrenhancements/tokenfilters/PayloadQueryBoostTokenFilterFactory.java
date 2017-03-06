package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * Created by simon.hughes on 7/30/15.
 */
public class PayloadQueryBoostTokenFilterFactory extends TokenFilterFactory {

    public PayloadQueryBoostTokenFilterFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new PayloadQueryBoostTokenFilter(tokenStream);
    }

}
