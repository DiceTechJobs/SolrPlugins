package org.dice.solrenhancements.jointprobability;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

import java.io.Serializable;
import java.util.*;

/**
 * Created by simon.hughes on 1/18/16.
 */
public class JointProbabilityModel implements Serializable {

    private Map<String,Float> priors = new HashMap<String,Float>();
    private Map<String,Float> jointCounts = new HashMap<String,Float>();

    private final float smoothValue;
    private final float totalJointCounts;
    private final float totalPriorCounts;

    public JointProbabilityModel(NamedList<Object> jointProbabilityResult){
        this(jointProbabilityResult, 0.01f);
    }

    public JointProbabilityModel(NamedList<Object> jointProbabilityResult, float smoothValue){
        this.smoothValue = smoothValue;

        for(Map.Entry<String, Object> facetFields: jointProbabilityResult){
            String field = facetFields.getKey();
            ArrayList<SimpleOrderedMap<Object>> outerCounts =(ArrayList<SimpleOrderedMap<Object>> )facetFields.getValue();

            for(SimpleOrderedMap<Object> outerFieldCount: outerCounts){

                String token = "";
                String subField = "";

                int priorCount = -1;
                ArrayList<SimpleOrderedMap<Object>> innerFieldCounts = null;

                for(Map.Entry<String, Object> entry: outerFieldCount){
                    String key = entry.getKey();
                    final Object value = entry.getValue();
                    if(key.equals(JointCounts.VALUE)){
                        token = value.toString();
                    }
                    else if(key.equals(JointCounts.COUNT)){
                        priorCount = (Integer) value;
                    }
                    else{
                        subField = key;
                        innerFieldCounts = (ArrayList<SimpleOrderedMap<Object>>)value;
                    }
                }
                addPrior(field, token, priorCount);

                if(innerFieldCounts != null){
                    for(SimpleOrderedMap<Object> innerFieldCount: innerFieldCounts) {

                        int jointCount = (Integer) innerFieldCount.get(JointCounts.COUNT);
                        String subToken = innerFieldCount.get(JointCounts.VALUE).toString();
                        addJoint(field, token, subField, subToken, jointCount);
                    }
                }
            }
        }

        float total = 0f;
        for(Float count: this.jointCounts.values()){
            total += count;
        }
        this.totalJointCounts = total;

        float pTotal = 0;
        for(Float count: this.priors.values()){
            pTotal += count;
        }
        this.totalPriorCounts = pTotal;
    }

    private static final String KEY_DELIM = "__";
    private String getFieldKey(String field, String val){
        return String.format("%s%s%s", field, KEY_DELIM, val.replace(KEY_DELIM,""));
    }

    private String getKey(String fieldA, String a, String fieldB, String b){
        String aKey = getFieldKey(fieldA, a);
        String bKey = getFieldKey(fieldB, b);
        if(aKey.compareTo(bKey) < 0) {
            return aKey + "|" + bKey;
        }
        else{
            return bKey + "|" + aKey;
        }
    }

    private void addJoint(String fieldA, String a, String fieldB, String b, int count){
        jointCounts.put(getKey(fieldA, a, fieldB, b), count + this.smoothValue);
    }

    private void addPrior(String fieldA, String a, int count){
        priors.put(getFieldKey(fieldA, a), count + this.smoothValue);
    }

    /***
     * Computes the conditional probability - p(a/b)
     *
     * @param fieldA field name for value a
     * @param a      value of field a
     * @param fieldB field name for value b
     * @param b      value of field b
     * @return       conditional probability p(a/b)
     */
    public float getConditional(String fieldA, String a, String fieldB, String b){
        final float prior = this.getPrior(fieldB, b);
        // don't return p == 1 for unobserved values
        if(prior == this.smoothValue){
            return 0.0f;
        }
        return this.getJoint(fieldA, a, fieldB, b) / prior;
    }

    /***
     * Computes the joint probability of a and b - p(A ^ B)
     *
     * @param fieldA field name for value a
     * @param a      value of field a
     * @param fieldB field name for value b
     * @param b      value of field b
     * @return       the joint probability of a AND b
     */
    public float getJoint(String fieldA, String a, String fieldB, String b){
        String key = getKey(fieldA, a, fieldB, b);

        float count = smoothValue;
        if(jointCounts.containsKey(key)){
            count = this.jointCounts.get(key);
        }
        return count / this.totalJointCounts;
    }

    /***
     * Computes the prior probability of a value for a field - p(fieldName = value)
     *
     * @param fieldName the field name for value
     * @param value     the value of the field
     * @return          the prior probability of the value for the field - p(fieldName = value)
     */
    public float getPrior(String fieldName, String value){
        String key = getFieldKey(fieldName, value);
        float count = smoothValue;
        if(this.priors.containsKey(key)){
            count = this.priors.get(key);
        }
        return count / totalPriorCounts;
    }

    public Set<String> getFieldValues(String fieldName){
        Set<String> tokens = new HashSet<String>();
        for(String key : this.priors.keySet()){
            String[] arr = key.split(KEY_DELIM);
            if(arr.length > 1 && arr[0].equals(fieldName)){
                tokens.add(arr[arr.length-1].trim());
            }
        }
        return tokens;
    }

    public int getNumPriors(){
        return this.priors.size();
    }

    public int getNumJointCounts(){
        return this.jointCounts.size();
    }
}
