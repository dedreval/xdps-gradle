package com.wiley.cms.cochrane.cmanager;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.cmanager.data.DelayedThread;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 02-Apr-2007
 */
public interface IQaService {
    @Deprecated
    void startQas(List<URI> urisList, int pckId, String pckName, boolean delay) throws Exception;

    @Deprecated
    void startQas(List<URI> urisList, int pckId, String pckName, boolean delay, URI callback) throws Exception;

    @Deprecated
    void updateDeliveryFile(int dfId, boolean isSuccess);

    RecordEntity updateCCARecord(String recordName, String dbName, int issueId, boolean successful, String result,
                                 String unitTitle, Integer unitStatusId);

    @Deprecated
    void updateRecords(Collection<Record> records, String dbName, int issueId,
                       boolean isTranslatedAbstracts, boolean isMeshterm, boolean isWhenReady) throws NamingException;

    @Deprecated
    void updateRecordsQAStatuses(Collection<String> records, String dbName, int issueId, int deliveryFileId,
                                 boolean status);

    void parseSources(Collection<Record> records, String dbName, int issueId, boolean isWhenReady);

    @Deprecated
    void writeResultToDb(int jobId, String qaResult);

    @Deprecated
    DelayedThread getQaNextJob();

    @Deprecated
    void deleteQasResultByDf(int dfId);

    void updateQaResults(int recordId, String qasResultMessages);
}