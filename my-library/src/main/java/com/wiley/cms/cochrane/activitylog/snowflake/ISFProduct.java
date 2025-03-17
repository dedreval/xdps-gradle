package com.wiley.cms.cochrane.activitylog.snowflake;


import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/13/2021
 */
public interface ISFProduct extends ISFProductPart {
    String P_PRODUCT_TYPE     = "productType";
    String P_PRODUCT_STATE    = "productState";
    String P_PUBLICATION_TYPE = "publicationType";
    String P_PRODUCT_TITLE    = "productTitle";
    String P_PRODUCT_CODE     = "productCode";
    String P_PRODUCT_FIRST_ONLINE_DATE = "productFirstOnlineDate";
    String PARENT_DOI         = "parentDoi";

    String getPubCode();

    String getParentDOI();

    String getType();

    String getPublicationType();

    default void setPublicationType(String publicationType) {
    }

    default String getAccessType() {
        return Constants.NA;
    }

    default String getFirstOnlineDate() {
        return Constants.NA;
    }

    default void setFirstOnlineDate(String firstOnlineDate) {
    }

    default String getOnlineDate() {
        return Constants.NA;
    }

    default void setOnlineDate(String date) {
    }

    default String getSPDDate() {
        return Constants.NA;
    }

    default void setSPDDate(String date) {
    }

    default String getTitle() {
        return null;
    }

    default void setTitle(String title) {
    }

    default boolean isCreated() {
        return getState() == FlowProduct.State.CREATED;
    }
}
