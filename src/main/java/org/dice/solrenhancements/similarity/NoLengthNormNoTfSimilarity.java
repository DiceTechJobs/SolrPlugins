package org.dice.solrenhancements.similarity;

/**
 * Created by simon.hughes on 4/16/14.
 * Turn off all weightings
 */
public class NoLengthNormNoTfSimilarity extends NoLengthNormSimilarity {

    @Override
    public float tf(float freq) {

        if(freq > 0){
            return 1.0f;
        }
        return 0.0f;
    }

    @Override
    public String toString() {
        return "NoLengthNormNoTfSimilarity";
    }
}
