package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

/**
 * Created by simon.hughes on 6/5/15.
 */
public class ConstantTokenFilter extends TokenFilter {

    private final String token;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    protected ConstantTokenFilter(TokenStream input, String token) {
        super(input);
        this.token = token;
    }


    @Override
    public final boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            CharTermAttribute termAtt = this.getAttribute(CharTermAttribute.class);
            termAtt.setEmpty();
            termAtt.append(token);
            return true;
        } else
            return false;
    }
}
