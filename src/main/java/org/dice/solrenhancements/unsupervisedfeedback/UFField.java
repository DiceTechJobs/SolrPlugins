package org.dice.solrenhancements.unsupervisedfeedback;

/**
 * Created by simon.hughes on 9/4/14.
 */
public class UFField {
    private final String word;
    private final String fieldName;
    private final float score;
    private final float idf;
    private final int docFreq;
    private final float tf;
    private final float payload;

    // non-payload
    public UFField(String word, String fieldName, float score, float tf, float idf, int docFreq){
        this(word, fieldName, score, tf, idf, docFreq, 1.0f);
    }

    // with payload
    public UFField(String word, String fieldName, float score, float tf, float idf, int docFreq, float payload){

        this.word = word;
        this.fieldName = fieldName;
        this.score = score;
        this.idf = idf;
        this.docFreq = docFreq;
        this.tf = tf;
        this.payload = payload;
    }

    public String getWord() {
        return word;
    }

    public String getFieldName() {
        return fieldName;
    }

    public float getScore() {
        return score;
    }

    public float getIdf() {
        return idf;
    }

    public int getDocFreq() {
        return docFreq;
    }

    public float getTf() {
        return tf;
    }

    public float getPayload() {
        return payload;
    }

    public boolean hasPayload(){
        return payload != 1.0;
    }
}