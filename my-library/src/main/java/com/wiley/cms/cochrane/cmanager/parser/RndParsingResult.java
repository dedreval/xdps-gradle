package com.wiley.cms.cochrane.cmanager.parser;

import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 28-Apr-2007
 */
public class RndParsingResult extends ParsingResult {

    private RenderingPlan plan;
    private int[] jobPartIds;

    public int[] getJobPartIds() {
        return jobPartIds;
    }

    public void setJobPartIds(int[] jobPartIds) {
        this.jobPartIds = jobPartIds;
    }

    public RenderingPlan getPlan() {
        return plan;
    }

    public void setPlan(RenderingPlan plan) {
        this.plan = plan;
    }
}
