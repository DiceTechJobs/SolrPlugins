package org.dice.solrenhancements.machinelearning.classification.rules;

import java.util.Map;

/**
 * Created by simon.hughes on 2/1/17.
 */
public class PartitionObjective {

    /**
     * Computes the entropy over a partition of labels. We want to
     * make splits that maximally reduce the entropy
     *
     * @param labelCounts
     * @return entropy calculation
     */
    public static Float entropy(Map<String, Integer> labelCounts){

        float totalCount = 0.0f;
        for(Integer i: labelCounts.values()){
            totalCount += i;
        }

        float entropy = 0.0f;
        for(Map.Entry<String, Integer> entry: labelCounts.entrySet()){
            int count = entry.getValue();
            float probability = count / totalCount;
            entropy -= (probability * Math.log(probability));
        }

        return entropy;
    }
}
