package org.dice.solrenhancements.functionqueries;

/**
 * Created by simon.hughes on 2/21/17.
 */
public class SimilarityType {
    // https://en.wikipedia.org/wiki/Sorensen-Dice_coefficient
    public static String DICE = "dice";

    // https://en.wikipedia.org/wiki/Jaccard_index
    public static String JACCARD = "jaccard";

    // divide by parameter length
    public static String PARAM_LEN = "param";

    // divide by document length
    public static String DOC_LEN = "doc";
}
