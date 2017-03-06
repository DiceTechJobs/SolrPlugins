package org.dice.solrenhancements.similarity;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.util.BytesRef;

/**
 * Created by simon.hughes on 4/16/14.
 */
public class PayloadOnlySimilarity extends PayloadIdfSimilarity {

    @Override
    public float sloppyFreq(int distance)
    {
        return 1.0f;
    }

    @Override
    public float idf(long docFreq, long numDocs)
    {
        return 1.0f;
    }

    @Override
    public float scorePayload(int doc, int start, int end, BytesRef payload) {
        if (payload != null) {
            float x = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
            return x;
        }
        return 1.0F;
    }

}
