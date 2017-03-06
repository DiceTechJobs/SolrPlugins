package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * Created by simon.hughes on 7/30/15.
 */
public class PayloadQueryBoostTokenFilter extends TokenFilter {

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    protected PayloadQueryBoostTokenFilter(TokenStream input) {
        super(input);
    }

    @Override
    public final boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            CharTermAttribute termAtt = this.getAttribute(CharTermAttribute.class);
            final String term = termAtt.toString();
            termAtt.setEmpty();

            PayloadAttribute payloadAtt = this.getAttribute(PayloadAttribute.class);
            final BytesRef payload = payloadAtt.getPayload();
            if(payload == null) {
                return true;
            }

            float payloadValue = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
            if(payloadValue == 0.0f){
                return true;
            }

            String weight = Float.toString(payloadValue);
            // set weights to zero if in scientific notation
            if(weight.contains("E-")){
                return true;
            }

            String boostedTerm = term + "^" + weight;
            termAtt.append(boostedTerm);
            return true;
        }
        return false;
    }
}
