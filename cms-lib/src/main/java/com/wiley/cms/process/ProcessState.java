package com.wiley.cms.process;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 */
public enum ProcessState {
    NONE, WAITED, STARTED, SUCCESSFUL, FAILED, STOPPED;

    public static final String RESTARTED = "RD";

    public static final List<ProcessState> NOT_STARTED_STATES = new ArrayList<>();
    public static final List<ProcessState> EXECUTED_STATES = new ArrayList<>();
    public static final List<ProcessState> FINISHED_STATES = new ArrayList<>();

    static {
        FINISHED_STATES.add(SUCCESSFUL);
        FINISHED_STATES.add(FAILED);

        EXECUTED_STATES.add(WAITED);
        EXECUTED_STATES.add(STARTED);

        NOT_STARTED_STATES.add(NONE);
        NOT_STARTED_STATES.add(STOPPED);
    }

    public boolean isStopped() {
        return STOPPED == this;
    }

    public boolean isStarted() {
        return STARTED == this;
    }

    public boolean isWaited() {
        return WAITED == this;
    }

    public boolean isSuccessful() {
        return SUCCESSFUL == this;
    }

    public boolean isCompleted() {
        return this == SUCCESSFUL || this == FAILED;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public static boolean isStarted(ProcessState state) {
        return STARTED == state;
    }

    public static boolean isSuccessful(ProcessState state) {
        return SUCCESSFUL == state;
    }

    public static boolean isCompleted(ProcessState state) {
        return state == SUCCESSFUL || state == FAILED;
    }

    public static boolean isFailed(ProcessState state) {
        return state == FAILED;
    }

    public static List<ProcessState> asList(ProcessState[] states) {
        return new AbstractList<ProcessState>() {
            public ProcessState get(int index) {
                return states[index];
            }

            public int size() {
                return states.length;
            }
        };
    }
}
