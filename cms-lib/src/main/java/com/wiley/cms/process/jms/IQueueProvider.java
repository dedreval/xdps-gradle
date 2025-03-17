package com.wiley.cms.process.jms;

import javax.jms.Queue;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/21/2017
 */
public interface IQueueProvider {

    String TASK_QUEUE_DEFAULT = "taskQueue";

    Queue getTaskQueue();

    Queue getQueue(String name);
}
