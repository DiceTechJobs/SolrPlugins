package org.dice.solrenhancements.filtersuggester;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.*;
import org.dice.solrenhancements.machinelearning.FeatureExtractor;
import org.dice.solrenhancements.JarVersion;
import org.dice.solrenhancements.SearchComponentHelper;
import org.dice.solrenhancements.machinelearning.classification.rules.DecisionStump;
import org.dice.solrenhancements.machinelearning.classification.rules.DecisionStumpLearner;
import org.dice.solrenhancements.machinelearning.classification.rules.PartitionObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by simon.hughes on 10/14/16.
 */
public class FilterSuggesterComponent extends SearchComponent {

    /*
    This component takes a set of positive and negative documents, and attempts to auto-suggest filter queries that
    maximize the likelihood of only getting the desired documents returned (or minimizes the likelihood of getting more
    negative documents back)

    SH: I envision 3 principle Use Cases:

    1. Specify positive and negative examplars
    2. Specify positive examplars (from clicks), and negative query is set to the original search query.
        In this case, the component will execute the original query, and subtract any positive examplars from it before
        continuing
    3. Specify either positive or negative examplars only. In which case the algorithm will sample from the entire collection
        to populate the other side of the equation.


    Example config:

    <requestHandler name="/filterSuggest" class="org.apache.solr.handler.component.SearchHandler">
     <lst name="defaults">
     </lst>
     <arr name="components">
       <str>filterSuggest</str>
     </arr>
   </requestHandler>

	<searchComponent name="filterSuggest" class="org.dice.solrenhancements.filtersuggester.FilterSuggesterComponent">
		<str name="fl">jobTitle,jobTitle_text,skill</str>
		<!-- not - multiple "Spell Checkers" can be declared and used by this component -->
	</searchComponent>

     */


    private static final Logger Log = LoggerFactory.getLogger( FilterSuggesterComponent.class );
    private final static String EDISMAX = ExtendedDismaxQParserPlugin.NAME;

    private static class PARAMS{
        private static final String posQry = "positiveDocsQry";
        private static final String negQry = "negativeDocsQry";

        // maximum number of docs to match for the positive and negative queries
        private static final String maxDocs = "maxDocs";
        private static final String minDf = "minDf";
        private static final String suggestions = "suggestions";
    }

    private static int DFLT_MAX_DOCS = 100;
    private static int DFLT_MIN_DF   = 5;
    private static int DFLT_SUGGESTIONS = 5;

    // if no documents match one of the 2 queries, sample from this collection
    public static final String FALL_BACK_QRY = "*:*";

    private String[] fields = new String[0];
    private int cfgMaxDocs = DFLT_MAX_DOCS;
    private int cfgMinDf = DFLT_MIN_DF;
    private int cfgSuggestions = DFLT_SUGGESTIONS;

    private Analyzer analyzer = null;

    @Override
    public void init( NamedList args ) throws SolrException {
        Log.info( "init ..." );

        String fieldList = (String)args.get( CommonParams.FL );
        if ( fieldList != null ) {
            Log.info(String.format("setting %s value to: %s", CommonParams.FL, fieldList ));
            this.fields = SearchComponentHelper.parseFieldsParameter(fieldList);
        }

        String strMaxDocs = (String)args.get(PARAMS.maxDocs);
        if(strMaxDocs != null){
            int iMaxDocs = Integer.parseInt(strMaxDocs);
            this.cfgMaxDocs = iMaxDocs;
        }

        String strSuggestions  = (String)args.get(PARAMS.suggestions);
        if(strSuggestions != null){
            int iSuggestions= Integer.parseInt(strSuggestions);
            this.cfgSuggestions = iSuggestions;
        }
    }

    public void prepare(ResponseBuilder rb) throws IOException {
    }

