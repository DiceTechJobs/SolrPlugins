package org.dice.solrenhancements.morelikethis;

import com.google.common.base.Strings;
import org.apache.lucene.index.Term;

import java.text.DecimalFormat;
import java.util.Comparator;

/**
 * Created by simon.hughes on 9/4/14.
 */
public class MLTTerm implements Comparable<MLTTerm> {

    private final String word;
    private final String fieldName;
    private final float idf;
    private final int docFreq;
    private final float tf;
    private final float fieldBoost;
    private final float payload;
    private final static DecimalFormat format = new DecimalFormat("#0.00");

    private final static DecimalFormat intFormat = new DecimalFormat("##.##");
    private final boolean logTf;
    private final boolean hasPayload;
    private final boolean useBoost;

    private float vectorLength = 1.0f;

    // non-payload
    public MLTTerm(String word, String fieldName, float tf, float idf, int docFreq, boolean logTf, float fieldBoost, boolean useBoost){
        this(word, fieldName, tf, idf, docFreq, logTf, fieldBoost, 1.0f, useBoost, false);
    }

    // with payload
    public MLTTerm(String word, String fieldName, float tf, float idf, int docFreq, boolean logTf, float fieldBoost, float payload, boolean useBoost, boolean hasPayload){

        this.word = word;
        this.fieldName = fieldName;
        this.idf = idf;
        this.docFreq = docFreq;
        this.tf = tf;
        this.fieldBoost = fieldBoost;
        this.payload = payload;
        this.logTf = logTf;
        this.useBoost = useBoost;
        this.hasPayload = hasPayload;
    }

    public String getWord() {
        return word;
    }

    public String getFieldName() {
        return fieldName;
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

    public float getTermWeight(){
        if(this.hasPayload()){
            // for the payload, typically we want to include the TF but not the IDF. This is what is passed to the payload value
            return this.getPayload();
        }
        else {
            if(false == this.useBoost){
                return 1.0f;
            }
            float tfVal = this.tf;
            if (this.logTf) {
                tfVal = getLogTf();
            }
            return tfVal * this.idf;
        }
    }

    public float getNormalizedTermWeight(){
        return this.getTermWeight() / this.vectorLength;
    }

    private float getLogTf() {
        return (float) Math.log(this.tf + 1.0d);
    }

    public float getFinalScore(){
        return this.getFieldBoost() * this.getNormalizedTermWeight();
    }

    public String valuesToString(){
        StringBuilder sb = new StringBuilder();
        sb.append("score: ").append(padFloat(this.getFinalScore()));
        sb.append(" term wt: ").append(padFloat(this.getTermWeight()));

        if(this.useBoost) {
            if (this.logTf) {
                sb.append(" log(tf): ").append(padFloat(this.getLogTf()));
            } else {
                sb.append(" tf: ").append(padInt(this.getTf()));
            }
            sb.append(" df: ").append(padInt((this.getDocFreq())));
            sb.append(" idf: ").append(padFloat((this.getIdf())));
        }
        if(this.hasPayload())
        {
            sb.append(" pyld: ").append(padFloat((this.getPayload())));
        }
        sb.append(" fldBst: ").append(padFloat((this.getFieldBoost())));
        sb.append(" veclen: ").append(padFloat((this.vectorLength)));
        return sb.toString();
    }

    public static Comparator<MLTTerm> FLD_BOOST_X_SCORE_ORDER = new Comparator<MLTTerm>() {
        @Override
        public int compare(MLTTerm t1, MLTTerm t2) {
            float d = t2.getFinalScore() - t1.getFinalScore();
            if( d == 0 ) {
                return 0;
            }
            return (d>0)?1:-1;
        }
    };

    public int compareTo(MLTTerm o) {
        return ((Float)o.getFinalScore()).compareTo(this.getFinalScore());
    }

    // used in debug info (mlt.interestingTerms = details)
    public Term getTerm() {
        return new Term(this.getFieldName(), this.getWord());
    }

    public boolean hasPayload() {
        return hasPayload;
    }

    public void setVectorLength(float vectorLength) {
        this.vectorLength = vectorLength;
    }
}