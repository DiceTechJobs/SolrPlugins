package org.dice.solrenhancements.tokenfilters;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;

/**
 * Created by simon.hughes on 7/30/15.
 */
public class MeanPayloadTokenFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    private List<Tuple<String, Float>> averagePayloadQueue = new ArrayList<Tuple<String, Float>>();
    private AttributeSource.State current;
    private boolean processedPayloads = false;

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    protected MeanPayloadTokenFilter(TokenStream input) {
        super(input);
    }


    @Override
    public final boolean incrementToken() throws IOException {

        if(!processedPayloads) {
            final Map<String, Float>   totalPayload = new HashMap<String, Float>();
            final Map<String, Integer> tokenCount = new HashMap<String, Integer>();

            while (input.incrementToken()) {
                CharTermAttribute termAtt = this.getAttribute(CharTermAttribute.class);

                PayloadAttribute payloadAtt = this.getAttribute(PayloadAttribute.class);

                final BytesRef payload = payloadAtt.getPayload();
                if(payload == null)
                    continue;

                float payloadValue = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
                final String term = termAtt.toString();
                if (totalPayload.containsKey(term)) {
                    totalPayload.put(term, totalPayload.get(term) + payloadValue);
                    tokenCount.put(term, tokenCount.get(term) + 1);
                } else {
                    totalPayload.put(term, payloadValue);
                    tokenCount.put(term, 1);
                }
            }

            // compute the average vector and vector length
            final Map<String, Float>   averagePayload = new HashMap<String, Float>();
            float vectorLengthSq = 0.0f;
            for(String term: totalPayload.keySet()){
                float mean = totalPayload.get(term) / tokenCount.get(term);
                vectorLengthSq += mean * mean;
                averagePayload.put(term, mean);
            }

            // normalize by the vector length
            double vectorLength = Math.sqrt(vectorLengthSq);
            for(String term : averagePayload.keySet()){
                float normedValue = (float)(averagePayload.get(term) / vectorLength);
                this.averagePayloadQueue.add(new Tuple<String, Float>(term, normedValue));
            }
            this.processedPayloads = true;
        }

        if(false == this.averagePayloadQueue.isEmpty()){
            restoreState(current);
            clearAttributes();

            Tuple<String, Float> tuple = this.averagePayloadQueue.remove(0);
            setAttributes(tuple.x, tuple.y);
            current = captureState();
            return true;
        }
        return false;
    }

    protected void setAttributes(String token, float payload) {
        CharTermAttribute termAtt = this.getAttribute(CharTermAttribute.class);
        termAtt.setEmpty();
        termAtt.append(token);
        termAtt.setLength(token.length());

        PayloadAttribute payloadAtt = this.getAttribute(PayloadAttribute.class);
        byte[] bytes = PayloadHelper.encodeFloat(payload);
        payloadAtt.setPayload(new BytesRef(bytes));
    }

    @Override
    public void reset( )  throws IOException {
        this.averagePayloadQueue.clear();
        this.processedPayloads = false;
        super.reset();
    }

    public class Tuple<X, Y> {
        public final X x;
        public final Y y;
        public Tuple(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }
}
