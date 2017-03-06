package org.dice.solrenhancements.similarity;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.util.BytesRef;

public class PayloadAwareDefaultSimilarityNoIdf extends ClassicSimilarity {

    @Override
    public float scorePayload(int doc, int start, int end, BytesRef payload) {
        if (payload != null) {
            float x = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
            return x;
        }
        return 1.0F;
    }

    @Override
    public float idf(long docFreq, long numDocs)
    {
        return 1.0f;
    }

}
