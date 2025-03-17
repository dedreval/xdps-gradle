package com.wiley.cms.notification;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.notification.service.NewNotification;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.res.JaxbResourceFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/5/2016
 */
@Entity
@Table(name = "COCHRANE_SUSPEND_NOTIFICATION")
@NamedQueries({
        @NamedQuery(
            name = SuspendNotificationEntity.QUERY_SELECT_SUSPEND_NOTIFICATIONS,
            query = "SELECT e FROM SuspendNotificationEntity e WHERE e.systemId=:systemId"
                    + " AND e.type < " + SuspendNotificationEntity.TYPE_TIBCO
        ),
        @NamedQuery(
            name = SuspendNotificationEntity.QUERY_SELECT_SEND_TIBCO_NOTIFICATIONS,
            query = "SELECT e FROM SuspendNotificationEntity e WHERE e.systemId=:systemId"
                    + " AND e.type >= " + SuspendNotificationEntity.TYPE_TIBCO
                    + " AND e.type <= " + SuspendNotificationEntity.TYPE_FROM_TIBCO_TEST
        )
    })
public class SuspendNotificationEntity extends DbEntity {

    public static final String QUERY_SELECT_SUSPEND_NOTIFICATIONS = "suspendNotifications";
    public static final String QUERY_SELECT_SEND_TIBCO_NOTIFICATIONS = "sendTibcoNotifications";
    public static final String PARAM_SYSTEM_ID = "systemId";

    public static final int TYPE_NO_ERROR = -1;
    public static final int TYPE_SYSTEM_ERROR = 0;
    public static final int TYPE_DEFINED_ERROR = 1;
    public static final int TYPE_UNDEFINED_ERROR = 2;
    public static final int TYPE_DISABLED_SERVICE = 3;
    public static final int TYPE_TIBCO = 100;
    public static final int TYPE_TO_TIBCO = 101;
    public static final int TYPE_FROM_TIBCO = 102;
    public static final int TYPE_TO_TIBCO_TEST = 103;
    public static final int TYPE_FROM_TIBCO_TEST = 104;
    public static final int TYPE_TIBCO_TEST = 105;

    private static final JaxbResourceFactory<NewNotification> FACTORY =
            JaxbResourceFactory.create(NewNotification.class);

    private String body;
    private String message;
    private String reason;
    private int type;
    private int systemId;

    public SuspendNotificationEntity() {
    }

    public SuspendNotificationEntity(String message, NewNotification body, String reason, int type) throws Exception {
        this(message, getNewNotificationAsString(body), reason, type,
                SuspendNotificationSender.SUSPEND_NOTIFICATION_SERVICE.ordinal());
    }

    public SuspendNotificationEntity(String message, String notification, String reason, int type, int systemId) {

        this.message = message;
        this.body = notification;
        setReason(reason == null ? "" : reason);
        this.type = type;
        this.systemId = systemId;
    }

    public NewNotification createNewNotification() throws Exception {
        return FACTORY.createResource(body);
    }

    @Column(name = "body", length = STRING_MEDIUM_TEXT_LENGTH, nullable = false)
    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Column(name = "reason", length = STRING_VARCHAR_LENGTH_LARGE, nullable = false)
    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        if (value != null && value.length() > STRING_VARCHAR_LENGTH_LARGE) {
            reason = value.substring(0, STRING_VARCHAR_LENGTH_LARGE);
        } else {
            reason = value;
        }
    }

    @Column(name = "message", length = STRING_VARCHAR_LENGTH_64, nullable = false)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Column(nullable = false)
    public int getType() {
        return type;
    }

    public void setType(int value) {
        type = value;
    }

    @Column(name = "system_id", nullable = false)
    public int getSystemId() {
        return systemId;
    }

    public void setSystemId(int value) {
        systemId = value;
    }

    @Transient
    public boolean isToReSend() {
        return isToReSend(getType());
    }

    @Transient
    public boolean isToTibco() {
        return type == TYPE_TO_TIBCO;
    }

    @Transient
    public boolean isToTibcoTest() {
        return type == TYPE_TO_TIBCO_TEST;
    }

    @Transient
    @Override
    public String toString()  {
        return String.format("%s %d [%d] (%s)", message, type, getId(), reason);
    }

    public static String getNewNotificationAsString(NewNotification notification) throws Exception {
        return XmlUtils.cutStandaloneXMLHead(FACTORY.convertResourceToString(notification));
    }

    public static boolean isToReSend(int type) {
        return type > TYPE_DEFINED_ERROR;
    }

    public static boolean isDisabledService(int type) {
        return type == TYPE_DISABLED_SERVICE;
    }
}
