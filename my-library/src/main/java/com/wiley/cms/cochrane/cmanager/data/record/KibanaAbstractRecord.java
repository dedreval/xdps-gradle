package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.IFlowProduct;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 1.2
 * @since 30.04.2020
 */
public abstract class KibanaAbstractRecord implements IKibanaRecord, Serializable {
    private static final long serialVersionUID = 1L;

    protected String name;
    protected String litPackageName;
    protected String hwPackageName;
    protected String dsPackageName;

    protected final FlowProduct product;

    protected KibanaAbstractRecord(String cdNumber, int pubNumber, String dfName, int dfId, String vendor) {
        this.name = cdNumber;
        String dbName = RecordHelper.getDbNameByRecordNumber(RecordHelper.buildRecordNumber(cdNumber));
        BaseType bt = BaseType.find(dbName).get();
        String pubName = bt.getProductType().buildPublisherId(cdNumber, pubNumber);
        product = new FlowProduct(pubName, bt.getProductType().buildDoi(pubName), dfId, bt, vendor, this);
        product.setPackageName(dfName);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTitle() {
        return product.getTitle();
    }

    @Override
    public void setTitle(String title) {
        product.setTitle(title);
    }

    @Override
    public String getVendor() {
        return product.getVendor();
    }

    @Override
    public String getDfName() {
        String dfName = product.getPackageName();
        return dfName != null ? dfName : "";
    }

    @Override
    public Integer getDfId() {
        return product.getSourcePackageId();
    }

    @Override
    public String getLitPackageName() {
        return litPackageName != null ? litPackageName : "";
    }

    @Override
    public void setLitPackageName(String litPackageName) {
        this.litPackageName = litPackageName;
    }

    @Override
    public String getHwPackageName() {
        return hasHwPackageName() ? hwPackageName : "";
    }

    @Override
    public boolean hasHwPackageName() {
        return hwPackageName != null;
    }

    @Override
    public void setHwPackageName(String hwPackageName) {
        this.hwPackageName = hwPackageName;
    }

    @Override
    public String getDsPackageName() {
        return dsPackageName != null ? dsPackageName : "";
    }

    @Override
    public void setDsPackageName(String dsPackageName) {
        this.dsPackageName = dsPackageName;
    }

    @Override
    public FlowProduct getFlowProduct() {
        return product;
    }

    @Override
    public IFlowProduct getFlowProduct(String language) {
        return language == null ? product : product.getTranslation(language);
    }
}
