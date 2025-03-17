package com.wiley.cms.notification;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.publish.PublishServiceFactory;
import com.wiley.cms.cochrane.cmanager.res.RetryType;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.task.UpTimes;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Res;

import java.util.Date;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
public enum SuspendNotificationSender {
    SUSPEND_NOTIFICATION_SERVICE {

        @Override
        public int checkSuspendNotifications() throws Exception {
            return CochraneCMSBeans.getNotificationManager().checkSuspendNotifications(this);
        }

        @Override
        public int send(SuspendNotificationEntity sn) throws Exception {
            return MessageSender.sendAsMessage(sn.getBody(), sn.getMessage(), false);
            // Deprecated ns notification service
            // NewNotification nn;
            // try {
            // nn = sn.createNewNotification();
            // } catch (Exception e) {
            // throw noParseMsg(sn, e.getMessage());
            // }
            // return MessageSender.sendMessage(nn, sn.getMessage(), false);
        }

        @Override
        public void onSendSuccess(int count) {
            MessageSender.enable(true);
            LOG.info(String.format("%d suspended notifications have been sent, message sender is enabled", count));
        }

        @Override
        public void onSendFailed(int sentCount, int all) {
            if (sentCount > 0) {
                LOG.info(String.format(
                    "%d suspended notifications were sent, but message sender is OFF because of sending is interrupted",
                        sentCount));
            }
        }

    }, SUSPEND_ARCHIE_SERVICE {

        @Override
        public int send(SuspendNotificationEntity sn) {
            int ret = SuspendNotificationEntity.TYPE_DISABLED_SERVICE;
            try {
                String err = RevmanPackage.notifyPublishedRepeated(sn.getMessage(), sn.getBody());
                boolean controlledError = isControlledError(err);
                if (err == null || controlledError) {

                    PublishedAbstractEntity pae = CochraneCMSBeans.getPublishStorage().updateWhenReadyOnPublished(
                            extractObjectId(sn), null, true);
                    if (pae != null) {
                        CochraneCMSPropertyNames.lookupFlowLogger().onDashboardEvent(
                                KibanaUtil.Event.NOTIFY_PUBLISHED, null, new Date(), pae);
                    }
                    ret = controlledError ? SuspendNotificationEntity.TYPE_DEFINED_ERROR
                            : SuspendNotificationEntity.TYPE_NO_ERROR;

                } else {
                    ret = SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                }
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
            return ret;
        }

    }, SUSPEND_ARIES_SENDING("Aries SFTP") {
        @Override
        public int send(SuspendNotificationEntity sn) {
            try {
                PublishServiceFactory.getFactory().getInstance().sendAcknowledgementAries(extractObjectId(sn));
            } catch (Exception e) {
                LOG.warn(e.getMessage());
                return SuspendNotificationEntity.TYPE_DEFINED_ERROR;
            }
            return SuspendNotificationEntity.TYPE_NO_ERROR;
        }

    }, SUSPEND_WR_SENDING {
        @Override
        public int send(SuspendNotificationEntity sn) {
            try {
                PublishServiceFactory.getFactory().getInstance().publishWhenReady(extractObjectId(sn));

            } catch (Exception e) {
                LOG.error(e.getMessage());
                return SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
            }
            return SuspendNotificationEntity.TYPE_NO_ERROR;
        }

        @Override
        public void onSendOff(int tries) {
            onSendOff2("WOLLIT", tries);
        }

    }, SUSPEND_HW_SENDING("HW service") {
        @Override
        public int send(SuspendNotificationEntity sn) {
            PublishServiceFactory.getFactory().getInstance().sendWhenReadyHW(extractObjectId(sn), sn.getBody());
            return SuspendNotificationEntity.TYPE_NO_ERROR;
        }
    };

    private static final Logger LOG = Logger.getLogger(SuspendNotificationSender.class);

    private static final String ON_RETRY_MSG = "Sending will be retried in ~";

    final UpTimes upTimes = new UpTimes();

    final String serviceName;

    SuspendNotificationSender() {
        serviceName = "";
    }

    SuspendNotificationSender(String service) {
        serviceName = service;
    }

    public abstract int send(SuspendNotificationEntity sn) throws Exception;

    public int checkSuspendNotifications() throws Exception {
        synchronized (this) {
            return CochraneCMSBeans.getNotificationManager().checkSuspendNotifications(this);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void onSendSuccess(int sentCount) {
    }

    public void onSendFailed(int sentCount, int all) {
    }

    public void onSendOff(int tries)  {
    }

    public void onUpdate() {
        upTimes.setUpTime(System.currentTimeMillis());
    }

    public int getLimit() {
        Res<RetryType> retry = RetryType.find(name());
        if (Res.valid(retry)) {
            return retry.get().getLimit();
        }
        return 0;
    }

    public void setLimit(int limit) {
        Res<RetryType> retry = RetryType.find(name());
        if (Res.valid(retry)) {
            retry.get().setLimit(limit);
        }
    }

    public boolean noTries(int tries) {
        int max = getMaxCount();
        return max > 0 && max <= tries;
    }

    public int getMaxCount() {
        Res<RetryType> retry = RetryType.find(name());
        if (Res.valid(retry)) {
            return retry.get().getMaxCount();
        }
        return 0;
    }

    public String getSchedule() {
        Res<RetryType> retry = RetryType.find(name());

        if (Res.valid(retry)) {
            String value = retry.get().getSchedule();
            if (value != null && value.trim().length() > 1) {
                return value;
            }
        }
        return null;
    }

    public void setSchedule(String value) {
        Res<RetryType> retry = RetryType.find(name());
        if (Res.valid(retry)) {
            retry.get().setSchedule(value);
        }
    }

    public String normalizeReason(String reason) {
        Res<RetryType> retry = RetryType.find(name());
        if (Res.valid(retry)) {
            return retry.get().getRetryError(reason);
        }
        return null;
    }

    protected void onSendSuccess(String where) {
        MessageSender.sendReport(MessageSender.MSG_TITLE_SYSTEM_INFO,
                String.format("%s (%s) is available again.", serviceName, where));
    }

    protected void onSendFailed(String where, int sentCount, int all) {
        String msg = String.format("%s (%s) is not available. %d packages to be notified",
                serviceName, where, all - sentCount);
        LOG.warn(msg);
        MessageSender.sendReport(MessageSender.MSG_TITLE_SYSTEM_WARN, msg);
    }

    protected void onSendOff1(String where, int tries) {
        String msg = String.format(
            "%s (%s) hasn't responded after %d calls. No more automatically attempts will be done. "
                    + "Please contact Support Team.", serviceName, where, tries);
        MessageSender.sendReport(MessageSender.MSG_TITLE_SYSTEM_ERROR, msg);
    }

    protected static void onSendOff2(String where, int tries) {
        String msg = String.format("Sending to %s failed after %d tries. "
            + "No more automatically attempts will be done. Please contact Support Team.", where, tries);
        MessageSender.sendReport(MessageSender.MSG_TITLE_SYSTEM_ERROR, msg);
    }

    public static CmsException notAvailableMsg(String what, int delay, String msg) {
        return new CmsException(String.format("%s was not sent: %s.\n\n%s%s.",
                RepositoryUtils.getLastNameByPath(what), msg, ON_RETRY_MSG, Now.buildTime(delay)));
    }

    public CmsException errMsg(String msg) {
        return new CmsException(String.format("failure on %s call: %s.", serviceName, msg));
    }

    protected static Integer extractObjectId(SuspendNotificationEntity sn) {
        return Integer.valueOf(sn.getMessage());
    }

    protected boolean isControlledError(String error) {
        Res<RetryType> retry = RetryType.find(name());
        return Res.valid(retry) && retry.get().getControlledError(error) != null;
    }

    public static void suspendNotification(SuspendNotificationSender sender, String what,
                                           String id, String body, Exception ce) throws CmsException {
        int delay = (int) ProcessHelper.getDelayBySchedule(sender.getSchedule());
        if (delay == 0) {
            throw new CmsException(ce.getMessage());
        }
        if (CochraneCMSBeans.getNotificationManager().suspendNotification(sender, id, body, ce.getMessage(),
                SuspendNotificationEntity.TYPE_UNDEFINED_ERROR)) {
            throw sender.notAvailableMsg(what, delay, ce.getMessage());
        } else {
            throw new CmsException(ce.getMessage());
        }
    }

    private static Exception noParseMsg(SuspendNotificationEntity sn, String msg) {
        return new Exception(String.format("can't parse body of suspend notification %s - %s", sn, msg));
    }
}
