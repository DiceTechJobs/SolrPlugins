package org.dice.solrenhancements.similarity;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.util.BytesRef;

/**
 * Created by simon.hughes on 4/2/14.
 *
 * Only considers payloads and idf
 */
public class PayloadIdfSimilarity extends NoLengthNormNoTfSimilarity  {
    @Override
    public float coord(int overlap, int maxOverlap)
    {
        return 1.0f;
    }

    @Override
    public float queryNorm(float sumOfSquaredWeights)
    {
        return 1.0f;
    }

    @Override
    public float idf(long docFreq, long numDocs)
    {
        return super.idf(docFreq, numDocs);
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
