package org.dice.solrenhancements.similarity;

/**
 * Created by simon.hughes on 4/16/14.
 * Turn off all weightings
 */
public class NoLengthNormSimilarity extends DiceDefaultSimilarity{

    @Override
    public float decodeNormValue(long norm) {
        return 1.0f;
    }

    @Override
    public float lengthNorm(org.apache.lucene.index.FieldInvertState state)
    {
        return state.getBoost();
    }

    @Override
    public String toString() {
        return "NoLengthNormSimilarity";
    }
}
