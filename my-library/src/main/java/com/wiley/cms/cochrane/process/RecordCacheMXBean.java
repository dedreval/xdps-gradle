package com.wiley.cms.cochrane.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/10/2015
 */
public interface RecordCacheMXBean {

    void update();

    void clear();

    String printState();
}
