package com.wiley.cms.process.task;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/11/2014
 */
public interface ITaskExecutor {

    int RESCHEDULE = -1;

    boolean execute(TaskVO task) throws Exception;

    /**
     * It can delay current execution event by some inner condition
     * @return a delay time (ms) where 0 is no delaying, -1 implies a delay to next scheduled time
     */
    default long canDelay() {
        return 0;
    }

    default ITaskExecutor initialize(String ... params) throws Exception {
        return this;
    }
}
