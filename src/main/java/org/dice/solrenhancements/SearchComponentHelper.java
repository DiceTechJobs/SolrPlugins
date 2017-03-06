package org.dice.solrenhancements;

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.ExtendedDismaxQParserPlugin;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SyntaxError;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 10/14/16.
 */
public final class SearchComponentHelper {
    private final static String EDISMAX = ExtendedDismaxQParserPlugin.NAME;

    private static String SPACE = " ";
    public static String[] parseFieldsParameter(String fieldList) {
        List<String> tmp = new ArrayList<String>();

        String delim = ",";
        if(fieldList.contains(SPACE)){
            delim = SPACE;
        }

        for(String s: fieldList.trim().split(delim)){
            String val = s.trim();
            if(val.length() > 0){
                tmp.add(val);
            }
        }
        String[] tmpArray = new String[tmp.size()];
        tmp.toArray(tmpArray);
        return tmpArray;
    }

    public static FieldType getFieldTypeByName(String fieldTypeName, IndexSchema indexSchema){
        return indexSchema.getFieldTypeByName(fieldTypeName);
    }

    public static FieldType getFieldType(String fieldName, IndexSchema indexSchema){
        SchemaField field = indexSchema.getField(fieldName);
        return field.getType();
    }

    public static Query parseQuery(SolrQueryRequest request, String q) throws SyntaxError {
        final SolrParams params = request.getParams();
        String defType = params.get(QueryParsing.DEFTYPE, EDISMAX);
        QParser parser = QParser.getParser(q, defType, request);
        return parser.getQuery();
    }

    public static Query parseQuery(SolrQueryRequest request, String q, String defType) throws SyntaxError {
        QParser parser = QParser.getParser(q, defType, request);
        return parser.getQuery();
    }
}
