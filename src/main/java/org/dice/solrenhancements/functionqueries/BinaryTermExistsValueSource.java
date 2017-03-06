package org.dice.solrenhancements.functionqueries;

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


import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.docvalues.IntDocValues;
import org.apache.lucene.queries.function.valuesource.DocFreqValueSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Map;

/**
 * SH: Note that this is a modified version of the term frequency value source
 *
 * Function that returns {@link PostingsEnum#freq()} for the
 * supplied term in every document.
 * <p>
 * If the term does not exist in the document, returns 0.
 * If frequencies are omitted, returns 1.
 */
public class BinaryTermExistsValueSource extends DocFreqValueSource {
    public  BinaryTermExistsValueSource(String field, String val, String indexedField, BytesRef indexedBytes) {
        super(field, val, indexedField, indexedBytes);
    }

    @Override
    public String name() {
        return "binarytermexists";
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException{
        Fields fields = readerContext.reader().fields();
        final Terms terms = fields.terms(indexedField);

        return new IntDocValues(this) {

            PostingsEnum docs ;
            int atDoc;
            int lastDocRequested = -1;

            { reset(); }

            public void reset() throws IOException {
                // no one should call us for deleted docs?

                if (terms != null) {
                    final TermsEnum termsEnum = terms.iterator();
                    if (termsEnum.seekExact(indexedBytes)) {
                        docs = termsEnum.postings(null);
                    } else {
                        docs = null;
                    }
                } else {
                    docs = null;
                }

                if (docs == null) {
                    docs = new PostingsEnum() {
                        @Override
                        public int docID() {
                            return DocIdSetIterator.NO_MORE_DOCS;
                        }

                        @Override
                        public int nextDoc() throws IOException {
                            return DocIdSetIterator.NO_MORE_DOCS;
                        }

                        @Override
                        public int advance(int i) throws IOException {
                            return DocIdSetIterator.NO_MORE_DOCS;
                        }

                        @Override
                        public long cost() {
                            return 0;
                        }

                        @Override
                        public int freq() throws IOException {
                            return 0;
                        }

                        @Override
                        public int nextPosition() throws IOException {
                            return 0;
                        }

                        @Override
                        public int startOffset() throws IOException {
                            return 0;
                        }

                        @Override
                        public int endOffset() throws IOException {
                            return 0;
                        }

                        @Override
                        public BytesRef getPayload() throws IOException {
                            return null;
                        }
                    };
                }
                atDoc = -1;
            }

            @Override
            public int intVal(int doc) {
                try {
                    if (doc < lastDocRequested) {
                        // out-of-order access.... reset
                        reset();
                    }
                    lastDocRequested = doc;

                    if (atDoc < doc) {
                        atDoc = docs.advance(doc);
                    }

                    if (atDoc > doc) {
                        // term doesn't match this document... either because we hit the
                        // end, or because the next doc is after this doc.
                        return 0;
                    }

                    // a match! - SH: cap at 1 for match score
                    return Math.min(1,docs.freq());
                } catch (IOException e) {
                    throw new RuntimeException("caught exception in function "+description()+" : doc="+doc, e);
                }
            }
        };
    }
}

