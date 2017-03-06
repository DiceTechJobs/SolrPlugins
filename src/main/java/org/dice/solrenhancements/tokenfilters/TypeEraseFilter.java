package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;

/**
 * Created by simon.hughes on 6/5/15.
 */
public class TypeEraseFilter extends TokenFilter {

    private final TypeAttribute typeAttr = addAttribute(TypeAttribute.class);

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    public TypeEraseFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {

        if(!input.incrementToken()){
            return false;
        }

        typeAttr.setType(null);
        return true;
    }
}
