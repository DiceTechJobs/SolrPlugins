package org.dice.solrenhancements.jointprobability.searchcomponents;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.dice.solrenhancements.JarVersion;
import org.dice.solrenhancements.jointprobability.JointCounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Created by simon.hughes on 12/21/15.
 */
public class JointProbabilityComponent extends JointProbabilityComponentBase {

    private static final Logger log = LoggerFactory.getLogger( JointProbabilityComponent.class );

    // parameters
    private String[] dfltFields = null;
    private int dfltMinCount = 2;
    private int dfltLimit = -1;

    @Override
    protected String getPrefix() {
        return "jointprob";
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {

        if (isEnabled(rb)) {
            rb.setNeedDocSet(true);

            // De-duplicate field list
            ModifiableSolrParams params = new ModifiableSolrParams();
            SolrParams origParams = rb.req.getParams();
            Iterator<String> iter = origParams.getParameterNamesIterator();
            while (iter.hasNext()) {
                String paramName = iter.next();
                // Deduplicate the list with LinkedHashSet, but _only_ for facet params.
                if (!paramName.equals(JointProbabilityParams.FIELDS)) {
                    params.add(paramName, origParams.getParams(paramName));
                    continue;
                }
                HashSet<String> deDupe = new LinkedHashSet<String>(Arrays.asList(origParams.getParams(paramName)));
                params.add(paramName, deDupe.toArray(new String[deDupe.size()]));
            }
            rb.req.setParams(params);
        }
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        if(isEnabled(rb)){
            long startTime = System.currentTimeMillis();

            SolrParams params = rb.req.getParams();
            Integer minCount = getMinCountParameter(params);
            Integer limit = getLimitParameter(params);
            String[] fields = getFieldsParameter(params);

            JointCounts jointProb = new JointCounts(rb.req, rb.getResults().docSet, minCount, limit, fields);
            NamedList<Object> counts = jointProb.process();

            long duration = System.currentTimeMillis() - startTime;

            NamedList<Object> results = new NamedList<Object>();
            results.add("Time", duration);
            results.add("counts", counts);
            rb.rsp.add("joint_probabilities", results);
        }
    }

    @Override
    public String getDescription() {
        return String.format("Dice %s for computing joint probabilities", JointProbabilityComponent.class.getSimpleName());
    }

    private String version = null;

    @Override
    public String getVersion() {
        if (version != null) {
            return version;
        }

        version = JarVersion.getVersion(log);
        return version;
    }
    
    @Override
    protected String[] getFieldsParameter(SolrParams params) {
        String[] fields = super.getFieldsParameter(params);
        if(fields.length == 1){
            String[] newFields = new String[2];
            newFields[0] = fields[0];
            newFields[1] = fields[0];
            return newFields;
        }
        return fields;
    }


}
