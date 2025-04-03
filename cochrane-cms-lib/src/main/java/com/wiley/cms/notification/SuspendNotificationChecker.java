package com.wiley.cms.notification;

import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/18/2017
 */
public class SuspendNotificationChecker implements ITaskExecutor, IScheduledTask {

    public static final int SUCCESS = 1;
    public static final int FAIL = -1;
    public static final int EMPTY = 0;

    private SuspendNotificationSender sender = null;

    @Override
    public boolean execute(TaskVO task) throws Exception {

        long currentTime = System.currentTimeMillis();
        int ret = sender.checkSuspendNotifications();
        if (stopEmpty(currentTime, ret)) {
            task.setSchedule(null);

        } else if ((ret != EMPTY && sender.noTries(task.getCountTries())) || sender.noTries(task.getCountTries() - 1)) {
            sender.onSendOff(task.getCountTries());
            task.setState(ProcessState.STOPPED);

        } else {
            updateSchedule(task);
        }
        return true;
    }

    private boolean stopEmpty(long currentTime, int ret) {
        
        if (ret == EMPTY) {
            sender.upTimes.setLastTime(currentTime);
            return sender.upTimes.wasSetDown();
        }
        return false;
    }

    @Override
    public ITaskExecutor initialize(String ... params) throws Exception {
        if (params.length <= 1) {
            throw new Exception(String.format("%s: an enum for sender was not passed", getClass().getSimpleName()));
        }
        sender = SuspendNotificationSender.valueOf(params[1].trim());
        return this;
    }

    @Override
    public String getScheduledTemplate() {
        return sender.getSchedule();
    }

    @Override
    public long canDelay() {
        return sender.upTimes.wasSetDown() ? RESCHEDULE : 0;
    }
}
