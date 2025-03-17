package com.wiley.cms.cochrane.process;

import java.util.Collection;

import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.parser.QaParsingResult;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
public interface IQaManager extends IProcessManager {

    void startQa(DeliveryPackageInfo manifest, int packageId, String packageName, String dbName) throws Exception;

    void sendQa(ProcessVO pvo, Integer packageId, String packageName, Collection<IRecord> records) throws Exception;

    /**
     * It removes withdrawn record, checks if QA for the all records is unsuccessful.
     * It can be called once in the end of QA processing. Sets a withdrawn records number to the opStats
     * @param creatorId  a parent process created this QA job
     * @param issueId    an Issue identifier
     * @param dbName     a database name
     * @param df         it represents the source's delivery file
     * @param opStats    record numbers related to delivery
     *
     */
    void finishQa(int creatorId, Integer issueId, String dbName, DeliveryFileVO df, OpStats opStats);

    void acceptQaResults(int jobId);


    /**
     *
     * @param processId
     * @param result
     * @param df
     * @param isCDSR
     * @param isTranslated
     * @param toMl3g
     * @param setRender
     * @return  record numbers stats related to delivery;
     */
    OpStats updateRecords(int processId, QaParsingResult result, DeliveryFileVO df,
                          boolean isCDSR, boolean isTranslated, boolean toMl3g, boolean setRender);

    void parseRecords(Collection<Record> records, String dbName, int issueId, boolean isWhenReady);

    int getCountQaCompletedRecords(int dfId, boolean successful);

    long getRecordCountByDf(int id);
}
