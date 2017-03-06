package org.dice.solrenhancements.machinelearning.classification.rules;

/**
 * Created by simon.hughes on 2/1/17.
 */
public class DecisionStump<V>{

    private final String featureValue;
    private final String classLabel;
    private final Float ruleScore;
    private final Integer partitionSize;

    public DecisionStump(String featureValue, String classLabel, Float ruleScore, Integer partitionSize) {
        this.featureValue = featureValue;
        this.classLabel = classLabel;
        this.ruleScore = ruleScore;
        this.partitionSize = partitionSize;
    }

    public String getFeatureValue() {
        return featureValue;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public Float getRuleScore() {
        return ruleScore;
    }

    @Override
    public String toString(){
        return String.format("%s -> %s", this.featureValue, this.classLabel);
    }

    public Integer getPartitionSize() {
        return partitionSize;
    }
}
