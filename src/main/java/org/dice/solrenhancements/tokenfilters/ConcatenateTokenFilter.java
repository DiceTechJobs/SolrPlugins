package org.dice.solrenhancements.tokenfilters;

/**
 * Created by simon.hughes on 4/7/16.
 */

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.tokenattributes.*;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Based on http://sujitpal.blogspot.com/2011/07/lucene-token-concatenating-tokenfilter_30.html
 *
 * Concatenate all tokens, separated by a provided character,
 * defaulting to a single space. It always produces exactly one token, and it's designed to be the
 * last token filter in an analysis chain.
 */
public class ConcatenateTokenFilter extends TokenFilter {

  /*
  For a very different approach that could accept synonyms or anything except position gaps (e.g.
  not stopwords),
  consider using o.a.l.analysis.TokenStreamToAutomaton
  with o.a.l.util.automaton.SpecialOperations.getFiniteStrings().
  For gaps (stopwords), we could perhaps index a special token at those gaps and then have the
  tagger deal with them -- also doable.
   */

    private final String separator;
    private LinkedList<List<String>> words;
    private LinkedList<String> phrases;

    private boolean concat = false;
    private AttributeSource.State current;

    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAttr = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAttr = addAttribute(PositionLengthAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private StringBuilder buf = new StringBuilder(128);

    public ConcatenateTokenFilter(TokenStream input, String separator) {
        super(input);
        this.separator = separator;
        this.words = new LinkedList<List<String>>();
        this.phrases = new LinkedList<String>();
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.words = new LinkedList<List<String>>();
        this.phrases = new LinkedList<String>();
    }

    @Override
    public final boolean incrementToken() throws IOException {
        //TODO make sure this works with synonyms and stop words

        int i = 0;
        while (input.incrementToken()) {
            String term = new String(termAttr.buffer(), 0, termAttr.length());
            List<String> word = posIncrAttr.getPositionIncrement() > 0 ? new ArrayList<String>() : words.removeLast();
            word.add(term);
            words.add(word);
            i++;
        }
        // now write out as a single token
        if (! concat) {
            makePhrases(words, phrases, 0);
            concat = true;
        }
        while (phrases.size() > 0) {

            String phrase = phrases.removeFirst();
            restoreState(current);
            clearAttributes();

            termAttr.setEmpty();
            termAttr.append(phrase);
            termAttr.setLength(phrase.length());

            //posIncrAttr.setPositionIncrement(0);
            typeAtt.setType(ShingleFilter.DEFAULT_TOKEN_TYPE);//"shingle"

            current = captureState();
            return true;
        }

        concat = false;
        return false;
    }

    private void makePhrases(List<List<String>> words, List<String> phrases, int currPos) {
        if (currPos == words.size()) {
            return;
        }
        if (phrases.size() == 0) {
            phrases.addAll(words.get(currPos));
        } else {
            List<String> newPhrases = new ArrayList<String>();
            for (String phrase : phrases) {
                for (String word : words.get(currPos)) {
                    newPhrases.add(StringUtils.join(new String[] {phrase, word}, " "));
                }
            }
            phrases.clear();
            phrases.addAll(newPhrases);
        }
        makePhrases(words, phrases, currPos + 1);
    }

    @Override
    public void end() throws IOException {
        //we already called input.end() in incrementToken
    }
}