package com.wiley.cms.cochrane.cmanager.publish;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/23/2019
 */
public interface IPublishScheduler {

    /**
     * It sets (or cancels) scheduled sending for publication a specified article
     * @param recordId    a local identifier of the article
     * @param startDate   a scheduled date to start sending;
     *                    if NULL - the already scheduled sending should be canceled
     * @param formatter   a formatter to parse a scheduled date to IN/OUT format
     * @param zoneId      a zone (should be UTC)
     * @param user
     * @return            an array; its first element is an article's title,
     *                              and the second one is an actual date of sending
     * @throws Exception
     */
    String[] scheduleSending(Integer recordId, String startDate, DateTimeFormatter formatter,
                             ZoneId zoneId, String user) throws Exception;

    /**
     *  It sets (or cancels) scheduled sending for publication a latest version of a specified article
     * @param cdNumber     a cd number of the article
     * @param dbId         a base database key
     * @param startDate    a scheduled date to start sending;
     *                     if NULL - the already scheduled sending should be canceled
     * @param formatter    a formatter to parse a scheduled date to IN/OUT format
     * @param zoneId       a zone (should be UTC)
     * @param user
     * @return             an array; its first element is an article's title,
     *                               and the second one is an actual date of sending
     * @throws Exception
     */
    String[] scheduleSending(String cdNumber, Integer dbId, String startDate, DateTimeFormatter formatter,
                             ZoneId zoneId, String user) throws Exception;

    /**
     *
     * @param cdNumber
     * @param dbId
     * @param formatter
     * @param zoneId
     * @return             an array; its first element is an article's title,
     *      *                  and the second one is an actual date of sending
     * @throws Exception
     */
    String[] findScheduledSending(String cdNumber, Integer dbId, DateTimeFormatter formatter, ZoneId zoneId)
            throws Exception;

    /**
     * It cancels scheduled sending for publication a specified article
     * @param recordId     a local identifier of the article
     * @param user
     * @return             an article's title
     * @throws Exception
     */
    String[] cancelSending(Integer recordId, String user) throws Exception;


    String[] cancelSending(String cdNumber, Integer dbId, String user) throws Exception;


    void scheduleSendingDS(Integer dbId, String startDate, String user) throws Exception;
}
