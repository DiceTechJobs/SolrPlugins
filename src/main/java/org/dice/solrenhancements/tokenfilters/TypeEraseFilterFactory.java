package org.dice.solrenhancements.tokenfilters;

/**
 * Created by simon.hughes on 6/5/15.
 */
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * Factory for {@link TypeEraseFilter}.
 */
public class TypeEraseFilterFactory extends TokenFilterFactory {

    /** Creates a new PorterStemFilterFactory */
    public TypeEraseFilterFactory (Map<String,String> args) {
        super(args);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    @Override
    public TypeEraseFilter create(TokenStream input) {
        return new TypeEraseFilter(input);
    }
}