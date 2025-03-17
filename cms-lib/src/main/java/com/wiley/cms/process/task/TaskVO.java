package com.wiley.cms.process.task;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.entity.TaskEntity;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.12.12
 */
public class TaskVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private final String label;
    private final Date creationDate;

    private Date startTime;
    private Date lastDate;
    private ProcessState state;
    private String msg;
    private String schedule;
    private int runCount;
    private boolean fast = false;
    private boolean sent = false;

    private ProcessHandler handler;
    private String params;
    private ITaskExecutor executor;

    public TaskVO(TaskEntity ee) {
        this(ee.getId(), ee.getLabel(), ee.getStartDate(), ee.getState(), ee.getSchedule(), ee.getCreationDate(), null);
        msg = ee.getMessage();
        lastDate = ee.getLastDate();
        params = ee.getParams();
        runCount = ee.getRunCount();
        setFast(ee.isFast());
        sent = ee.isSent();
    }

    public TaskVO(String label, Date startTime, ProcessState state, String schedule, Date creationDate,
        ProcessHandler handler) {
        this(DbEntity.NOT_EXIST_ID, label, startTime, state, schedule, creationDate, handler);
    }

    public TaskVO(int id, String label, Date startTime, ProcessState state, String schedule, Date creationDate,
        ProcessHandler handler) {

        this.id = id;
        this.label = label;
        this.state = state;
        setStartTime(startTime);
        this.schedule = schedule;
        this.creationDate = (creationDate == null) ? new Date() : creationDate;
        this.handler = handler;
    }

    public void updateFrom(TaskEntity ee) {

        setMessage(ee.getMessage());
        setState(ee.getState());
        setSchedule(ee.getSchedule());

        setStartTime(ee.getStartDate());
        lastDate = ee.getLastDate();
        runCount = ee.getRunCount();
    }

    public static Res<Property> getCanDownloadProperty() {
        return Property.get("cms.task.download", Boolean.FALSE.toString());
    }

    public static Res<Property> getScheduleProperty() {
        return Property.get("cms.task.download.schedule", "0 0/10 * * * ?");
    }

    public void setStartTime(Date date) {
        startTime = date;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean existsInDb() {
        return id != DbEntity.NOT_EXIST_ID;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public String getLabel() {
        return label;
    }

    public long getStartTime() {
        return isScheduled() ? startTime.getTime() : Long.MAX_VALUE;
    }

    public boolean isScheduled() {
        return isScheduled(getStartDate(), getState());
    }

    public Date getStartDate() {
        return startTime;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getLastDate() {
        return lastDate;
    }

    public ProcessHandler getHandler() {
        if (handler == null) {
            handler = ProcessHandler.createHandler(params);
            handler.setName(label);
        }
        return handler;
    }

    public void setExecutor(ITaskExecutor executor) {
        this.executor = executor;
    }

    public ITaskExecutor getExecutor() {
        return executor;
    }

    public long delayExecution(long currentTime) {

        long ret = executor == null ? 0 : executor.canDelay();
        if (ret == 0) {
            return 0;
        }

        if (ret == ITaskExecutor.RESCHEDULE && isRepeatable()) {
            Date date = ProcessHelper.getNextDateBySchedule(getSchedule());
            if (date != null) {
                setStartTime(date);
            } else {
                ret = 0;
            }
        } else if (ret > Now.MS_IN_SEC) {
            setStartTime(new Date(currentTime + ret));
        } else {
            ret = 0;
        }

        return ret;
    }

    public void setMessage(String message) {
        msg = message;
    }

    public String getMessage() {
        return msg;
    }

    public void setSchedule(String value) {
        schedule = value;
    }

    public String getSchedule() {
        return schedule;
    }

    public boolean isRepeatable() {
        return schedule != null;
    }

    public long getNextScheduledDelay() throws ParseException {
        return getDelayBySchedule(getSchedule());
    }

    public static long getDelayBySchedule(String schedule) throws ParseException {
        Date dt = new Date();
        return Now.getNextValidTimeAfter(schedule, dt).getTime() - dt.getTime();
    }

    public static Date getNextDateBySchedule(String schedule) throws ParseException {
        Date dt = new Date();
        return Now.getNextValidTimeAfter(schedule, dt);
    }

    public static boolean isScheduled(Date startDate, ProcessState state) {
        return startDate != null && ProcessState.EXECUTED_STATES.contains(state);
    }

    public boolean isFast() {
        return fast;
    }

    public boolean isSent() {
        return sent;
    }

    public void setFast(boolean value) {
        fast = value;
    }

    public int getCountTries() {
        return runCount;
    }

    public void setCountTries(int count) {
        runCount = count;
    }

    @Override
    public String toString() {
        return isScheduled() ? String.format("%s [%d] '%s' [%tc]", label, id, state, startTime)
                : String.format("%s [%d] '%s'", label, id, state);
    }
}

