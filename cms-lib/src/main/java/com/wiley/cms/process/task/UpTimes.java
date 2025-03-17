package com.wiley.cms.process.task;

import java.io.Serializable;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/15/2017
 */
public class UpTimes implements Serializable {
    private static final long serialVersionUID = 1L;

    // new event time
    private volatile long newUpdateTime;

    // last check time
    private volatile long lastTime;

    // -1 (not init) | 0 (set up) | 1 (a first check) | last time (a next check)
    private volatile long controlTime = -1;

    public boolean wasSetDown() {
        return wasInitialized() && newUpdateTime < lastTime && controlTime == lastTime;
    }

    public boolean wasInitialized() {
        return lastTime > 0;
    }

    public boolean wasSetUp() {
        return controlTime == 0 && newUpdateTime > 0;
    }

    public void setLastControlTime(long time) {
        lastTime = time;
        setControlTime(time);
    }

    public void setLastTime(long time) {
        lastTime = time;
        setControlTime(controlTime <= 0 ? 1 : time);
    }

    private void setControlTime(long time) {
        controlTime = time;
    }

    /**
     * Set up time of new event
     * @param time  time (ms) of new event
     */
    public void setUpTime(long time) {
        newUpdateTime = time;
        setControlTime(0);
    }

    @Override
    public String toString() {
        return String.format("lastTime=%d newUpdateTime=%d controlTime=%d", lastTime, newUpdateTime, controlTime);
    }
}
