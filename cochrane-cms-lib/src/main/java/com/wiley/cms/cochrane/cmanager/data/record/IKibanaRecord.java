package com.wiley.cms.cochrane.cmanager.data.record;

import java.util.Set;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.IFlowProduct;

/**
 * Record used for Kibana Dashboard
 *
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 1.2
 * @since 30.04.2020
 */
public interface IKibanaRecord {

    String getName();

    String getTitle();

    void setTitle(String title);

    String getDfName();

    //void setDfName(String dfName);

    Integer getDfId();

    String getVendor();

    String getLitPackageName();

    void setLitPackageName(String litPackageName);

    String getHwPackageName();

    void setHwPackageName(String hwPackageName);

    boolean hasHwPackageName();

    String getDsPackageName();

    void setDsPackageName(String dsPackageName);

    default String getPubName() {
        return null;
    }

    default String getStage() {
        return null;
    }

    default void setStage(String stage) {
    }

    default String getStatus() {
        return null;
    }

    default void setStatus(int status) {
    }

    default boolean hasReview() {
        return false;
    }

    default Set<String> getLanguages() {
        return null;
    }

    default IFlowProduct addLanguage(String language) {
        return null;
    }

    default boolean hasLanguages() {
        return false;
    }

    FlowProduct getFlowProduct();

    IFlowProduct getFlowProduct(String language);
}