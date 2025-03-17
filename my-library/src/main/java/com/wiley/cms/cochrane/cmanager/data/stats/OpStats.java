package com.wiley.cms.cochrane.cmanager.data.stats;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 6/6/2022
 */
public class OpStats {
    public static final OpStats EMPTY = new OpStats() {
        @Override
        public void addTotalCompletedByKey(Integer key, Integer uniqueNumber) {
        }
        @Override
        public void addError(String error) {
        }
    };

    private int total;
    private int totalCompleted;
    private int totalSuccessful;
    private int current;
    private int deleted;

    private Map<Integer, OpStats> multiCounters = Collections.emptyMap();
    private Set<Integer> uniqueNumbers;
    private StringBuilder err;
                                                                                       
    public OpStats() {
        multiCounters = new HashMap<>();
        err = new StringBuilder();
    }

    public OpStats(List<Object[]> counters) {
        this();
        for (Object[] obj: counters) {
            long count = (Long) obj[1];
            OpStats stats = new OpStats((int) count, (int) count);
            multiCounters.put((Integer) obj[0], stats);
            totalCompleted += stats.getTotalCompleted();
        }
    }

    public OpStats(int totalCompleted, int total) {
        this(totalCompleted, total, 0);
    }

    public OpStats(int totalCompleted, int total, int totalSuccessful) {
        this.total = total;
        setTotalCompleted(totalCompleted);
        setTotalSuccessful(totalSuccessful);
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int value) {
        total = value;
    }

    public int getTotalCompleted() {
        return uniqueNumbers != null ? uniqueNumbers.size() : totalCompleted;
    }

    public void setTotalCompleted(int value) {
        totalCompleted = value;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int value) {
        current = value;
    }

    public int getTotalSuccessful() {
        return totalSuccessful;
    }

    public void setTotalSuccessful(int value) {
        totalSuccessful = value;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int value) {
        deleted = value;
    }

    public boolean isCompleted() {
        return total == totalCompleted;
    }

    public Map<Integer, OpStats> getMultiCounters() {
        return multiCounters;
    }

    public void addTotalCompletedByKey(Integer key) {
        addTotalCompletedByKey(key, null);
    }

    public void addTotalCompletedByKey(Integer key, Integer uniqueNumber) {
        if (key == null) {
            return;
        }
        OpStats stats = multiCounters.computeIfAbsent(key, f -> new OpStats(0, 0));

        if (uniqueNumber != null) {
            if (stats.uniqueNumbers == null) {
                stats.uniqueNumbers = new HashSet<>();
            }
            if (!stats.uniqueNumbers.contains(uniqueNumber)) {
                stats.totalCompleted++;
                totalCompleted++;
                stats.uniqueNumbers.add(uniqueNumber);
            }
        } else {
            stats.totalCompleted++;
            totalCompleted++;
        }
    }

    public void addError(String error) {
        if (err == null) {
            err = new StringBuilder();
        }
        err.append(error);
    }

    public String getErrors() {
        return err == null ? null : err.toString();
    }

    @Override
    public String toString() {
        return String.format("total %d: (completed %d (%d), successful %d deleted %d), current=%d",
                total, getTotalCompleted(), totalCompleted, totalSuccessful, deleted, current);
    }
}
