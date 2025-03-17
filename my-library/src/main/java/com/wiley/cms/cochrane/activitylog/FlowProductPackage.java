package com.wiley.cms.cochrane.activitylog;

import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/17/2022
 */
public class FlowProductPackage extends FlowProductPart {
    private static final long serialVersionUID = 1L;

    private final BaseType baseType;
    private final String vendor;

    private Integer dfId;
    private Integer eventRecords;
    private Integer totalRecords;
    private String packageName;

    private String transactionId;

    protected FlowProductPackage(String pubName, String doi, Integer dfId, BaseType baseType, String vendor) {
        super(pubName, doi, dfId);

        this.baseType = baseType;
        this.vendor = vendor;
    }

    public FlowProductPackage(Integer dfId, String dfName, BaseType baseType, String vendor, String transactionId,
                              Integer... counters) {
        super(baseType.getProductType().getParentDOI(), baseType.getProductType().getParentDOI(), dfId);

        this.baseType = baseType;
        this.vendor = vendor;

        setPackageName(dfName);
        setTransactionId(transactionId);

        if (counters != null && counters.length > 1) {
            setEventRecords(counters[0], counters[1]);
        }
    }

    public boolean toDashboard() {
        return baseType.hasSFLogging();
    }

    @Override
    public void setPackageId(Integer dfId) {
        this.dfId = dfId;
        super.setPackageId(dfId);
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public void setTransactionId(String value) {
        transactionId = value;
    }

    @Override
    public String getType() {
        return baseType.getProductType().getType(baseType);
    }


    @Override
    public String getParentDOI() {
        return baseType.getProductType().getParentDOI();
    }

    @Override
    public Integer getSourcePackageId() {
        return dfId;
    }

    @Override
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getVendor() {
        return vendor;
    }

    @Override
    public String getSourceStatus() {
        return null;
    }

    @Override
    public String getFrequency() {
        // currently it is used only for CENTRAL
        return baseType.isCentral() ? HWFreq.BULK.getValue() : super.getFrequency();
    }

    @Override
    public Integer getEventRecords() {
        return eventRecords;
    }

    @Override
    public void setEventRecords(Integer eventValue, Integer totalValue) {
        eventRecords = eventValue;
        totalRecords = totalValue;
    }

    @Override
    public Integer getTotalRecords() {
        return totalRecords;
    }

    @Override
    public int getDbType() {
        return baseType.getDbId();
    }

    static String getNull4Empty(String value) {
        return value != null && value.isEmpty() ? null : value;
    }
}