    public void process(ResponseBuilder rb) throws IOException {
        try {

            final SolrQueryRequest request = rb.req;
            final SolrParams params = request.getParams();

            String[] arrFields = this.fields;
            // override fields from request parameters
            final String reqFields = params.get(CommonParams.FL);
            if(reqFields != null){
                arrFields = SearchComponentHelper.parseFieldsParameter(reqFields);
            }

            if(arrFields.length == 0){
                throw new RuntimeException(String.format("%s called with no field list specified", FilterSuggesterComponent.class.getName()));
            }

            String strPosQry = params.get( PARAMS.posQry);
            String strNegQry = params.get( PARAMS.negQry);

            if(strNegQry == null && strPosQry == null){
                Log.error(String.format("At least one positive or negative query must be specified for the '%s' to work", FilterSuggesterComponent.class.getName()));
                return;
            }
            if(strPosQry == null){
                strPosQry = FALL_BACK_QRY;
            }
            if(strNegQry == null){
                strNegQry = FALL_BACK_QRY;
            }

            // max documents to retrieve for each query
            int maxDocs = params.getInt(PARAMS.maxDocs, this.cfgMaxDocs);
            int minDf = params.getInt(PARAMS.minDf, this.cfgMinDf);
            int numSuggestions = params.getInt(PARAMS.suggestions, this.cfgSuggestions);

            SolrIndexSearcher searcher = request.getSearcher();
            IndexReader reader = searcher.getIndexReader();
            if(analyzer == null) {
                this.analyzer = searcher.getSchema().getIndexAnalyzer();
            }

            String defType = params.get(QueryParsing.DEFTYPE, EDISMAX);
            if(defType.equals(EDISMAX)){
                // edismax requires QF and DF parameters
                ModifiableSolrParams modParams = new ModifiableSolrParams( params );
                modParams.set(DisMaxParams.QF, String.join(" ", Arrays.asList(arrFields)));
                modParams.set(CommonParams.DF, arrFields[0]);
                request.setParams( modParams );
            }

            Query positiveQuery = SearchComponentHelper.parseQuery(request, strPosQry, defType);
            Query negativeQuery = SearchComponentHelper.parseQuery(request, strNegQry, defType);

            Set<Integer> positiveDocIds = this.getDocidsFromQuery(positiveQuery, searcher, maxDocs);
            Set<Integer> negativeDocIds = this.getDocidsFromQuery(negativeQuery, searcher, maxDocs);

            final boolean hasPositiveMatches = positiveDocIds.size() > 0;
            final boolean hasNegativeMatches = negativeDocIds.size() > 0;

            if(!hasPositiveMatches && !hasNegativeMatches){
                Log.info(String.format("No documents match the positive query %s nor the negative query %s", strPosQry, strNegQry ));
                return;
            }

            if(!hasPositiveMatches){
                Log.info(String.format("No documents match the positive query %s. Sampling from the entire collection", strPosQry));
                positiveDocIds = this.getDocidsFromQuery(SearchComponentHelper.parseQuery(request, FALL_BACK_QRY, defType), searcher, maxDocs);
                positiveDocIds.removeAll(negativeDocIds);
            }
            else {
                if(!hasNegativeMatches) {
                    Log.info(String.format("No documents match the negative query %s. Sampling from the entire collection", strNegQry));
                    negativeDocIds = this.getDocidsFromQuery(SearchComponentHelper.parseQuery(request, FALL_BACK_QRY, defType), searcher, maxDocs);
                }
                // do this either way

                // for the use case where we only have positive ids, subtract positive ids from the negative ids, as likely sampling from the collection
                // or executing negative query as the original search query
                negativeDocIds.removeAll(positiveDocIds);
            }

            Map<String, Map<String, Integer>> featureMap =
                    new FeatureExtractor(reader, arrFields, this.analyzer, minDf)
                        .extractFeaturesFromDocuments(positiveDocIds, FeatureExtractor.LABELS.POSITIVE)
                        .extractFeaturesFromDocuments(negativeDocIds, FeatureExtractor.LABELS.NEGATIVE)
                    .getFeatureMap();

            List<DecisionStump<String>> rankedRules = DecisionStumpLearner.learnRules(featureMap, new Function<Map<String, Integer>, Float>() {
                @Override
                public Float apply(Map<String, Integer> labelCounts) {
                    return PartitionObjective.entropy(labelCounts);
                }
            });

            SimpleOrderedMap responseInfo = new SimpleOrderedMap();
            rb.rsp.add("diceFilterSuggester", responseInfo);

            int rank = 0;
            for(DecisionStump<String> stump: rankedRules.subList(0, Math.min(numSuggestions, rankedRules.size()))){
                rank +=1;
                String filter = String.format("fq=%s(%s)", stump.getClassLabel().equals(FeatureExtractor.LABELS.POSITIVE) ? "+": "-", stump.getFeatureValue());
                responseInfo.add(new Integer(rank).toString(), filter);
            }
        }
        catch (Exception ex){
            Log.error(String.format("Error thrown in class %s while pre-processing user query", FilterSuggesterComponent.class.getName()), ex);
        }
    }


    private Set<Integer> getDocidsFromQuery(Query query, SolrIndexSearcher searcher, int maxRows) throws SyntaxError, IOException {

        DocList match = searcher.getDocList(query, null, null, 0, maxRows, 0); // only get the first one...
        Set<Integer> docsIds = new HashSet<Integer>();

        DocIterator iterator = match.iterator();
        while (iterator.hasNext()) {
            docsIds.add(iterator.nextDoc());
        }
        return docsIds;
    }

    private String[] fieldNames;
    private String[] getFieldNames(IndexReader ir) {
        if (fieldNames == null) {
            // gather list of all valid fields from lucene, if none specified
            Collection<String> fields = MultiFields.getIndexedFields(ir);
            fieldNames = fields.toArray(new String[fields.size()]);
        }

        return fieldNames;
    }

    public String getDescription() {
        return null;
    }

    private String version = null;
    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        }

        version = JarVersion.getVersion(Log);
        return version;
    }
}
