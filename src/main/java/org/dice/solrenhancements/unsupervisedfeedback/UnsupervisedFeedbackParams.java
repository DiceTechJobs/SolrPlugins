package org.dice.solrenhancements.unsupervisedfeedback;

import java.util.Locale;

/**
 * Created by simon.hughes on 9/4/14.
 */
public interface UnsupervisedFeedbackParams {

    String PREFIX = "uf.";

    String SIMILARITY_FIELDS = PREFIX + "fl";
    String MIN_TERM_FREQ = PREFIX + "mintf";
    String MAX_DOC_FREQ  = PREFIX + "maxdf";
    String MIN_DOC_FREQ  = PREFIX + "mindf";
    String MIN_WORD_LEN  = PREFIX + "minwl";
    String MAX_WORD_LEN  = PREFIX + "maxwl";
    //Changed from maxqt
    String MAX_QUERY_TERMS_PER_FIELD = PREFIX + "maxflqt";
    //Changed from maxntp
    String MAX_NUM_TOKENS_PARSED_PER_FIELD = PREFIX + "maxflntp";

    // number of docs to grab terms from
    String MAX_DOCUMENTS_TO_PROCESS = PREFIX + "maxdocs";

    String FQ = PREFIX + "fq";
    String QF = PREFIX + "qf";

    // new to this plugin
    String BOOST_FN = PREFIX + "boost";
    String PAYLOAD_FIELDS = PREFIX + "payloadfl";

    // normalize field boosts
    String NORMALIZE_FIELD_BOOSTS = PREFIX + "normflboosts";
    String IS_LOG_TF = PREFIX + "logtf";
    // end new to this plugin

    // Do you want to include the original document in the results or not
    public final static String MATCH_INCLUDE = PREFIX + "match.include";

    // If multiple docs are matched in the query, what offset do you want?
    public final static String MATCH_OFFSET  = PREFIX + "match.offset";

    // Do you want to include the original document in the results or not
    public final static String INTERESTING_TERMS = PREFIX + "interestingTerms";  // false,details,(list or true)

    public enum TermStyle {
        NONE,
        LIST,
        DETAILS;

        public static TermStyle get( String p )
        {
            if( p != null ) {
                p = p.toUpperCase(Locale.ROOT);
                if( p.equals( "DETAILS" ) ) {
                    return DETAILS;
                }
                else if( p.equals( "LIST" ) ) {
                    return LIST;
                }
            }
            return NONE;
        }
    }
}