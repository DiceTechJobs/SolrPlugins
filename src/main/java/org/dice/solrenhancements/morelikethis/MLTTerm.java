package org.dice.solrenhancements.morelikethis;

import com.google.common.base.Strings;

import java.text.DecimalFormat;
import java.util.Comparator;

/**
 * Created by simon.hughes on 9/4/14.
 */
public class MLTTerm implements Comparable<MLTTerm> {

    private final String word;
    private final String fieldName;
    private final float score;
    private final float idf;
    private final int docFreq;
    private final float tf;
    private final float fieldBoost;
    private final float payload;

    private final static DecimalFormat format = new DecimalFormat("#0.00");
    private final static DecimalFormat intFormat = new DecimalFormat("##.##");
    private final boolean logTf;

    // non-payload
    public MLTTerm(String word, String fieldName, float score, float tf, float idf, int docFreq, boolean logTf, float fieldBoost){
        this(word, fieldName, score, tf, idf, docFreq, logTf, fieldBoost, 1.0f);
    }

    // with payload
    public MLTTerm(String word, String fieldName, float score, float tf, float idf, int docFreq, boolean logTf, float fieldBoost, float payload){

        this.word = word;
        this.fieldName = fieldName;
        this.score = score;
        this.idf = idf;
        this.docFreq = docFreq;
        this.tf = tf;
        this.fieldBoost = fieldBoost;
        this.payload = payload;
        this.logTf = logTf;
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

    public float getFieldBoost() { return fieldBoost; }

    private String padFloat(float f){
        String formatted = format.format(f);
        return Strings.padStart(formatted, 7, ' ');
    }

    private String padInt(float f){
        String formatted = intFormat.format(f);
        return Strings.padStart(formatted, 5, ' ');
    }

    private float getBoostedScore(){
        return this.getFieldBoost() * this.getScore();
    }

    public String valuesToString(){
        StringBuilder sb = new StringBuilder();
        sb.append("bstd score: ").append(padFloat(this.getBoostedScore()));
        sb.append(" score: ").append(padFloat(this.getScore()));
        if(this.logTf){
            sb.append(" log(tf): ").append(padFloat(this.getTf()));
        }
        else {
            sb.append(" tf: ").append(padInt(this.getTf()));
        }
        sb.append(" df: ").append(padInt((this.getDocFreq())));
        sb.append(" idf: ").append(padFloat((this.getIdf())));
        sb.append(" pyld: ").append(padFloat((this.getPayload())));
        sb.append(" fldBst: ").append(padFloat((this.getFieldBoost())));
        return sb.toString();
    }

    public static Comparator<MLTTerm> FLD_BOOST_X_SCORE_ORDER = new Comparator<MLTTerm>() {
        @Override
        public int compare(MLTTerm t1, MLTTerm t2) {
            float d = t2.getScore() - t1.getScore();
            if( d == 0 ) {
                return 0;
            }
            return (d>0)?1:-1;
        }
    };

    public int compareTo(MLTTerm o) {
        return ((Float)o.getBoostedScore()).compareTo(this.getBoostedScore());
    }
}