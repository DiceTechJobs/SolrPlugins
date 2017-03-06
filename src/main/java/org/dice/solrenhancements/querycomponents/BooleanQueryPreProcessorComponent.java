package org.dice.solrenhancements.querycomponents;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.dice.parsing.QueryLexer;
import org.dice.parsing.RecursiveDescentParser;
import org.dice.parsing.ast.Expression;
import org.dice.solrenhancements.JarVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by simon.hughes on 4/14/16.
 *
 * Use this for reference: https://github.com/lucidworks/simple-category-extraction-component/blob/master/src/main/java/com/lucidworks/solr/query/CategoryExtractionComponent.java
 */
public class BooleanQueryPreProcessorComponent extends QueryComponent {
// SH: This should just extend SearchComponent, not query component

    private static final Logger Log = LoggerFactory.getLogger( BooleanQueryPreProcessorComponent.class );

    // parameter containing the user's query. Default to the q parameter
    private String queryParameter = CommonParams.Q;
    private static final String QPARAM = "qParam";

    // template parameter. Replace this value if the query is a boolean or phrase query
    private String queryConfigParam = null;
    private static final String QUERY_CONFIG_PARAM = "qConfigParam";

    private String altQueryConfig = null;
    private static final String ALT_QUERY_CONFIG = "altQueryConfig";

    private boolean configParamParsingEnabled = true;
    private static final String PARSING_ENABLED = "parseQuery";

    @Override
    public void init( NamedList initArgs ) {
        // parameter containing the user query
        String queryParam = (String)initArgs.get(QPARAM);
        if ( queryParam != null ) {
            this.queryParameter = queryParam;
        }

        String qConfigParam = (String)initArgs.get(QUERY_CONFIG_PARAM);
        if(false == StringUtils.isBlank(qConfigParam)){
            String altQueryConfig = (String)initArgs.get(ALT_QUERY_CONFIG);
            if(StringUtils.isBlank(altQueryConfig)){
                String error = String.format("%s is specified but %s is not. Both parameters need to be specified to configure the alternate query", QUERY_CONFIG_PARAM, ALT_QUERY_CONFIG);
                Log.error(error);
            }
            else{
                this.queryConfigParam = qConfigParam;
                this.altQueryConfig = altQueryConfig;
            }
        }
        Boolean parsingEnabled = (Boolean)initArgs.get(PARSING_ENABLED);
        if(parsingEnabled != null){
            this.configParamParsingEnabled = parsingEnabled;
        }
    }

    private SimpleOrderedMap buildParsedDebugInfo(RecursiveDescentParser parser, String parsedQuery) {

        SimpleOrderedMap responseInfo = new SimpleOrderedMap();
        responseInfo.add("parsedQuery", parsedQuery);
        responseInfo.add("hasErrors", parser.hasErrors());
        if(parser.hasErrors()){
            StringBuilder errorList = new StringBuilder();
            for(int error : parser.getErrors()){
                errorList.append(error).append(",");
            }
            final String sListErrors = errorList.toString();
            responseInfo.add("errorCodes", sListErrors.substring(0, sListErrors.length()-1));
        }
        return responseInfo;
    }

    @Override
    public void prepare( ResponseBuilder rb )
    {
        if(this.queryParameter == null || this.queryConfigParam == null) {
            return;
        }

        try {
            SolrQueryRequest req = rb.req;
            SolrParams params = req.getParams( );

            String userQuery = params.get( this.queryParameter );

            Boolean isParsingEnabled = params.getBool(PARSING_ENABLED);
            if(isParsingEnabled == null){
                isParsingEnabled = this.configParamParsingEnabled;
            }

            QueryLexer lexer = new QueryLexer(userQuery);
            ModifiableSolrParams modParams = new ModifiableSolrParams( params );
            // replace Query template with alternate template for handling advanced user queries (containing booleans or phrases)
            SimpleOrderedMap responseInfo = new SimpleOrderedMap();
            rb.rsp.add("diceBooleanQueryParser", responseInfo);

            if(this.queryConfigParam != null && lexer.isAdvancedQuery()){
                modParams.set(this.queryConfigParam, this.altQueryConfig);
                responseInfo.add("isComplexQuery", true);
            }
            else{
                responseInfo.add("isComplexQuery", false);
            }

            // Solr does not parse boolean queries according to operator precedence when AND's and OR's are present.
            // When AND's are present, re-parse user query using a custom boolean logic parser
            if(isParsingEnabled && lexer.isAndQuery()){
                RecursiveDescentParser parser = new RecursiveDescentParser(lexer, "*:*");
                Expression ast = parser.parse();
                if(ast != null)
                {
                    final String parsedQuery = ast.evaluate();
                    modParams.set( this.queryParameter, parsedQuery);
                    responseInfo.add("parserOutput", buildParsedDebugInfo(parser, parsedQuery));
                }
            }
            req.setParams( modParams );
        }
        catch (Exception ex){
            Log.error(String.format("Error thrown in class %s while pre-processing user query", BooleanQueryPreProcessorComponent.class.getName()), ex);
        }
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException
    {
        // do nothing - needed so we don't execute the query here.
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
