package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
public class EntireRecordVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id = 0;

    private String name;

    private String unitTitle;

    private Integer dbId;
    private int lastIssue = 0;

    private Integer unitStatusId = 0;
    private Integer productSubtitleId = 0;

    public EntireRecordVO() {
    }

    public EntireRecordVO(EntireDBEntity entity) {

        setId(entity.getId());
        setName(entity.getName());
        setUnitTitle(entity.getUnitTitle());
        if (entity.getUnitStatus() != null) {
            setUnitStatusId(entity.getUnitStatus().getId());
        }
        if (entity.getProductSubtitle() != null) {
            setProductSubtitleId(entity.getProductSubtitle().getId());
        }
        lastIssue = entity.getLastIssuePublished();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnitTitle() {
        return unitTitle;
    }

    public void setUnitTitle(String unitTitle) {
        this.unitTitle = unitTitle;
    }

    public Integer getDbId() {
        return dbId;
    }

    public void setDbId(Integer dbId) {
        this.dbId = dbId;
    }

    public Integer getUnitStatusId() {
        return unitStatusId;
    }

    public void setUnitStatusId(Integer unitStatusId) {
        this.unitStatusId = unitStatusId;
    }

    public Integer getProductSubtitleId() {
        return productSubtitleId;
    }

    public void setProductSubtitleId(Integer productSubtitleId) {
        this.productSubtitleId = productSubtitleId;
    }

    public int getLastIssue() {
        return lastIssue;
    }

    @Override
    public String toString() {
        return String.format("%s [%d] %d", name, id, lastIssue);
    }
}
