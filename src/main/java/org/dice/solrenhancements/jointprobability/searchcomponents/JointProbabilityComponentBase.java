package org.dice.solrenhancements.jointprobability.searchcomponents;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;

import java.util.List;

/**
 * Created by simon.hughes on 1/15/16.
 */
public abstract class JointProbabilityComponentBase extends SearchComponent {

    protected String[] fields = null;
    protected int dfltMinCount = 2;
    protected int dfltLimit = -1;

    @Override
    public void init( NamedList args){

        String fullPrefix = getPrefix() + ".";
        List<String> lFields = args.getAll(fullPrefix + JointProbabilityParams.FIELDS);
        if(lFields.size() > 0){
            this.fields = lFields.toArray(new String[lFields.size()]);
        }

        Object oFreq = args.get(fullPrefix + JointProbabilityParams.MIN_COUNT);
        if(oFreq != null && oFreq instanceof Integer){
            this.dfltMinCount = (Integer)oFreq;
        }

        Object oLimit = args.get(fullPrefix + JointProbabilityParams.LIMIT);
        if(oLimit != null && oLimit instanceof Integer){
            this.dfltLimit = (Integer)oLimit;
        }
    }


    protected String[] getFieldsParameter(SolrParams params) {
        final String paramName = getPrefix() + "." + JointProbabilityParams.FIELDS;

        String[] fields = params.getParams(paramName);
        if(fields == null || fields.length == 0){
            fields = this.fields;
        }
        if(fields == null || fields.length == 0){
            throw new SolrException(
                    SolrException.ErrorCode.BAD_REQUEST,
                    String.format("%s is a required parameter", paramName)
            );
        }

        for(int i = 0; i<fields.length; i++){
            String fld = fields[i];
            // if a single field, make a pair
            if(fld.split(",").length == 0){
                throw new SolrException (SolrException.ErrorCode.BAD_REQUEST,"No fields specified");
            }
        }
        return fields;
    }

    protected int getMinCountParameter(SolrParams params) {
        return params.getInt(getPrefix() + "." + JointProbabilityParams.MIN_COUNT, dfltMinCount);
    }

    protected int getLimitParameter(SolrParams params) {
        return params.getInt(getPrefix() + "." + JointProbabilityParams.LIMIT, this.dfltLimit);
    }

    protected boolean isEnabled(ResponseBuilder rb) {
        return rb.req.getParams().getBool(getPrefix(), false);
    }

    protected abstract String getPrefix();
}
