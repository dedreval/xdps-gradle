package com.wiley.cms.cochrane.services;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 17.11.12
 */
public class PublishEvent extends WREvent {
    private static final long serialVersionUID = 1L;

    final String recordName;
    final int recordNumber;
    final int pubNumber;
    final String eventName;
    final Date eventDate;
    private final boolean visible;

    PublishEvent(PublishDestination dest, String recName, int pub, int recordNumber, String eventName, Date eventDate,
                 boolean visible) {
        setDest(dest);
        this.recordName = recName;
        this.pubNumber = pub;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.visible = visible;
        this.recordNumber = recordNumber;
    }

    @Override
    int onReceiveEvent(IWREventVisitor receiver) {
        return receiver.onEventReceive(this);
    }

    @Override
    public String getRecordName() {
        return recordName;
    }

    @Override
    public int getPubNumber() {
        return pubNumber;
    }

    @Override
    public int getRecordNumber() {
        return recordNumber;
    }

    public String getEventName() {
        return eventName;
    }

    @Override
    public Date getEventDate() {
        return eventDate;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public String toString() {
        String time = getEndedAtTime();
        return time == null
                ? toString(recordName, pubNumber, eventName, getDeliveryId())
                : toString(recordName, pubNumber, eventName, getDeliveryId(), time);
    }

    public static String toString(String cdNumber, int pubNumber, String eventName) {
        return pubNumber > Constants.FIRST_PUB ? String.format("%s.pub%d (%s)", cdNumber, pubNumber, eventName)
                : String.format("%s (%s)", cdNumber, eventName);
    }

    private static String toString(String cdNumber, int pubNumber, String eventName, String deliveryId) {
        return pubNumber > Constants.FIRST_PUB ? String.format("%s.pub%d (%s) deliveryId:%s", cdNumber, pubNumber,
                    eventName, deliveryId == null ? "" : deliveryId)
                : String.format("%s (%s) deliveryId:%s", cdNumber, eventName, deliveryId == null ? "" : deliveryId);
    }

    private static String toString(String cdNumber, int pubNumber, String eventName, String deliveryId, String time) {
        return pubNumber > Constants.FIRST_PUB ? String.format("%s.pub%d (%s) deliveryId:%s [%s]", cdNumber, pubNumber,
                    eventName, deliveryId, time)
                : String.format("%s (%s) deliveryId:%s [%s]", cdNumber, eventName, deliveryId, time);
    }
}
