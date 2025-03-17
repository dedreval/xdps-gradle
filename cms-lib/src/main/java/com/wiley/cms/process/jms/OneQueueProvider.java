package com.wiley.cms.process.jms;

import javax.jms.Queue;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/24/2018
 */
public class OneQueueProvider implements IQueueProvider {

    private Queue oneQueue;

    public IQueueProvider setQueue(Queue queue) {
        oneQueue = queue;
        return this;
    }

    @Override
    public Queue getTaskQueue() {
        return oneQueue;
    }

    @Override
    public Queue getQueue(String name) {
        return oneQueue;
    }
}
