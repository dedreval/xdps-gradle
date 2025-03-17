package com.wiley.cms.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 09.07.13
 */
public interface IProcessCache {

    void addProcess(ProcessVO process);

    ProcessVO getProcess(int id);

    ProcessVO removeProcess(int id);

    String printState();
}
