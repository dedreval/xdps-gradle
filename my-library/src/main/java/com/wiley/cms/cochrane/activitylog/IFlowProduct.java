package com.wiley.cms.cochrane.activitylog;

import java.util.Collection;
import java.util.Collections;

import com.wiley.cms.cochrane.activitylog.snowflake.ISFPackageProduct;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/13/2021
 */
public interface IFlowProduct extends ISFPackageProduct {

    String RETRACTED = "RETRACTED";

    Integer getEntityId();

    default void setEntityId(Integer entityId) {
    }

    String getSID();

    default void setSID(String sid) {
    }

    String getCochraneVersion();

    default void setCochraneVersion(String cochraneVersion) {
    }

    default FlowProduct.State getFlowState() {
        return FlowProduct.State.UNDEFINED;
    }

    default String getStage() {
        return null;
    }

    default void setStage(String stage) {
    }

    default boolean isRetracted() {
        return RETRACTED.equals(getStage());
    }

    default Collection<IFlowProduct> getTranslations() {
        return Collections.emptyList();
    }

    default Collection<Pair<FlowLogEntity, IFlowProduct>> getDeletedTranslations() {
        return Collections.emptyList();
    }

    default String getPackageName() {
        return null;
    }

    default void setPackageName(String packageName) {
    }

    default String getError() {
        return null;
    }

    int getDbType();

    //default String getVendor() {
    //    return "";
    //}

    default int getHighPriority() {
        return 0;
    }

    default void setHighPriority(int hp) {
    }

    default void setHighProfile(boolean highProfile) {
    }

    default void setHighFrequency(boolean high) {
    }

    default boolean wasCompleted() {
        return getFlowState() == FlowProduct.State.PUBLISHED_DS;
    }

    default boolean wasPublished() {
        return getFlowState() == FlowProduct.State.PUBLISHED || wasCompleted();
    }

    default void setTitleId(Integer titleId) {
    }

    default Integer getTitleId() {
        return null;
    }
}
