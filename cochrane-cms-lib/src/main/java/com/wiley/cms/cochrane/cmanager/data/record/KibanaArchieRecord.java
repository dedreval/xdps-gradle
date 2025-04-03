package com.wiley.cms.cochrane.cmanager.data.record;

import com.wiley.cms.cochrane.activitylog.IFlowProduct;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;

import java.io.Serializable;
import java.util.Set;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 1.2
 * @since 30.04.2020
 */
public class KibanaArchieRecord extends KibanaAbstractRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public KibanaArchieRecord(String cdNumber, int pubNumber, String dfName, Integer dfId, String vendor) {
        super(cdNumber, pubNumber, dfName, dfId, vendor);
    }

    public KibanaArchieRecord(ArchieEntry meta, String dfName, Integer dfId, String vendor) {
        super(meta.getName(), meta.getPubNumber(), dfName, dfId, vendor);
    }

    public KibanaArchieRecord(RecordEntity re, RecordMetadataEntity rme, DeliveryFileEntity dfe) {
        super(rme.getCdNumber(), rme.getPubNumber(), dfe.getName(), dfe.getId(), dfe.getVendor());
        setStage(rme.getStage());
        setTitle(re.getUnitTitleNormalized());
    }

    @Override
    public String getStatus() {
        return product.getSourceStatus() != null ? product.getSourceStatus() : "";
    }

    @Override
    public void setStatus(int statusId) {
        product.setSourceStatus(RecordMetadataEntity.RevmanStatus.getStatusName(statusId));
    }

    public boolean hasReview() {
        return product.getSourceStatus() != null;
    }

    @Override
    public String getPubName() {
        return product.getPubCode();
    }

    @Override
    public String getStage() {
        return product.getStage() != null ? product.getStage() : "";
    }

    @Override
    public void setStage(String stage) {
        if (product.getStage() == null) {
            product.setStage(stage);
        }
    }

    @Override
    public Set<String> getLanguages() {
        return product.getLanguages();
    }

    @Override
    public IFlowProduct addLanguage(String language) {
        IFlowProduct ret = product.getTranslation(language);
        return ret == null ? product.addTranslation(null, language, false) : ret;
    }

    @Override
    public boolean hasLanguages() {
        return product.hasLanguages();
    }
}
