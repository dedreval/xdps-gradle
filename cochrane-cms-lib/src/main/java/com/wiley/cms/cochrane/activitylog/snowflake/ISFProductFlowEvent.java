package com.wiley.cms.cochrane.activitylog.snowflake;

import java.util.Date;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/21/2021
 */
public interface ISFProductFlowEvent {
    String P_EVENT_TYPE            = "eventType";
    String P_EVENT_DATE            = "eventDate";
    String P_EVENT_ID              = "eventId";
    String P_EVENT_NAME            = "eventName";
    String P_EVENT_PACKAGE_NAME    = "eventPackageName";
    String P_ERROR_MESSAGE         = "errorMessage";
    String P_EVENT_COMPLETED       = "eventCompleted";
    String P_EVENT_STARTED         = "eventStarted";

    String P_EVENT_TRANSACTION_ID  = "transactionId";
    String P_EVENT_RECORDS         = "eventRecords";
    String P_TOTAL_PHASE_RECORDS   = "totalPhaseRecords";

    String getEventId();

    String getEventName();

    default String getEventPackageName() {
        return null;
    }

    String getErrorMessage();

    ISFPackageProduct getProduct();

    Date getEndedAtTime();

    default Date getStartedAtTime() {
        return null;
    }
}
