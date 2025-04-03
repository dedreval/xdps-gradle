package com.wiley.cms.notification;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseCheckerControler;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.notification.service.NewNotification;

import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.cms.process.task.TaskManager;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
@Local(INotificationManager.class)
@Singleton
@DependsOn("CochraneCMSProperties")
@Startup
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class NotificationManager extends JMXHolder implements NotificationManagerMXBean, INotificationManager {
    private static final Logger LOG = Logger.getLogger(NotificationManager.class);

    @EJB(beanName = "DbStorage")
    private IDbStorage dbs;
   
    @EJB(beanName = "LiteratumResponseChecker")
    private LiteratumResponseCheckerControler literatumResponseCheckerControler;

    @EJB(lookup = ProcessHelper.LOOKUP_TASK_MANAGER)
    private ITaskManager tm;

    @PostConstruct
    public void start() {
        registerInJMX();
    }

    @PreDestroy
    public void stop() {
        unregisterFromJMX();
    }

    public String consumeLiteratum() {
        return literatumResponseCheckerControler.checkResponses();
    }

    public String startLiteratumConsumer() {
        literatumResponseCheckerControler.start();
        return literatumResponseCheckerControler.getClass().getSimpleName() + " was started";
    }

    public String stopLiteratumConsumer() {
        literatumResponseCheckerControler.stop();
        return literatumResponseCheckerControler.getClass().getSimpleName() + " was stopped";
    }

    public void imitateResponse(String fileName, String publisherId, String date, boolean firstOnline, boolean offLine)
            throws Exception {
        LiteratumResponseChecker.Responder.instance().imitateLocalResponseForHW(
                fileName, Constants.DOI_PREFIX_CDSR + publisherId, date, firstOnline, offLine);
    }

    public boolean suspendNSNotification(String key, NewNotification notification, String reason, int type) {
        try {
            return suspendNotification(SuspendNotificationSender.SUSPEND_NOTIFICATION_SERVICE, key,
                SuspendNotificationEntity.getNewNotificationAsString(notification), reason, type);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }

    public boolean suspendASNotification(String key, String notification, String reason, int type) {
        try {
            return suspendNotification(SuspendNotificationSender.SUSPEND_NOTIFICATION_SERVICE, key,
                    notification, reason, type);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }

    public boolean suspendPublishWRNotification(String key, String notification, String reason, int type) {
        return suspendNotification(SuspendNotificationSender.SUSPEND_WR_SENDING, key, notification, reason, type);
    }

    public void suspendArchieNotification(String key, String notification, String reason, int type) {
        suspendNotification(SuspendNotificationSender.SUSPEND_ARCHIE_SERVICE, key, notification, reason, type);
    }

    public boolean suspendNotification(SuspendNotificationSender sender, String key, String body, String reason,
                                       int type) {
        String schedule = sender.getSchedule();
        String normReason = sender.normalizeReason(reason);
        if (schedule == null || normReason == null || sender.getMaxCount() <= 0) {
            return false;
        }
        SuspendNotificationEntity sn = new SuspendNotificationEntity(key, body, normReason, type, sender.ordinal());
        dbs.persist(sn);
        return suspendNotification(sender, sn, schedule);
    }

    private boolean suspendNotification(SuspendNotificationSender sender, SuspendNotificationEntity sn,
                                        String schedule) {
        if (findStoppedNotificationTask(sender.name())) {
            return false;
        }

        boolean wasSetUp = sender.upTimes.wasSetUp();
        if (sn.isToReSend()) {
            sender.onUpdate();
        }
        boolean addTask = !wasSetUp && !findActiveNotificationTask(sender.name());
        if (addTask || sender.upTimes.wasSetDown()) {
            TaskManager.Factory.getFactory().getInstance().addTask(sender.name(), schedule, new Date(), true,
                    SuspendNotificationChecker.class.getName(), sender.name());
        }
        return true;
    }

    public int checkSuspendNotifications(SuspendNotificationSender sender) throws Exception {

        int limit = sender.getLimit();
        if (limit <= 0) {
            throw new Exception(String.format("%s wrong sender limit: %d", sender, limit));
        }

        int start = 0;
        Collection<SuspendNotificationEntity> list = findSuspendNotifications(sender.ordinal(), start);
        int initialCount = list.size();
        if (initialCount == 0) {
            return SuspendNotificationChecker.EMPTY;
        }
        int count = 0;
        boolean success = true;

        while (!list.isEmpty()) {

            Pair<Boolean, Integer> ret = sendSuspendNotifications(sender, list, limit - count);
            success = ret.first;
            count += ret.second;

            if (!success || count == limit) {
                break;
            }

            start += (DbConstants.DB_PACK_SIZE - ret.second);
            list = findSuspendNotifications(start, sender.ordinal());
        }

        int ret = SuspendNotificationChecker.SUCCESS;
        if (success) {
            sender.onSendSuccess(count);
        } else {
            sender.onSendFailed(count, initialCount);
            ret = SuspendNotificationChecker.FAIL;
        }

        return ret;
    }

    private boolean findStoppedNotificationTask(String taskName) {
        List<TaskVO> list = tm.findTasks(taskName);
        for (TaskVO task: list) {
            if (task.getState().isStopped() || task.getState().isFailed()) {
                return true;
            }
        }
        return false;
    }

    private boolean findActiveNotificationTask(String taskName) {
        List<TaskVO> list = tm.findActiveTasks(taskName);
        for (TaskVO task: list) {
            if (task.getState().isWaited() || task.getState().isStarted()) {
                return true;
            }
        }
        return false;
    }

    private Collection<SuspendNotificationEntity> findSuspendNotifications(int type, int start) {
        return dbs.find(SuspendNotificationEntity.class, SuspendNotificationEntity.QUERY_SELECT_SUSPEND_NOTIFICATIONS,
                start, DbConstants.DB_PACK_SIZE, SuspendNotificationEntity.PARAM_SYSTEM_ID, type);
    }

    private Pair<Boolean, Integer> sendSuspendNotifications(SuspendNotificationSender sender,
        Collection<SuspendNotificationEntity> list, int limit) {

        boolean success = true;
        int count = 0;
        for (SuspendNotificationEntity sn: list) {

            if (!sn.isToReSend()) {
                continue;
            }

            int result;
            try {
                result = sender.send(sn);

            } catch (Exception e) {

                LOG.error(e.getMessage());
                sn.setType(SuspendNotificationEntity.TYPE_SYSTEM_ERROR);
                sn.setReason(e.getMessage() + ", " + sn.getReason());
                dbs.flush(sn);
                continue;
            }

            if (SuspendNotificationEntity.isToReSend(result)) {
                // it should be suspended again
                success = false;
                if (SuspendNotificationEntity.isDisabledService(result)) {
                    break;
                } else {
                    continue;
                }
            }

            dbs.delete(SuspendNotificationEntity.class, sn.getId());

            count++;
            if (count == limit) {
                break;
            }
        }
        return new Pair<>(success, count);
    }

    public void disableNotificationService() {
        MessageSender.enable(false);
    }

    public void enableNotificationService() {
        MessageSender.enable(true);
    }
}
