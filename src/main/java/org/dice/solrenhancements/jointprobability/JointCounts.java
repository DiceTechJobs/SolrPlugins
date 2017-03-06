package org.dice.solrenhancements.jointprobability;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.request.DocValuesFacets;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.BoolField;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;

import java.io.IOException;
import java.util.*;

/**
 * Created by simon.hughes on 1/7/16.
 */
public class JointCounts extends SimpleFacets {
    private final SolrIndexSearcher searcher;
    private final DocSet docs;
    private final int minCount;
    private final int limit;
    private final String[] fields;

    private Map<String, FacetMethod> facetMethodPerField = new HashMap<String, FacetMethod>();
    private Map<String, SchemaField> cachedFields = new HashMap<String, SchemaField>();
    private Map<String, NamedList<Integer>> cachedFieldCounts = new HashMap<String, NamedList<Integer>>();

    public static final String VALUE = "value";
    public static final String COUNT = "count";

    enum FacetMethod {
        ENUM, FC, FCS;
    }

    public JointCounts(SolrQueryRequest req, DocSet docs, int minCount, int limit, String[] fields) {
        super(req, docs, req.getParams());

        this.searcher = req.getSearcher();
        this.docs = docs;

        this.minCount = minCount;
        this.limit = limit;
        this.fields = fields;
    }

    public NamedList<Object> process() throws IOException {
        if(this.docs == null){
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No documents matched to build joint probabilities");
        }
        NamedList<Object> pivots = new NamedList<Object>();

        for(String sPivotList: this.fields) {
            List<String> lPivotList = StrUtils.splitSmart(sPivotList, ',');
            // ensure at least 2 items
            if(lPivotList.size() == 1){
                lPivotList.add(0, lPivotList.get(0));
            }
            Deque<String> queue = new LinkedList<String>(lPivotList);
            String fieldName = queue.removeFirst();
            NamedList<Integer> facetCounts = this.getTermCounts(fieldName, fieldName, minCount, limit, this.docs);
            pivots.add(fieldName, doPivots(facetCounts, fieldName, fieldName, queue, this.docs));
        }
        return pivots;
    }

    protected List<NamedList<Object>> doPivots(NamedList<Integer> superFacets, String fieldName, String fieldPath,
                                               Deque<String> queue, DocSet docs) throws IOException {

        SchemaField field = getSchemaField(fieldName);
        FieldType ftype = field.getType();
        String subField = null;
        if( queue.size() > 0 )  {
            subField = queue.remove();
        }

        // re-usable BytesRefBuilder for conversion of term values to Objects
        BytesRefBuilder termval = new BytesRefBuilder();

        List<NamedList<Object>> values = new ArrayList<NamedList<Object>>(superFacets.size());
        for (Map.Entry<String, Integer> kv : superFacets) {
            // Only sub-facet if parent facet has positive count - still may not be any values for the sub-field though
            if (kv.getValue() >= this.minCount) {

                final String fieldValue = kv.getKey();
                final int pivotCount = kv.getValue();

                SimpleOrderedMap<Object> pivot = new SimpleOrderedMap<Object>();
                if (null == fieldValue) {
                    pivot.add( VALUE, null );
                } else {
                    ftype.readableToIndexed(fieldValue, termval);
                    pivot.add( VALUE, ftype.toObject(field, termval.get()) );
                }
                pivot.add( COUNT, pivotCount );

                if( subField != null )  {
                    final String newfieldPath = fieldPath + "|" + subField;
                    //TODO pass subset here
                    final DocSet subset = getSubset(docs, field, fieldValue);
                    NamedList<Integer> facetCounts= this.getTermCounts(subField, newfieldPath, minCount, limit, subset);
                    if (facetCounts.size() >= 1) {
                        // returns null if empty
                        pivot.add(subField, doPivots(facetCounts, subField, newfieldPath, queue, subset));
                    }
                }
                values.add( pivot );
            }
        }
        return values;
    }

    private FacetMethod getFacetMethod(String field){
        if(this.facetMethodPerField.containsKey(field)){
            return facetMethodPerField.get(field);
        }

        SchemaField sf = searcher.getSchema().getField(field);
        FieldType ft = sf.getType();

        // determine what type of faceting method to use
        FacetMethod method = FacetMethod.FC;

        if (ft instanceof BoolField) {
            // Always use filters for booleans... we know the number of values is very small.
            method = FacetMethod.ENUM;
        }

        /*
        final boolean multiToken = sf.multiValued() || ft.multiValuedFieldCache();
        if (ft.getNumericType() != null && !sf.multiValued()) {
            // the per-segment approach is optimal for numeric field types since there
            // are no global ords to merge and no need to create an expensive
            // top-level reader
            method = FacetMethod.FCS;
        }

        if (method == FacetMethod.FCS && multiToken) {
            // only fc knows how to deal with multi-token fields
            method = FacetMethod.FC;
        }
        */

        if (method == FacetMethod.ENUM && sf.hasDocValues()) {
            // only fc can handle docvalues types
            method = FacetMethod.FC;
        }
        synchronized (this.facetMethodPerField){
            this.facetMethodPerField.put(field, method);
        }
        return method;
    }

    private NamedList<Integer> getTermCounts(String field, String fieldPath, Integer mincount, int limit, DocSet docs) throws IOException {
        if(cachedFieldCounts.containsKey(fieldPath)){
            return cachedFieldCounts.get(fieldPath);
        }

        int offset = 0;
        if (limit == 0) return new NamedList<Integer>();

        String sort = FacetParams.FACET_SORT_COUNT;
        NamedList<Integer> counts;

        FacetMethod method = getFacetMethod(field);
        switch (method) {
            case ENUM:
                // intersectsCheck should be false, else facet counts will be capped at 1 (binary)
                counts = getFacetTermEnumCounts(searcher, docs, field, offset, limit, mincount,false,sort,null, null, false, null);
                break;
            case FC:
                counts = DocValuesFacets.getCounts(searcher, docs, field, offset, limit, mincount, false, sort, null, null, false);
                break;
            default:
                throw new AssertionError();
        }
        cachedFieldCounts.put(fieldPath, counts);
        return counts;
    }

    private SchemaField getSchemaField(String fieldName){
        if(cachedFields.containsKey(fieldName)){
            return cachedFields.get(fieldName);
        }
        SchemaField field = searcher.getSchema().getField(fieldName);
        cachedFields.put(fieldName, field);
        return field;
    }

    /**
     * Given a base docset, computes the subset of documents corresponding to the specified pivotValue
     *
     * @param base the set of documents to evaluate relative to
     * @param field the field type used by the pivotValue
     * @param pivotValue String representation of the value, may be null (ie: "missing")
     */
    private DocSet getSubset(DocSet base, SchemaField field, String pivotValue) throws IOException {
        FieldType ft = field.getType();
        if ( null == pivotValue ) {
            Query query = ft.getRangeQuery(null, field, null, null, false, false);
            DocSet hasVal = searcher.getDocSet(query);
            return base.andNot(hasVal);
        } else {
            Query query = ft.getFieldQuery(null, field, pivotValue);
            // for tokenizer chains that are not idempotent (values disappear
            // if passed thru twice) this blow up (same in facet module)
            // as query is null
            if(query == null) {
                // return empty DocSet
                return base.andNot(base);
            }
            return searcher.getDocSet(query, base);
        }
    }
}
