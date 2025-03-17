package com.wiley.cms.notification;

import com.wiley.cms.notification.service.NewNotification;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
public interface INotificationManager {

    String consumeLiteratum();

    String startLiteratumConsumer();

    String stopLiteratumConsumer();

    boolean suspendNSNotification(String key, NewNotification notification, String suspendReason, int type);

    boolean suspendASNotification(String key, String notification, String suspendReason, int type);

    void suspendArchieNotification(String key, String notification, String reason, int type);

    boolean suspendPublishWRNotification(String key, String notification, String reason, int type);

    boolean suspendNotification(SuspendNotificationSender sender, String key, String body, String reason, int type);

    /**
     * It tries to send suspend notifications for a specified sender (if they exist)
     * @param sender  The specified sender
     * @return  0 - nothing was to send; 1 - sending was successful; -1 - sending was failed
     * @throws Exception - if sending is impossible because of wrong settings
     */
    int checkSuspendNotifications(SuspendNotificationSender sender) throws Exception;

    void disableNotificationService();

    void enableNotificationService();

    void imitateResponse(String fileName, String publisherId, String date, boolean firstOnline, boolean offLine)
        throws Exception;
}
