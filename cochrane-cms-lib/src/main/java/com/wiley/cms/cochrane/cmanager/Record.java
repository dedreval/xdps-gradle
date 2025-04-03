package com.wiley.cms.cochrane.cmanager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.process.ProcessHelper;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 10-Jan-2007
 */
public class Record implements Serializable, IRecord {
    private static final long serialVersionUID = 1L;

    private int id;

    private String name;
    private boolean isSuccessful = true;
    private String title;

    private List<String> filesList;
    private String messages;
    private boolean isCompleted;
    private Integer jobPartId;
    private String recordSourceUri;
    private Integer unitStatus;
    private boolean meth;
    //private int group;
    private String groupSid;
    private Integer subTitle;
    private String reviewType;
    private String qasErrorCause;
    private boolean isDeleted = false;
    private boolean isRawExist = false;
    private boolean converted;

    private Integer history;
    private Integer dfType;
    private Integer dfId;
    private Set<String> languages;
    private Boolean jats = null;

    public Record(String name, boolean isCompleted) {
        this.name = name;
        setCompleted(isCompleted);
    }

    public Record(RecordEntity record) {

        this(record.getName(), record.isQasSuccessful() && record.isRenderingSuccessful());

        if (record.getId() != null) {
            setId(record.getId());
        }
        setUnitStatusId(record.getUnitStatus() == null ? null : record.getUnitStatus().getId());
        setTitle(record.getUnitTitle());
        setSubTitle(record.getProductSubtitle() == null ? null : record.getProductSubtitle().getId());
        setRecordSourceUri(record.getRecordPath());
        setRawExist(record.isRawDataExists());

        setDeliveryFileType(record.getDeliveryFile().getType());
        setDeliveryFileId(record.getDeliveryFile().getId());

        RecordMetadataEntity rme = record.getMetadata();
        if (rme != null) {
            Integer historyNumber = rme.getHistoryNumber();
            if (historyNumber != null && historyNumber > RecordEntity.VERSION_LAST) {
                // this is historical article
                setHistoryNumber(historyNumber);
            }
            setJats(rme.isJats());
        }
    }

    public Record(EntireDBEntity record) {

        this(record.getName(), true);

        if (record.getId() != null) {
            setId(record.getId());
        }
        if (record.getUnitStatus() != null) {
            setUnitStatusId(record.getUnitStatus().getId());
        }
        setTitle(record.getUnitTitle());
        setSubTitle(record.getProductSubtitle() == null ? null : record.getProductSubtitle().getId());
        setRecordSourceUri(FilePathCreator.getFilePathToSourceEntire(record.getDbName(), record.getName()));
    }

    public Integer getDeliveryFileType() {
        return dfType;
    }

    public void setDeliveryFileType(int dfType) {
        this.dfType = dfType;
    }

    public Integer getDeliveryFileId() {
        return dfId;
    }

    public void setDeliveryFileId(Integer dfId) {
        this.dfId = dfId;
    }

    @Override
    public boolean insertTaFromEntire() {
        return dfType == null || DeliveryFileEntity.isDefault(dfType);
    }

    public boolean getMeth() {
        return meth;
    }

    public void setMeth(boolean meth) {
        this.meth = meth;
    }

    public String getGroupSid() {
        return groupSid;
    }

    public void setGroupSid(String group) {
        this.groupSid = group;
    }

    public Integer getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(Integer subTitle) {
        this.subTitle = subTitle;
    }

    public Integer getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public boolean isRawExist() {
        return isRawExist;
    }

    public void setRawExist(boolean  exist) {
        isRawExist = exist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getFilesList() {
        return filesList;
    }

    public void setFilesList(List<String> filesList) {
        this.filesList = filesList;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(String messages) {
        this.messages = messages;
    }

    public void addMessages(String messages) {

        String newMessages = ProcessHelper.addErrorReportMessageXml(messages, this.messages);
        if (newMessages != null) {
            this.messages = newMessages;
        } else {
            this.messages = ProcessHelper.buildErrorReportMessageXml(name, messages);
        }
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public void setDeleted() {
        isDeleted = true;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public boolean isUnchanged() {
        return unitStatus != null && UnitStatusEntity.isUnchanged(unitStatus);
    }

    public boolean isWithdrawn() {
        return UnitStatusEntity.isWithdrawn(unitStatus)
                || (UnitStatusEntity.isSystem(unitStatus) && isWithdrawnBefore());
    }

    private boolean isWithdrawnBefore() {
        ICDSRMeta meta = ResultStorageFactory.getFactory().getInstance().findLatestMetadata(name, false);
        return meta != null && meta.isWithdrawn();
    }

    public boolean isStageR() {
        return subTitle != null && ProductSubtitleEntity.ProductSubtitle.REVIEWS == subTitle;
    }

    public Integer getJobPartId() {
        return jobPartId;
    }

    public void setJobPartId(Integer jobPartId) {
        this.jobPartId = jobPartId;
    }

    public boolean isConverted() {
        return converted;
    }

    public void setConverted(boolean converted) {
        this.converted = converted;
    }

    public void addUri(String filePath) {
        if (filesList == null) {
            filesList = new ArrayList<String>();
        }
        filesList.add(filePath);
    }

    public void setSuccessful(boolean successful, String message) {
        isSuccessful = successful;
        messages = message;
    }

    public void setSuccessful(boolean successful) {
        if (this.isSuccessful()) {
            isSuccessful = successful;
        }
    }

    public String getRecordSourceUri() {
        return recordSourceUri;
    }

    @Override
    public String getRecordPath() {
        return getRecordSourceUri();
    }

    public void setRecordSourceUri(String recordSourceUri) {
        this.recordSourceUri = recordSourceUri;
    }

    public Integer getUnitStatus() {
        return unitStatus;
    }

    public void setUnitStatusId(Integer unitStatus) {
        this.unitStatus = unitStatus;
    }

    @Override
    public Integer getUnitStatusId() {
        return unitStatus;
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public String getQasErrorCause() {
        return qasErrorCause;
    }

    public void setQasErrorCause(String qasErrorCause) {
        this.qasErrorCause = qasErrorCause;
    }

    public String getErrorMessages() {
        return ProcessHelper.parseErrorReportMessageXml(messages, true);
    }

    public void setHistoryNumber(Integer history) {
        this.history = history;
    }

    @Override
    public Integer getHistoryNumber() {
        return history;
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
    public boolean isJats() {
        if (jats == null) {
            throw new RuntimeException("a 'jats' field is not initialized");
        }
        return jats.booleanValue();
    }

    public void setJats(boolean value) {
        jats = Boolean.valueOf(value);
    }
}

