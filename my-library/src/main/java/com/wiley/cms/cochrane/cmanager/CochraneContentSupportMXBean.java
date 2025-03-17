package com.wiley.cms.cochrane.cmanager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/8/2016
 */
public interface CochraneContentSupportMXBean {

    String downloadFromLocal(String path);

    String downloadCentralToLocal(int issueNumber, String fileName);

    //String uploadAriesPackages(int issueId, int fullIssueNumber);

    String validateWml3gCDSR(String cdnumberList, boolean withRules);

    String validateWml3gCDSR(boolean withRules) throws Exception;

    String validateRevman() throws Exception;

    String reprocessWhenReady(int issueNumber, String cdnumberListStr, boolean upload) throws Exception;

    String cleanRepository(int actualIssueLimit, int actualMonthsLimit);

    String getRecordsState(int issueNumber, String cdNumber);

    String completeFlowLogEvents();

    String reproduceFlowLogEvent(Long id);

    String reproduceFlowLogEvents();
}
