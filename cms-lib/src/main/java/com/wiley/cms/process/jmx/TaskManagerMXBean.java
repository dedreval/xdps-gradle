package com.wiley.cms.process.jmx;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/9/2015
 */
public interface TaskManagerMXBean {

    String update();

    String printState();

    String execute(int taskId);
}
