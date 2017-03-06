package org.dice.solrenhancements.machinelearning.classification.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DecisionStumpLearner {

    public static List<DecisionStump<String>> learnRules(Map<String,Map<String, Integer>> featureMap,
                                                  Function<Map<String, Integer>, Float> objectiveToMinimize){
        List<DecisionStump<String>> rankedRules = new ArrayList<DecisionStump<String>>();
        for(Map.Entry<String, Map<String, Integer>> entry: featureMap.entrySet()){

            final String feature = entry.getKey();

            final Map<String, Integer> classDistributions = entry.getValue();
            float score = objectiveToMinimize.apply(classDistributions);

            int maxScore = Integer.MIN_VALUE;
            String mostFrequentClass = "";

            int partitionSize = 0;
            for(Map.Entry<String, Integer> kvp: classDistributions.entrySet()){
                partitionSize += kvp.getValue();
                if(kvp.getValue() > maxScore){
                    maxScore = kvp.getValue();
                    mostFrequentClass = kvp.getKey();
                }
            }

            rankedRules.add(new DecisionStump<String>(feature, mostFrequentClass, score, partitionSize));
        }

        // sort by score, lowest to highest (minimizing the objective)
        rankedRules.sort(new Comparator<DecisionStump<String>>() {
            @Override
            public int compare(DecisionStump<String> r1, DecisionStump<String> r2) {
                // score ascending
                int compare = r1.getRuleScore().compareTo(r2.getRuleScore());
                if(compare != 0){
                    return compare;
                }
                // then frequency descending
                return r2.getPartitionSize().compareTo(r1.getPartitionSize());
            }
        });

        return rankedRules;
    }
}
