package com.wiley.cms.cochrane.cmanager.data.record;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IRecordVO {
    Integer getId();

    void setId(Integer id);

    String getName();

    void setName(String name);

    String getUnitTitle();

    void setUnitTitle(String unitTitle);

    boolean isQasCompleted();

    void setQasCompleted(boolean qasCompleted);

    boolean isQasSuccessful();

    void setQasSuccessful(boolean qasSuccessful);

    boolean isRenderingCompleted();

    void setRenderingCompleted(boolean renderingCompleted);

    boolean isRenderingSuccessful();

    void setRenderingSuccessful(boolean renderingSuccessful);

    int getDbId();

    void setDbId(int dbId);

    Integer getDeliveryFileId();

    void setDeliveryFileId(Integer deliveryFileId);

    boolean isApproved();

    void setApproved(boolean approved);

    boolean isDisabled();

    void setDisabled(boolean disabled);

    boolean isRawDataExists();

    void setRawDataExists(boolean rawDataExists);

    String getStateDescription();

    void setStateDescription(String stateDescription);

    String getNotes();

    void setNotes(String notes);

    String getRecordPath();

    void setRecordPath(String recordPath);

    boolean isEdited();

    void setEdited(boolean edited);

    UnitStatusVO getUnitStatus();

    void setUnitStatus(UnitStatusVO unitStatus);

    ProductSubtitleVO getProductSubtitle();

    void setProductSubtitle(ProductSubtitleVO productSubtitle);

    //boolean getOpenAccess();
}
