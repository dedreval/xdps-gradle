package com.wiley.cms.cochrane.cmanager;

import java.net.URI;
import java.util.Collection;

import com.wiley.cms.process.res.ProcessType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/15/2018
 */
public interface ICochraneContentSupport {
    void reloadComponentFactories();

    String getRecordsState(Integer clDbId, Collection<String> cdNumbers, String operation);

    String getRecordsStateByPackage(Integer dfId, String operation);

    void uploadAriesPackages(Integer issueId, int fullIssueNumber);

    void uploadMl3gPackage(String packageName, URI packageUri, String dbName, ProcessType pt);

    String reproduceFlowLogEvents();


    // for admin page
    String downloadFromLocal(String path);

    String downloadCentralToLocal(int issueNumber, String fileName);

    String getRecordsState(int issueNumber, String cdNumber);

    String reprocessWhenReady(int issueNumber, String cdNumbersStr, boolean upload) throws Exception;

    String cleanRepository(int actualIssueLimit, int actualMonthsLimit);

    String reproduceFlowLogEvent(Long id);

    String completeFlowLogEvents();
}
