package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RecordVO implements Serializable, IRecordVO, IGroupVO, IRecord {
    private static final long serialVersionUID = 1L;

    private Integer id;

    private String name;

    private String unitTitle;
    private boolean qasCompleted;
    private boolean qasSuccessful;

    private boolean renderingCompleted;
    private boolean renderingSuccessful;

    private int dbId;
    private Integer deliveryFileId;

    private boolean approved;
    private boolean disabled;
    private boolean isRawDataExists;

    private String stateDescription;
    private String notes;
    private String recordPath;

    private boolean edited;

    private UnitStatusVO unitStatus;
    private ProductSubtitleVO productSubtitle;
    private Integer subTitleId;
    private int state;
    private int insertTa = ITranslatedAbstractsInserter.INSERT_ENTIRE;
    private boolean wml3g;

    public RecordVO() {
    }

    public RecordVO(EntireDBEntity entity) {

        setId(entity.getId());
        setName(entity.getName());
        setUnitTitle(entity.getUnitTitle());
        setRecordPath(FilePathCreator.getFilePathToSourceEntire(entity.getDbName(), getName()));
        if (entity.getUnitStatus() != null) {
            setUnitStatus(entity.getUnitStatus().getVO());
        }
        if (entity.getProductSubtitle() != null) {
            setProductSubtitle(entity.getProductSubtitle().getVO());
            setSubTitle(entity.getProductSubtitle().getId());
        }
    }

    public RecordVO(RecordEntity entity) {

        setId(entity.getId());
        setName(entity.getName());
        setUnitTitle(entity.getUnitTitle());
        setQasCompleted(entity.isQasCompleted());
        setQasSuccessful(entity.isQasSuccessful());
        setRenderingCompleted(entity.isRenderingCompleted());
        setRenderingSuccessful(entity.isRenderingSuccessful());
        setDbId(entity.getDb().getId());
        setDeliveryFileId(entity.getDeliveryFile().getId());
        if (DeliveryFileEntity.isWml3g(entity.getDeliveryFile().getType())) {
            wml3g = true;
        }
        setApproved(entity.getApproved());
        setDisabled(entity.isDisabled());
        setRawDataExists(entity.isRawDataExists());
        setStateDescription(entity.getStateDescription());
        setNotes(entity.getNotes());
        setRecordPath(entity.getRecordPath());
        setEdited(entity.isEdited());
        if (entity.getUnitStatus() != null) {
            setUnitStatus(entity.getUnitStatus().getVO());
        }
        if (entity.getProductSubtitle() != null) {
            setProductSubtitle(entity.getProductSubtitle().getVO());
            setSubTitle(entity.getProductSubtitle().getId());
        }
        setState(entity.getState());
    }

    public void setInsertTa(int insertTa) {
        this.insertTa = insertTa;
    }

    public boolean insertTaFromEntire() {
        return insertTa == ITranslatedAbstractsInserter.INSERT_ENTIRE;
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

    public boolean isQasCompleted() {
        return qasCompleted;
    }

    public void setQasCompleted(boolean qasCompleted) {
        this.qasCompleted = qasCompleted;
    }

    public boolean isQasSuccessful() {
        return qasSuccessful;
    }

    public void setQasSuccessful(boolean qasSuccessful) {
        this.qasSuccessful = qasSuccessful;
    }

    public boolean isRenderingCompleted() {
        return renderingCompleted;
    }

    public void setRenderingCompleted(boolean renderingCompleted) {
        this.renderingCompleted = renderingCompleted;
    }

    public boolean isRenderingSuccessful() {
        return renderingSuccessful;
    }

    public void setRenderingSuccessful(boolean renderingSuccessful) {
        this.renderingSuccessful = renderingSuccessful;
    }

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public Integer getDeliveryFileId() {
        return deliveryFileId;
    }

    public void setDeliveryFileId(Integer deliveryFileId) {
        this.deliveryFileId = deliveryFileId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isRawDataExists() {
        return isRawDataExists;
    }

    public void setRawDataExists(boolean rawDataExists) {
        isRawDataExists = rawDataExists;
    }

    public String getStateDescription() {
        return stateDescription;
    }

    public void setStateDescription(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String getRecordPath() {
        return recordPath;
    }

    public void setRecordPath(String recordPath) {
        this.recordPath = recordPath;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public UnitStatusVO getUnitStatus() {
        return unitStatus;
    }

    public void setUnitStatus(UnitStatusVO unitStatus) {
        this.unitStatus = unitStatus;
    }

    @Override
    public Integer getUnitStatusId() {
        return unitStatus != null ? unitStatus.getId() : null;
    }

    @Override
    public boolean isUnchanged() {
        return unitStatus != null && UnitStatusEntity.isUnchanged(unitStatus.getId());
    }

    public ProductSubtitleVO getProductSubtitle() {
        return productSubtitle;
    }

    public void setProductSubtitle(ProductSubtitleVO productSubtitle) {
        this.productSubtitle = productSubtitle;
    }

    @Override
    public Integer getSubTitle() {
        return subTitleId;
    }

    private void setSubTitle(Integer subTitle) {
        this.subTitleId = subTitle;
    }

    public boolean isWml3g() {
        return wml3g;
    }
}
