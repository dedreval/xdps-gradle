package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class CDSRVO implements Serializable, IRecordVO {

    RecordVO record;

    private Integer id;
    private Integer recordId;
    private String recordName;
    private String doi;
    private boolean isUpdated;
    private boolean isCurrent;
    private Integer versionNumber;
    private Integer issueId;

    private boolean isMethodology;
    private String reviewType;
    private boolean isOpenAccess;

    private String groupSid;

    public CDSRVO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getGroupSid() {
        return groupSid;
    }

    public void setGroupSid(String groupSid) {
        this.groupSid = groupSid;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public boolean isUpdated() {
        return isUpdated;
    }

    public void setUpdated(boolean updated) {
        isUpdated = updated;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Integer getIssueId() {
        return issueId;
    }

    public void setIssueId(Integer issueId) {
        this.issueId = issueId;
    }

    public boolean isMethodology() {
        return isMethodology;
    }

    public void setMethodology(boolean methodology) {
        isMethodology = methodology;
    }

    public RecordVO getRecord() {
        return record;
    }

    public void setRecord(RecordVO record) {
        this.record = record;
    }


    public String getName() {
        return record.getName();
    }

    public void setName(String name) {
        record.setName(name);
    }

    public String getUnitTitle() {
        return record.getUnitTitle();
    }

    public void setUnitTitle(String unitTitle) {
        record.setUnitTitle(unitTitle);
    }

    public boolean isQasCompleted() {
        return record.isQasCompleted();
    }

    public void setQasCompleted(boolean qasCompleted) {
        record.setQasCompleted(qasCompleted);
    }

    public boolean isQasSuccessful() {
        return record.isQasSuccessful();
    }

    public void setQasSuccessful(boolean qasSuccessful) {
        record.setQasSuccessful(qasSuccessful);
    }

    public boolean isRenderingCompleted() {
        return record.isRenderingCompleted();
    }

    public void setRenderingCompleted(boolean renderingCompleted) {
        record.setRenderingCompleted(renderingCompleted);
    }

    public boolean isRenderingSuccessful() {
        return record.isRenderingSuccessful();
    }

    public void setRenderingSuccessful(boolean renderingSuccessful) {
        record.setRenderingSuccessful(renderingSuccessful);
    }

    public int getDbId() {
        return record.getDbId();
    }

    public void setDbId(int dbId) {
        record.setDbId(dbId);
    }

    public Integer getDeliveryFileId() {
        return record.getDeliveryFileId();
    }

    public void setDeliveryFileId(Integer deliveryFileId) {
        record.setDeliveryFileId(deliveryFileId);
    }

    public boolean isApproved() {
        return record.isApproved();
    }

    public void setApproved(boolean approved) {
        record.setApproved(approved);
    }

    public boolean isDisabled() {
        return record.isDisabled();
    }

    public void setDisabled(boolean disabled) {
        record.setDisabled(disabled);
    }

    public boolean isRawDataExists() {
        return record.isRawDataExists();
    }

    public void setRawDataExists(boolean rawDataExists) {
        record.setRawDataExists(rawDataExists);
    }

    public String getStateDescription() {
        return record.getStateDescription();
    }

    public void setStateDescription(String stateDescription) {
        record.setStateDescription(stateDescription);
    }

    public String getNotes() {
        return record.getNotes();
    }

    public void setNotes(String notes) {
        record.setNotes(notes);
    }

    public String getRecordPath() {
        return record.getRecordPath();
    }

    public void setRecordPath(String recordPath) {
        record.setRecordPath(recordPath);
    }

    public boolean isEdited() {
        return record.isEdited();
    }

    public void setEdited(boolean edited) {
        record.setEdited(edited);
    }

    public UnitStatusVO getUnitStatus() {
        return record.getUnitStatus();
    }

    public void setUnitStatus(UnitStatusVO unitStatus) {
        record.setUnitStatus(unitStatus);
    }

    public ProductSubtitleVO getProductSubtitle() {
        return record.getProductSubtitle();
    }

    public void setProductSubtitle(ProductSubtitleVO productSubtitle) {
        record.setProductSubtitle(productSubtitle);
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public boolean getOpenAccess() {
        return isOpenAccess;
    }

    public void setOpenAccess(boolean open) {
        isOpenAccess = open;
    }
}
