package com.wiley.cms.cochrane.cmanager.data.rendering;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/28/2019
 */
@Entity
@Table(name = "COCHRANE_RENDERING")
public class RenderingEntityPlain extends DbEntity {

    private Integer recordId;
    private Integer planId;

    public RenderingEntityPlain() {
    }

    public RenderingEntityPlain(Integer recordId, Integer planId) {
        this.recordId = recordId;
        this.planId = planId;
    }

    @Column(name = "record_id")
    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    @Column(name = "plan_id")
    public Integer getRenderingPlanId() {
        return planId;
    }

    public void setRenderingPlanId(Integer renderingPlanId) {
        planId = renderingPlanId;
    }
}
