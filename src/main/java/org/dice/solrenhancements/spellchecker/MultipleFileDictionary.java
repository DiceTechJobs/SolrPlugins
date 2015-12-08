package org.dice.solrenhancements.spellchecker;

import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Dictionary represented by a text file.
 *
 * <p/>Format allowed: 1 string per line, optionally with a tab-separated integer value:<br/>
 * word1 TAB 100<br/>
 * word2 word3 TAB 101<br/>
 * word4 word5 TAB 102<br/>
 */
public class MultipleFileDictionary implements Dictionary {


    private List<BufferedReader> ins;
    private BufferedReader currentReader = null;
    private String line;
    private boolean done = false;

    /**
     * Creates a dictionary based on a reader.
     */
    public MultipleFileDictionary(Reader[] readers) {
        ins = new ArrayList<BufferedReader>();

        for(Reader reader: readers) {
            BufferedReader in = new BufferedReader(reader);
            ins.add(in);
        }

    }

    @Override
    public InputIterator getEntryIterator() throws IOException {
        return new InputIterator.InputIteratorWrapper(new FileIterator());
    }

    final class FileIterator implements InputIterator {
        private long curFreq;
        private final BytesRefBuilder spare = new BytesRefBuilder();

        FileIterator(){
        }

        @Override
        public long weight() {
            return curFreq;
        }

        @Override
        public BytesRef next() throws IOException {
            if (done) {
                return null;
            }
            if(currentReader == null){
                currentReader = ins.remove(0);
            }
            line = currentReader.readLine();
            if (line != null) {
                String[] fields = line.split("\t");
                if (fields.length > 1) {
                    // keep reading floats for bw compat
                    try {
                        curFreq = Long.parseLong(fields[1]);
                    } catch (NumberFormatException e) {
                        curFreq = (long)Double.parseDouble(fields[1]);
                    }
                    spare.copyChars(fields[0]);
                } else {
                    spare.copyChars(line);
                    curFreq = 1;
                }
                return spare.get();
            } else {
                IOUtils.close(currentReader);
                if(ins.size() == 0){
                    done = true;
                    return null;
                }
                currentReader = ins.remove(0);
                return this.next();
            }
        }

        @Override
        public Comparator<BytesRef> getComparator() {
            return null;
        }

        @Override
        public BytesRef payload() {
            return null;
        }

        @Override
        public boolean hasPayloads() {
            return false;
        }

        @Override
        public Set<BytesRef> contexts() {
            return null;
        }

        @Override
        public boolean hasContexts() {
            return false;
        }
    }


}