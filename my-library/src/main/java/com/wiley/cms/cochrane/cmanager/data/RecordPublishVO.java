package com.wiley.cms.cochrane.cmanager.data;

import java.util.Date;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 26.11.2013
 */
public class RecordPublishVO implements IRecord {

    private int id;
    private String name;
    private Date date;
    private int state;
    private int recordId;
    private boolean unchanged = true;
    private Set<String> languages;
    private Integer subTitle;
    private Integer unitStatus;

    private Integer dfId = null;

    public RecordPublishVO() {
    }

    public RecordPublishVO(RecordPublishEntity entity) {
        id = entity.getId();
        name = entity.getName();
        date = entity.getDate();
        state = entity.getState();
        recordId = (entity instanceof DbRecordPublishEntity
                            ? ((DbRecordPublishEntity) entity).getRecord().getId()
                            : ((EntireRecordPublishEntity) entity).getRecord().getId());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getRecordId() {
        return recordId;
    }

    public void setRecordId(int recordId) {
        this.recordId = recordId;
    }

    public Integer getDeliveryFileId() {
        return dfId;
    }

    public void setDeliveryFileId(Integer dfId) {
        if (DbUtils.exists(dfId)) {
            this.dfId = dfId;
        }
    }

    public void setUnchanged(boolean value) {
        unchanged = value;
    }

    public boolean isUnchanged() {
        return unchanged;
    }

    @Override
    public void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    @Override
    public Set<String> getLanguages() {
        return languages;
    }

    @Override
    public Integer getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(Integer subTitle) {
        this.subTitle = subTitle;
    }

    @Override
    public boolean isStageR() {
        return subTitle != null && ProductSubtitleEntity.ProductSubtitle.REVIEWS == subTitle;
    }

    @Override
    public Integer getUnitStatusId() {
        return unitStatus;
    }

    public void setUnitStatusId(Integer unitStatus) {
        this.unitStatus = unitStatus;
    }

    public boolean isWithdrawn() {
        return UnitStatusEntity.isWithdrawn(unitStatus);
    }
}
