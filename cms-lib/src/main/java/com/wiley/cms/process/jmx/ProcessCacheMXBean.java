package com.wiley.cms.process.jmx;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/11/2015
 */
public interface ProcessCacheMXBean {

    void clear();

    String printState();
}
