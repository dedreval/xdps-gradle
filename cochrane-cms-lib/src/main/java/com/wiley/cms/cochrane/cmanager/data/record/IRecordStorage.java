package com.wiley.cms.cochrane.cmanager.data.record;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IRecordStorage {
    void remove(int dbId, String recordName);

    boolean isRecordExists(int dbId, String recordName);

    RecordEntity getRecordEntityById(int id);

    RecordEntity getEditorialRecordEntityById(int id);

    @Deprecated
    List<RecordVO> getRecordsSuccessfulRenderingByName(String name);

    List<RecordVO> getRecordVOsByIds(Collection<Integer> ids, boolean check4Ta);

    List<RecordEntity> getEditorialRecordsByIssue(int issueId, int size, int offset);

    List<RecordEntity> getDbRecordEntityByFirstCharList(int dbId, char str, int productSubtitle);

    List<CDSRVO> getCDSREntityByFirstCharList4Entire(char ch, int productSubtitle);

    List<RecordVO> getDbRecordVOByFirstCharList(int dbId, char ch, int productSubtitle);

    List<RecordVO> getRecordVOsByDF(Collection<Integer> dfIds);

    List<RecordEntity> getDbRecordEntityByNumStartedList(int dbId, int productSubtitle);

    List<RecordEntity> getRecordEntitiesByIds(Collection<Integer> ids);

    List<CDSRVO> getCDSRVOListByGroup4Entire(String group);

    List<CDSRVO> getCDSRVOByNumStartedList4Entire(int productSubtitle);

    List<RecordVO> getDbRecordVOByNumStartedList(int dbId, int productSubtitle);

    /**
     * Returns list of RecordEntity ids related to specified Issue DB id.
     * @param dbId Issue DB id from which record Ids should be received
     * @param items array of record names which ids should be received from specified DB
     * @param searchStatus status of records which ids should be received from specified DB
     * @param offset defines position of the first result to retrieve
     * @param limit max amount of record which ids should be received from specified DB
     * @return list of record Ids.
     */
    List<Integer> getDbRecordIdList(int dbId, String[] items, int searchStatus, int offset, int limit);

    List<RecordVO> getDbRecordVOList(int dbId);

    List<String> getRecordNamesByIds(List<Integer> ids);

    String[] getDbRecordListNames(int dbId, int beginIndes, int amount, String[] items, int searchStatus,
                                  String fileStatus, int orderField, boolean orderDesc);

    List<RecordEntity> getDbRecordList(int dbId, int beginIndex, int amount, String[] items,
                                       int searchStatus, String fileStatus, int orderField, boolean orderDesc);

    List<RecordEntity> getDbRecordList(int dbId, int beginIndex, int amount, String[] items,
                                       int searchStatus, String fileStatus, int orderField, boolean orderDesc,
                                       String text);

    List<RecordEntity> getDbRecordList(int dbId, int dfId, int beginIndex, int amount, String[] items,
                                       int searchStatus, String fileStatus, int orderField, boolean orderDesc,
                                       String text);

    List<CDSRVO4Entire> getRecordsByUnitStatusesFromEntire(int[] unitStatuses, int currentIss, int prevIss);

    int getDbRecordListCount(int dbId, String[] items, int searchStatus);

    int getDbRecordListCount(int dbId, String[] items, int searchStatus, String fileStatus);

    int getDbRecordListCount(int dbId, String[] items, int searchStatus, String text, String fileStatus);

    List<RecordEntity> getDFRecordList(int dfId, int beginIndex, int amount, int orderField, boolean orderDesc);

    List<RecordLightVO> getRecordLightVOList(int dbId, String[] items, int searchStatus);

    void updateRecordList(int dbId, String[] items, boolean approved, boolean rejected, int searchStatus);

    List<String> getRecordPathByDf(int dfId, boolean success);

    List<String> getRecordPathByDf(int dfId, boolean success, String recNames);

    List<ProductSubtitleVO> getProductSubtitles(int[] groups);

    //List<RecordVO> getDbRecordVOListByProductSubtitle(int dbId, int productSubtitleId);

    String getRecordQASResults(int id);

    List<String> getNewReviews(int dbId);

    List<String> getUpdatedReviews(int dbId);

    List<String> getNewProtocols(int dbId);

    List<String> getUpdatedProtocols(int dbId);

    List<RecordEntity> getCdsrRecords4Report(int dbId, List<Integer> ignoredStatuses);

    List<String> getRecordPathByDb(int dbId, int start, int pieceSize);

    List<String> getRecordPathByDf(int dfId, int start, int pieceSize);

    List<Object[]> getRecordPathMappedToId(Collection<Integer> recIds);

    List<UnitStatusVO> getUnitStatusList(List<Integer> unitStatuses);

    List<String> getRecordPathByNameAndIssue(int issueId, String name);

    @Deprecated
    List<Object[]> getWithdrawnRecords();

    @Deprecated
    List<String> getWithdrawnRecords(String dbName, int issueId);

    List<String> getWithdrawnRecords(int dbId, int dfId);

    /**
     * Get number details about QA related to the source package specified
     * @param dfId a source package identifier
     * @return record numbers related to delivery;
     */
    OpStats getQasCompleted(int dfId);

    int getQasUncompleted(int dfId);

    long getRecordCountByDf(int dfId);

    List<RecordEntity> getRecordsByDFile(DeliveryFileEntity dfEntity, boolean onlySuccessful);

    List<RecordEntity> getRecordsQasUncompleted(int dfId);

    List<Record> getRecordsForRendering(int dfId);

    List<RecordEntity> getRecordsByDFile(String recordNames, DeliveryFileEntity dfEntity);

    List<Integer> getRecordIdsByDfId(int dfId, int offset, int limit);

    long getRecordsCountByClDbAndNames(String recordNames, int numberRndPlans, ClDbEntity dbEntity);

    int setCompletedAndDisableForRecords(String recordNames, int numberRndPlans, ClDbEntity dbEntity);

    int setSuccessForRecords(String recordNames, int numberRndPlans, ClDbEntity dbEntity, boolean isEqualsNums);

    void updateRecords(Collection<String> records, int deliveryFileId, boolean status, ClDbEntity db);

    void flushQAResults(Collection<? extends IRecord> records, int dbId, boolean wr, boolean isTa, boolean toMl3g,
                        boolean setRender);

    void flushQAResults(IRecord record, boolean wr, boolean successful, String message, IActivityLog logger);

    int setRenderResults(Collection<Integer> ids, boolean successful, boolean completed);

    void updateRenderingStateQaSuccessByDfIds(List<Integer> dfIds, boolean state);

    void updateRecordsRenderingStatus(Collection<String> records, int packageFileId, boolean renderingSuccess,
                                      ClDbEntity db);

    long getCountRenderingCompletedByDfID(int dfId);

    long getCountRenderingSuccessfulByDfId(int dfId);

    long getCountRenderingFailedByDfId(int dfId);

    long getCountRenderingFailedQaSuccessByDfId(int dfId);

    long getCountQaByDfId(int dfId, boolean successful);

    long getCountRenderingByDfId(int dfId);

    List<RecordPublishVO> getRecordPublishVOByRecordIdsAndStates(List<Integer> ids, List<Integer> states);

    /**
     * Returns an array of record names & paths from RecordEntity table belong to specified delivery file.
     * The list will contain only record ids which qasSuccessful state equals to specified value.
     * @param dfId delivery file id
     * @param qasSuccessful qasSuccessful state of retrieved records
     * @param offset position of the first result
     * @param limit max count of returned records
     * @return The array of record names & paths
     */
    List<Object[]> getRecordPaths(int dfId, boolean qasSuccessful, int offset, int limit);

    /**
     * Returns record id list from DbRecordPublishEntity table related with specified record ids from
     * RecordEntity table and states.
     * @param recIds record ids from RecordEntity table
     * @param states list of acceptable record publish states
     * @return record publish id list
     */
    List<Integer> getRecordIds(List<Integer> recIds, List<Integer> states);

    List<Integer> getRecordIds(Collection<Integer> ids, int state);

    long getRecordPublishCountByStateAndDbId(int state, int dbId);

    long getRecordPublishCountByRecordIdsAndStates(List<Integer> ids, List<Integer> states);

    void updateRecordPublishStateByRecordIds(Date date, int state, List<Integer> recIds);

    void deleteRecordPublishByRecordIds(Collection<Integer> recIds);

    void persistRecordPublish(Map<Integer, RecordPublishVO> recIds);

    void getLatestQasResultsByRecordsIds(Collection<Integer> recordIds, Map<Integer, String> results);

    List<IRecord> getTinyRecords(Collection<Integer> ids);
}
