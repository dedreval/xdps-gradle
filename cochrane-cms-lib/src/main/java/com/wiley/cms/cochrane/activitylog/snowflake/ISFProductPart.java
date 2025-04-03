package com.wiley.cms.cochrane.activitylog.snowflake;

import java.util.Map;

import com.wiley.cms.cochrane.activitylog.FlowProduct;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/21/2021
 */
public interface ISFProductPart {
    String P_PRODUCT_DOI = "productDoi";
    String P_TR_STATE    = "translationState";
    String P_TR_LANGUAGE = "translationLanguage";

    String getDOI();

    String getLanguage();

    FlowProduct.State getState();

    Map<String, String> getRawData();

    default void setState(FlowProduct.State state) {
    }

    default boolean isExisted() {
        return FlowProduct.State.NONE != getState();
    }

    default boolean isReceived() {
        return getState() == FlowProduct.State.RECEIVED;
    }

    default boolean isDeleted() {
        return getState() == FlowProduct.State.DELETED;
    }

    default boolean isPublished() {
        return getState() == FlowProduct.State.PUBLISHED;
    }

    default void sPD(boolean value) {
    }
    
    default FlowProduct.SPDState sPD() {
        return FlowProduct.SPDState.NONE;
    }
}
