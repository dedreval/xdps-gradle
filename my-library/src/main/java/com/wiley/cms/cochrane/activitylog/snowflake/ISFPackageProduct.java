package com.wiley.cms.cochrane.activitylog.snowflake;

import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/13/2021
 */
public interface ISFPackageProduct extends ISFProduct {
    String TYPE_SCHEDULED = "Scheduled";
    String TYPE_CANCELED = "Cancellation";

    String P_PACKAGE_ID = "packageId";
    String P_PACKAGE_TYPE = "packageType";
    String P_PRODUCT_OR_TRANSLATION_TITLE = "productOrTranslationTitle";
    String P_PRODUCT_PUBLICATION_STATUS = "productPublicationStatus";
    String P_PRODUCT_SPD = "packageScheduledPublicationDate";
    String P_PACKAGE_VENDOR = "vendorType";
    String P_PACKAGE_PACKAGE_FREQUENCY = "packageFrequency";
    String P_PACKAGE_HIGH_PROFILE_PUBLICATION = "packageHighProfilePublication";

    Integer getSourcePackageId();

    default String getSourcePackageType() {
        return sPD().on() ? TYPE_SCHEDULED : sPD().off() ? TYPE_CANCELED : null;
    }

    default String getSourceStatus() {
        return null;
    }

    default String getVendor() {
        return null;
    }

    default void setSourceStatus(String sourceStatus) {
    }

    default boolean isHighProfile() {
        return false;
    }

    default boolean isHighFrequency() {
        return false;
    }

    default String getFrequency() {
        return HWFreq.REAL_TIME.getValue();
    }

    default Integer getEventRecords() {
        return null;
    }

    default void setEventRecords(Integer eventValue, Integer totalValue) {
    }

    default Integer getTotalRecords() {
        return null;
    }

    default String getTransactionId() {
        return null;
    }

    default void setTransactionId(String uuid) {
    }
}
