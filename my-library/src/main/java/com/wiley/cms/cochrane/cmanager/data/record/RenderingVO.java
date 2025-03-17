package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 17.10.13
 */
public class RenderingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int planId;
    private int recordId;
    private boolean completed;
    private boolean successful;

    public RenderingVO(RenderingEntity re) {
        id = re.getId();
        planId = re.getRenderingPlan().getId();
        recordId = re.getRecord().getId();
        completed = re.isCompleted();
        successful = re.isSuccessful();
    }

    public final int getId() {
        return id;
    }

    public final int getPlanId() {
        return planId;
    }

    public final int getRecordId() {
        return recordId;
    }

    public final boolean isCompleted() {
        return completed;
    }

    public final boolean isSuccessful() {
        return successful;
    }
}
