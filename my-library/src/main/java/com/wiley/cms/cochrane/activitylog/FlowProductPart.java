package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.snowflake.ISFProductPart;
import com.wiley.cms.cochrane.activitylog.snowflake.SFType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/16/2021
 */
public abstract class FlowProductPart implements IFlowProduct, Serializable {
    private static final long serialVersionUID = 1L;

    private String sid;
    private String version;
    private final String pubName;
    private final String doi;
    private Integer entityId = DbEntity.NOT_EXIST_ID;
    private String title;
    private Integer titleId;
    private FlowProduct.State state = FlowProduct.State.NONE;

    private final Map<String, String> rawData = new LinkedHashMap<>();

    FlowProductPart(String pubName, String doi, Integer dfId) {
        this.pubName = pubName;
        this.doi  = doi;

        SFType.FLOW.setType(Now.START_DATE, rawData);
        SFType.setFlowEventId(null, rawData);
        setPackageId(dfId);
    }

    FlowProductPart(String pubName, String doi, String language, Integer dfId) {
        this(pubName, doi, dfId);

        SFType.setLanguage(language, rawData);
    }

    public void setPackageId(Integer dfId) {
        SFType.setPackageId(dfId, rawData);
    }

    @Override
    public Integer getTitleId() {
        return titleId;
    }

    @Override
    public void setTitleId(Integer titleId) {
        this.titleId = titleId;

    }

    public void setTitleId(Integer titleId, String title) {
        if (titleId != null) {
            setTitleId(titleId);
        }
        if (title != null) {
            setTitle(title);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    @Override
    public String getPubCode() {  
        return pubName;
    }

    @Override
    public String getDOI() {
        return doi;
    }

    @Override
    public String getParentDOI() {
        return Constants.NA;
    }

    @Override
    public String getSID() {
        return sid;
    }

    public void setSID(String sid) {
        this.sid = sid;
    }

    @Override
    public String getCochraneVersion() {
        return version;
    }

    @Override
    public void setCochraneVersion(String version) {
        this.version = version;
    }

    @Override
    public String getPublicationType() {
        return null;
    }

    @Override
    public String getSourceStatus() {
        return Constants.TR;
    }

    @Override
    public String getLanguage() {
        return rawData.get(ISFProductPart.P_TR_LANGUAGE);
    }

    @Override
    public FlowProduct.State getState() {
        return state;
    }

    @Override
    public void setState(FlowProduct.State state) {
        this.state = state;
    }

    @Override
    public Map<String, String> getRawData() {
        return rawData;
    }

    @Override
    public String toString() {
        return String.format("%s [%d] %s", pubName, entityId, state);
    }
}
