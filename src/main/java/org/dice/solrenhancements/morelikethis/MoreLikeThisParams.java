package org.dice.solrenhancements.morelikethis;

import java.util.Locale;

/**
 * Created by simon.hughes on 9/4/14.
 */
public interface MoreLikeThisParams {
    java.lang.String MLT = "mlt";
    java.lang.String PREFIX = "mlt.";
    java.lang.String SIMILARITY_FIELDS = "mlt.fl";
    java.lang.String MIN_TERM_FREQ = "mlt.mintf";
    java.lang.String MAX_DOC_FREQ = "mlt.maxdf";
    java.lang.String MIN_DOC_FREQ = "mlt.mindf";
    java.lang.String MIN_WORD_LEN = "mlt.minwl";
    java.lang.String MAX_WORD_LEN = "mlt.maxwl";
    //Changed from maxqt
    java.lang.String MAX_QUERY_TERMS_PER_FIELD = "mlt.maxflqt";
    //Changed from maxntp
    java.lang.String MAX_NUM_TOKENS_PARSED_PER_FIELD = "mlt.maxflntp";
    java.lang.String BOOST = "mlt.boost";
    java.lang.String FQ = "mlt.fq";

    java.lang.String QF = "mlt.qf";

    // new to this plugin
    java.lang.String FL_MUST_MATCH      = "mlt.fl.match";   // list of fields that must match the target document
    java.lang.String FL_MUST_NOT_MATCH  = "mlt.fl.different";   // list of fields that must NOT match the target document

    java.lang.String BOOST_FN = "mlt.boostfn";
    java.lang.String PAYLOAD_FIELDS = "mlt.payloadfl";

    // normalize field boosts
    java.lang.String NORMALIZE_FIELD_BOOSTS = "mlt.normflboosts";
    java.lang.String IS_LOG_TF = "mlt.logtf";

    java.lang.String STREAM_HEAD    = "stream.head";
    java.lang.String STREAM_HEAD_FL = "stream.head.fl";
    java.lang.String STREAM_BODY_FL = "stream.body.fl";

    java.lang.String STREAM_QF = "stream.qf";
    // end new to this plugin

    // the /mlt request handler uses 'rows'
    public final static String DOC_COUNT = PREFIX + "count";

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