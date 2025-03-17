package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementation of <tt>ITaskExecutor</tt> interface which is aimed to notify about articles
 * which ones are awaiting for Epoch more than specified amount of time.
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 01.10.2015
 */
public class EpochAwaitingTimeoutChecker implements ITaskExecutor {

    private static final String REC_NAMES_SEPARATOR = ", ";

    @Deprecated
    public boolean execute(TaskVO task) {

        PublishDestination dest = PublishProfile.getProfile().get().getDestination();

        Collection<String> destinations = dest.getMainTypes();
        for (String type: destinations) {

            List<RecordSendingDate> recs = getRecordSendingDates(dest.getWhenReadyTypeId(type));
            if (recs.isEmpty()) {
                continue;
            }
            TreeMap<Time, List<RecordSendingDate>> groupByTime = groupRecordsByTime(recs);
            sendNotification(groupByTime, type);
        }

        reschedule(task, getScheduleShortDelay());
        return true;
    }

    @Deprecated
    protected List<RecordSendingDate> getRecordSendingDates(int wrType) {
        int limit = getQueryLimit();
        int offset = 0;
        Date controlDate = getControlDate();
        IResultsStorage resStor = getResultsStorage();
        List<RecordSendingDate> recs = new ArrayList<RecordSendingDate>();

        List<RecordSendingDate> tmpRecs = resStor.getRecSendDateWhenReadyUnpublished(wrType, offset, limit);
        while (!tmpRecs.isEmpty()) {
            for (RecordSendingDate rec : tmpRecs) {
                if (rec.getDate().before(controlDate)) {
                    recs.add(rec);
                }
            }

            offset += limit;
            tmpRecs = resStor.getRecSendDateWhenReadyUnpublished(wrType, offset, limit);
        }

        return recs;
    }

    protected int getQueryLimit() {
        return CochraneCMSPropertyNames.getDbRecordBatchSize();
    }

    protected Date getControlDate() {
        int awaitingTimeout = CochraneCMSProperties.getIntProperty("cms.cochrane.epoch.awaiting_timeout", 0);
        return new Date(System.currentTimeMillis() - awaitingTimeout);
    }

    protected IResultsStorage getResultsStorage() {
        return AbstractManager.getResultStorage();
    }

    protected TreeMap<Time, List<RecordSendingDate>> groupRecordsByTime(List<RecordSendingDate> recs) {
        Collections.sort(recs);
        TreeMap<Time, List<RecordSendingDate>> group = new TreeMap();
        for (RecordSendingDate rec : recs) {
            Time time = getTime(rec.getDate());

            List<RecordSendingDate> subRecs = group.get(time);
            if (subRecs == null) {
                subRecs = new ArrayList<RecordSendingDate>();
                group.put(time, subRecs);
            }
            subRecs.add(rec);
        }

        return group;
    }

    //CHECKSTYLE:OFF MagicNumber
    protected Time getTime(Date date) {
        long millis = getElapsedTime(date);
        long sec = millis / 1000;
        long min = sec / 60;
        long hours = min / 60;

        Time time;
        if (hours > 0) {
            time = new Time((int) hours, TimeUnit.HOUR);
        } else if (min > 0) {
            time = new Time((int) min % 60, TimeUnit.MIN);
        } else {
            time = new Time((int) sec % 60, TimeUnit.SEC);
        }

        return time;
    }

    protected long getElapsedTime(Date date) {
        return System.currentTimeMillis() - date.getTime();
    }

    protected void sendNotification(TreeMap<Time, List<RecordSendingDate>> groupByTime, String keyType) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(MessageSender.MSG_PARAM_REQUEST, PubType.TYPE_WOL.equals(keyType) ? "WOL"
                : PubType.TYPE_SEMANTICO.equals(keyType) ? "HW" : keyType);
        params.put(MessageSender.MSG_PARAM_REPORT, getTimeMessage(groupByTime));

        sendMessage(params);
    }

    protected String getTimeMessage(TreeMap<Time, List<RecordSendingDate>> groupByTime) {
        StringBuilder msg = new StringBuilder();
        for (Time time : groupByTime.keySet()) {
            Map<String, String> params = new HashMap<String, String>();
            params.put("time", time.toString());
            params.put(MessageSender.MSG_PARAM_LIST, getRecordListAsString(groupByTime.get(time)));

            msg.append(CochraneCMSProperties.getProperty("publishing.epoch_awaiting_timeout.time_message", params))
                    .append(";\n");
        }

        return msg.toString();
    }

    protected String getRecordListAsString(List<RecordSendingDate> recs) {
        StringBuilder strb = new StringBuilder();
        for (RecordSendingDate rec : recs) {
            strb.append(rec.getRecordName());
            if (rec.isTa()) {
                strb.append(" (").append(rec.getLanguage()).append(")");
            }
            strb.append(REC_NAMES_SEPARATOR);
        }
        strb.delete(strb.length() - REC_NAMES_SEPARATOR.length(), strb.length());

        return strb.toString();
    }

    protected void sendMessage(Map<String, String> params) {
        MessageSender.sendMessage("epoch_awaiting_timeout", params);
    }

    protected String getScheduleLongDelay() {
        return CochraneCMSProperties.getProperty("cms.cochrane.epoch.awaiting_timeout.checker.schedule.long_delay",
                "0 0/8 * * * ?");
    }

    protected String getScheduleShortDelay() {
        return CochraneCMSProperties.getProperty("cms.cochrane.epoch.awaiting_timeout.checker.schedule.short_delay",
                "0/15 * * * * ?");
    }

    protected void reschedule(TaskVO task, String newSchedule) {
        ProcessHelper.rescheduleIfChanged(task, newSchedule);
    }

    /**
     *
     */
    public static class RecordSendingDate implements Comparable<RecordSendingDate> {

        private final String recordName;
        private final String lang;
        private final Date date;

        public RecordSendingDate(String recordName, String lang, Date date) {
            this.recordName = recordName;
            this.lang = lang;
            this.date = date;
        }

        public String getRecordName() {
            return recordName;
        }

        public String getLanguage() {
            return lang;
        }

        public Date getDate() {
            return date;
        }

        public boolean isTa() {
            return StringUtils.isNotEmpty(lang);
        }

        public int compareTo(RecordSendingDate rec) {
            return this.date.compareTo(rec.getDate());
        }
    }

    /**
     *
     */
    protected static class Time implements Comparable<Time> {

        private final int value;
        private final TimeUnit timeUnit;

        public Time(int value, TimeUnit timeUnit) {
            this.value = value;
            this.timeUnit = timeUnit;
        }

        public int getValue() {
            return value;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }

        public int compareTo(Time time) {
            int c = this.timeUnit.compareTo(time.getTimeUnit());
            if (c == 0) {
                return this.value - time.getValue();
            } else {
                return c;
            }
        }

        //CHECKSTYLE:OFF MagicNumber
        @Override
        public int hashCode() {
            return 31 * value + timeUnit.hashCode();
        }
        //CHECKSTYLE:ON MagicNumber

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Time
                    && compareTo((Time) obj) == 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return value + " " + timeUnit.toString();
        }
    }

    /**
     *
     */
    protected enum TimeUnit {
        SEC("sec"),
        MIN("min"),
        HOUR("hour(s)");

        private final String label;

        TimeUnit(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
