DiceTechJobs - Solr Plugins
===========================

A Dice Tech Job repository for Dice.com's Solr plugins. Most extend or build upon the core solr and lucene libraries (kudos to the original contributors and the ASF) with additional functionality we've found useful for certain tasks. This extends upon the original solr and lucene source code (version 4.6.1) so please note the APACHE license.
**Please see the branches for versions built against different solr versions, including 6.3**

Please see https://github.com/DiceTechJobs/SolrConfigExamples for example solr config file entries to configure these plugins. There is a lot of documentation in those config files that explain in more detail how to use these plugins.

Included:

* Plugins necessary for **Conceptual Search** Implementation (see Lucene Revolution 2015 talk - http://lucenerevolution.org/sessions/implementing-conceptual-search-in-solr-using-lsa-and-word2vec/)
  * Custom query parsers: **VectorQParser** (for handling dense vector fields), **QueryBoostingQParser** (weighted synonym term expansion at query time) 
    * **important**: these query handlers handle the solr multi-word synonym problem by replacing spaces with comma's before query analysis. Your query analysis pipeline for these fields must tokenize on commas as well as spaces.
  * Custom token filters - **MeanPayloadTokenFilter** (averages payloads over duplicate terms), **PayloadQueryBoostTokenFilter** (turns a payload in a synonym file into a term boost at query time)
  * See also https://github.com/DiceTechJobs/SolrConfigExamples for example solr xml files
  * See also https://github.com/DiceTechJobs/ConceptualSearch for python scripts to extract common keywords and phrases, train the word2vec model and cluster the resulting word vectors.
* **PayloadAwareExtendedDismaxQParserPlugin**
  * Extension of the edismax query parser that includes the mean payload score over each term in addition to term frequency and document frequency when computing a relevancy score. Allows application of a per term weighting at index time so you can apply your own weightings to the same term differently depending on the document, for instance if using a 'learning to rank' approach to improve relevancy, or some implementation of probabilistic information retrieval.
  * Requires a custom similarity class implementation to be payload aware, e.g. dice's PayloadAwareDefaultSimilarity
  * **important** only utilizes payloads for fields which have a field type name that contains 'payload' or 'vector'
* Custom Similarity Classes
  * Use `<similarity class="solr.SchemaSimilarityFactory"/>` in schema.xml to configure per field similarity class overrides
  * Custom classes include (see https://github.com/DiceTechJobs/SolrPlugins/tree/master/src/main/java/org/dice/solrenhancements/similarity for full list):
    * **PayloadAwareDefaultSimilarity** - DefaultSimilarity class extended to include payloads in scoring function
    * **NoLengthNormSimilarity** - remove all length norms from scoring function - useful for very short fields, such as job titles
    * **PayloadOnlySimilarity** - only score terms on payloads. Useful when building a custom relevancy calculation where you want to disable field norms and tf and idf weightings (such as storing a vector field for conceptual search, or building a recommender system where you want to embed your own term weights from a machine learning model)
* Custom Token Filters
  * **TypeEraseFilter** - erases the type field value from the tokens in an analysis chain. Useful if applying several sets of synonym filters, and you want to use only some of these filters to filter the resulting tokens with a TypeTokenFilterFactory
  * **ConstantTokenFilter** - emits a constant token for each token in the token stream. Useful for doing things like counting certain token types - use a synonym filter plus a TypeTokenFilter to filter to certain tokens, and then a ConstantTokenFilter to allow counting or boosting by the number of tokens using the termfreq() function at query time (or apply a negative boost using the count and the div function).
* **Dice custom MLT Handler**
  * Allows top n terms per field, rather than across all fields specified
  * Fleshes out 'more like these' functionality - use multiple target documents to generate recommendations
  * **boost** query support - supports multiplicative boosts for matching items, for instance boost by relevancy and proximity to your user
  * Better support for content streams in place of source documents to generate recommendations from
* **Unsupervised Feedback Handler (a.k.a. blind feedback \ pseudo-relevancy feedback)**
  * Implements a well-researched methodology from the field of information retrieval for improving relevancy. Also known as 'blind feedback' and 'pseudo-relevancy feedback'.
  * Uses code based on the custom MLT handler to execute each query twice. The first execution uses the MLT code to grab the top terms for the result set by their tf.idf values. It then adds these terms to the original query (term expansion) and re-executes.
  * This 2 phase execution happens inside of solr (one round trip) and so has a negilible impact on response time for most queries while noticeably improving relevancy.
* **DiceSuggester** 
  * A suggester that allows you to use one field type to source suggestions, and a separate field type to transform the matching suggestions into some other string using an analysis chain. For instance, we use it to map all variants of a skill into a canonical form (e.g. hadoop=>"Apahce Hadoop") before returning the suggestions.
  * It allows different field types to be used to process matching suggestions (suggestionAnalyzerFieldTypeName), such as applying synonyms, stemming, etc. This is necessary for applying the transformation, as the spellchecker needs to store the raw un modified tokens to do the auto-complete.
  * It also ensures all suggestions generated are UNIQUE.
  * Requires a comma-delimited set of files containing phrase counts (param - sourceLocation). These are phrases from the transformed field, along with their counts. See SolrConfigExamples - skillSuggest configuration.
* **DiceMultipleCaseSuggester** 
  * Solr suggester modification - can handle UPPER, lower and **T**itle **C**ase variations for type ahead. 
  * Regular solr suggester functionality is case sensitive.
  * See SolrConfigExamples - titleSuggest configuration.
* **DiceSpellCheckComponent** and **DiceDirectSolrSpellChecker**
  * Regular solr spell check component can only search for corrections within 2 edit distances of each query term
  * This extends this functionality to allow you to embed a file of common user typos that will take precedence over the edit distance matches.
  *  Allows you to data-mine common typos that go beyond an edit distance of 2 and inject them into your spellchecker, or override common bad spellchecking suggestions.

Should be compatible with solr versions 4+ and 5+. Please contact us via the issues list in this repository with any questions, bug reports, feedback or feature requests.
