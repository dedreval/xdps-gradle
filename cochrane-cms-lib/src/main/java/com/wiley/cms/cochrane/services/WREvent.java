package com.wiley.cms.cochrane.services;

import java.io.Serializable;
import java.util.Date;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 16.11.12
 */
public abstract class WREvent implements Serializable {
    private static final long serialVersionUID = 1L;

    protected PublishDestination dest;

    WREvent() {
    }

    public void setDest(PublishDestination dest) {
        this.dest = dest;
    }

    public PublishDestination getDest() {
        return dest;
    }

    abstract int onReceiveEvent(IWREventVisitor receiver);

    public boolean isVisible() {
        return true;
    }

    public String getRecordName() {
        return null;
    }

    public int getRecordNumber() {
        return 0;
    }

    public int getPubNumber() {
        return 0;
    }

    public String getDeliveryId() {
        return null;
    }

    public String getRawData() {
        return "";
    }

    public Date getEventDate() {
        return null;
    }

    public String getEndedAtTime() {
        return null;
    }

    public String getDataForUiLog() {
        return "";
    }

    public PublishDate getPublishDate(String tag) {
        return PublishDate.EMPTY;
    }

    public boolean isFirstPublishedOnline() {
        return false;
    }

    public boolean isContentOffline() {
        return false;
    }

    public BaseType getBaseType() {
        return BaseType.getCDSR().get();
    }

    public static WREvent createOnOrOfflineLiteratumEvent(String source, String event, Date eventDate, String doi,
                                                          String deliveryId, boolean fullDoi) throws CmsException {
        PublishDestination dest = determinePublishDestinationWithLiteratum(source, event, true);
        return createEvent(checkLiteratumDestination(checkDestination(dest, source, event), source, event, deliveryId),
                event, eventDate, doi, deliveryId, fullDoi);
    }

    private static PublishDestination checkLiteratumDestination(PublishDestination dest, String source, String event,
                                                                String deliveryId) throws CmsException {
        if (dest == PublishDestination.WOLLIT && (deliveryId == null || deliveryId.isEmpty())) {
            throw new CmsException(String.format("deliveryId is required for source %s and event %s", source, event));
        }
        return dest;
    }
    
    private static PublishDestination checkDestination(PublishDestination dest, String source, String event)
            throws CmsException {
        if (dest == null) {
            throw new CmsException(
                    String.format("cannot determine destination by source %s and event %s", source, event));
        }
        return dest;
    }

    /**
     * @param source    The event source
     * @param action    The event type
     * @param online    TRUE -> a filter for CONTENT_ONLINE is used, FALSE -> a LOAD_ON_PUBLISH  filter
     * @return          SEMANTICO | WOLLIT - a enum for this event
     */
    public static PublishDestination determinePublishDestinationWithLiteratum(String source, String action,
                                                                              boolean online) {
        if (online && CochraneCMSPropertyNames.isAmongValues(action,
                CochraneCMSPropertyNames.getLiteratumEventOnlineFilter(),
                CochraneCMSPropertyNames.getLiteratumEventOfflineFilter())
                
            || CochraneCMSPropertyNames.isAmongValues(action,
                CochraneCMSPropertyNames.getLiteratumEventOnLoadFilter())) {

            return source.equals(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterWol())
                    ? PublishDestination.WOLLIT
                    : source.equals(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterSemantico())
                    ? PublishDestination.SEMANTICO
                    : null;
        }
        return null;
    }

    public static WREvent createEvent(PublishDestination dest, String action, Date eventDate, String rawDoi,
                                      String deliveryId, boolean fullDoi) throws CmsException {
        if (rawDoi == null) {
            throw new CmsException("doi is null");
        }
        String doi = rawDoi.trim();
        if (doi.isEmpty()) {
            throw new CmsException("doi is empty");
        }

        BaseType bt = dest == PublishDestination.SEMANTICO ? PublishHelper.defineBaseTypeByHWDoi(doi)
                : PublishHelper.defineBaseTypeByWolLitDoi(doi);
        if (bt == null) {
            throw new CmsException(String.format("%s not to be processed with a publication event on real-time", doi));
        }

        String cdNumber = bt.isCentral() && doi.length() <= Constants.DOI_PREFIX_CENTRAL.length() ? null
                : bt.getCdNumberByDoi(doi);
        if (fullDoi && cdNumber == null) {
            throw new CmsException(String.format("%s cannot get article's name from hasPart.doi: ", doi));
        }

        return new LiteratumEvent(dest, bt, cdNumber, cdNumber == null ? Constants.FIRST_PUB
                : bt.getProductType().parsePubNumber(doi), action, eventDate, deliveryId);
    }

    @Override
    public String toString() {
        return "inner object";
    }
}
