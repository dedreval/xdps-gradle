package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ActivityLogSearchParameters implements Serializable {
    private int beginIndex;
    private int amount;
    private int orderField;
    private boolean orderDesc;

    private int entityId;
    private ActivityLogEntity.EntityLevel entityLevel;

    public ActivityLogSearchParameters() {

    }

    public ActivityLogSearchParameters(int beginIndex,
                                       int amount,
                                       int orderField,
                                       boolean orderDesc) {
        this.beginIndex = beginIndex;
        this.amount = amount;
        this.orderField = orderField;
        this.orderDesc = orderDesc;
    }

    public ActivityLogEntity.EntityLevel getEntityLevel() {
        return entityLevel;
    }

    public void setEntityLevel(ActivityLogEntity.EntityLevel entityLevel) {
        this.entityLevel = entityLevel;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }


    public int getBeginIndex() {
        return beginIndex;
    }

    public void setBeginIndex(int beginIndex) {
        this.beginIndex = beginIndex;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getOrderField() {
        return orderField;
    }

    public void setOrderField(int orderField) {
        this.orderField = orderField;
    }

    public boolean isOrderDesc() {
        return orderDesc;
    }

    public void setOrderDesc(boolean orderDesc) {
        this.orderDesc = orderDesc;
    }
}
