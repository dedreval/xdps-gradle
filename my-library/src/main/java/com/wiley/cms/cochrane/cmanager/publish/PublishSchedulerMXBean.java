package com.wiley.cms.cochrane.cmanager.publish;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/4/2019
 */
public interface PublishSchedulerMXBean {

    String[] scheduleSending(String cdNumber, Integer dbId, String startDate, String user) throws Exception;

    void scheduleSendingDS(Integer dbId, String startDate, String user) throws Exception;

    String[] findScheduledSending(String cdNumber, Integer dbId) throws Exception;
}
