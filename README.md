SolrPlugins
======================

A repository for Dice.com's Solr plugins. This relies on the solr source so please note the APACHE license.

Included:

* Plugins necessary for Conceptual Search Implementation (see Lucene Revolution 2015 talk - http://lucenerevolution.org/sessions/implementing-conceptual-search-in-solr-using-lsa-and-word2vec/)
* Dice custom MLT handler
  * Allows top n terms per field, rather than across all fields specified
  * Fleshes out 'more like these' functionality - use multiple target documents to generate recommendations
  * **boost** query support - supports multiplicative boosts for matching items, for instance boost by relevancy and proximity to your user
  * Better support for content streams in place of source documents to generate recommendations from
* Unsupervised feedback handler
