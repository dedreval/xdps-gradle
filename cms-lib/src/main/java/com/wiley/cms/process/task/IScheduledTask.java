package com.wiley.cms.process.task;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/9/2017
 */
public interface IScheduledTask {

    String getScheduledTemplate();

    default void updateSchedule(TaskVO task) throws ProcessException {

        String pattern = getScheduledTemplate();
        if (pattern == null || pattern.isEmpty()) {
            throw new ProcessException(String.format("the next automatic %s is disabled", task.getLabel()));
        }
        ProcessHelper.rescheduleIfChanged(task, pattern);
    }
}
