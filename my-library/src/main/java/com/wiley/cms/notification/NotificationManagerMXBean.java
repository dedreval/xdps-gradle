package com.wiley.cms.notification;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/8/2016
 */
public interface NotificationManagerMXBean {

    String consumeLiteratum();

    String startLiteratumConsumer();

    String stopLiteratumConsumer();

    void imitateResponse(String fileName, String publisherId, String date, boolean firstOnline, boolean offLine)
            throws Exception;
}
