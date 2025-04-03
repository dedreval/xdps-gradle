package com.wiley.cms.cochrane.services;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 17.11.12
 */
public class LiteratumEvent extends PublishEvent {
    public static final String WRK_EVENT_ONLINE_FINAL_FORM     = "publishedOnlineFinalForm";
    public static final String WRK_EVENT_FIRST_ONLINE          = "firstOnline";
    public static final String WRK_EVENT_ONLINE_CITATION_ISSUE = "publishedOnlineCitationIssue";

    private static final long serialVersionUID = 1L;

    private final String deliveryId;
    private String rawData;

    private Map<String, PublishDate> pubDates;

    private String originalTime;
    private boolean firstPublishedOnline;
    private final BaseType bt;

    LiteratumEvent(PublishDestination dest, BaseType bt, String cdNumber, int pub, String eventName,
                   Date eventDate, String deliveryId) {
        super(dest, cdNumber, pub,
                cdNumber == null ? 0 : bt.getProductType().buildRecordNumber(cdNumber), eventName, eventDate, true);
        this.deliveryId = checkDeliveryId(deliveryId);
        this.bt = bt;
    }

    public void setEndedAtTime(String endedAtTime) {
        originalTime = endedAtTime;
    }

    public boolean isContentOffline() {
        return CochraneCMSPropertyNames.getLiteratumEventOfflineFilter().equals(eventName);
    }

    @Override
    public String getEndedAtTime() {
        return originalTime;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    @Override
    public String getRawData() {
        return rawData;
    }

    @Override
    public PublishDate getPublishDate(String tag) {
        return pubDates == null || !pubDates.containsKey(tag) ? PublishDate.EMPTY : pubDates.get(tag);
    }

    public void setPublishDate(String tag, String publishDate) {
        if (pubDates == null) {
            pubDates = new HashMap<>();
        }
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(publishDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        pubDates.put(tag, new PublishDate(publishDate, Date.from(offsetDateTime.toInstant()),
                CmsUtils.getIssueNumber(offsetDateTime.getYear(), offsetDateTime.getMonthValue())));
    }

    @Override
    public boolean isFirstPublishedOnline() {
        return firstPublishedOnline;
    }

    public void setFirstPublishedOnline(boolean firstPublishedOnline) {
        this.firstPublishedOnline = firstPublishedOnline;
    }

    @Override
    int onReceiveEvent(IWREventVisitor receiver) {
        return receiver.onEventReceive(this);
    }

    @Override
    public String getDeliveryId() {
        return deliveryId;
    }

    private static String checkDeliveryId(String value) {
        String ret = value;
        if (ret != null) {
            ret = value.trim();
        }
        return ret != null && !ret.isEmpty() ? ret : null;
    }

    @Override
    public BaseType getBaseType() {
        return bt;
    }

    @Override
    public String getDataForUiLog() {
        return String.format("ContentOnline{deliveryId='%s', rawData='%s', pubDates=%s, originalTime='%s', "
                        + "firstPublishedOnline=%s, bt=%s, recordName='%s', recordNumber=%d, "
                        + "pubNumber=%d, eventName='%s', eventDate=%s, dest=%s}",
                deliveryId,
                rawData,
                pubDates != null ? pubDates.toString() : "There are no pub dates",
                originalTime,
                firstPublishedOnline,
                bt,
                recordName,
                recordNumber,
                pubNumber,
                eventName,
                eventDate,
                dest);
    }
}
