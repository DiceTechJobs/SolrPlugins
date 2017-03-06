package org.dice.solrenhancements.similarity;

import org.apache.lucene.search.similarities.ClassicSimilarity;

/**
 * Created by simon.hughes on 4/16/14.
 * Turn off all weightings
 */
public class CosineSimilarity extends ClassicSimilarity {

    @Override
    public float coord(int overlap, int maxOverlap)
    {
        return 1.0f;
    }

    @Override
    public float lengthNorm(org.apache.lucene.index.FieldInvertState state)
    {
        return 1.0f;
    }

    @Override
    public float queryNorm(float sumOfSquaredWeights)
    {
        return 1.0f;
    }

    // retain Tf (part of cosine sim)
    @Override
    public float tf(float freq) {

        return super.tf(freq);
    }

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
}
