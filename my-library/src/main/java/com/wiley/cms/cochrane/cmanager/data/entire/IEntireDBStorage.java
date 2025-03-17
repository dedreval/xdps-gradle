package com.wiley.cms.cochrane.cmanager.data.entire;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.record.EntireRecordVO;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */
public interface IEntireDBStorage {
    /**
     * Remove record from Entire DB
     *
     * @param db         Entire DB Name
     * @param recordName Record name
     */
    void remove(String db, String recordName);

    /**
     * Finds distinct SYSREV record names.
     *
     * @return List of distinct record names.
     */
    List<String> findSysrevRecordNames();

    List<String> findSysrevReviewRecordNames();

    /**
     * Finds SYSREV record names in given issue
     *
     * @param issueId - issue id
     * @return Sysrev record names that are present in given issue
     */
    List<String> findSysrevRecordNames(int issueId);

    /**
     * Finds unit status of the most recent record in Entire DB
     *
     * @param database   DB Record
     * @param recordName Record name
     * @return Unit Status id of the most recent record in Entire DB or null, if no such record in DB
     */
    Integer findUnitStatus(DatabaseEntity database, String recordName);

    /**
     * Create new record in Entire DB.
     * If record already exists - updates existing record with new data.
     *
     * @param dbId       ClDbEntity id
     * @param recordName Record name
     * @param setApproved if TRUE, an approved flag is set for the related record (used for CENTRAL automate publishing)
     */
    void updateRecord(int dbId, String recordName, boolean setApproved);

    /**
     * Checks if any record exists in Entire DB for given DB name and issue number.
     *
     * @param databaseId   Database identifier
     * @param issueNumber  The full issue number (last issue published)
     * @param recordName   Record name
     * @return True, if any record exists in Entire DB for given DB name and Record name, False - otherwise.
     */
    boolean isRecordExists(int databaseId, int issueNumber, String recordName);

    void getRecordExists(int databaseId, int issueNumber, Set<String> ret);

    List<Integer> getRecordIds(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                              int orderField, boolean orderDesc, String text, int lastIssuePublished);

    Map<Integer, String> getRecordIdsAndNames(String db, int beginIndex, int amount, String[] items,
                                              String fileStatus, int orderField, boolean orderDesc,
                                              String text);

    List<EntireDBEntity> getRecordList(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                       int orderFields, boolean orderDesc);

    List<EntireDBEntity> getRecordList(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                       int orderFields, boolean orderDesc, int processId);

    int getRecordListCount(int dbId);

    int getRecordListCount(String db, String[] items, String fileStatus);

    int getRecordListCount(String db, String[] items, String text, String fileStatus, int orderField,
                           boolean orderDesc);

    EntireDBEntity findRecord(int recordId);

    List<String> findRecordNames(List<Integer> ids);

    List<String> getRecordNamesByDbNameAndUnitStatuses(String dbName, List<Integer> unitStatuses);

    List<String> getRecordNamesFromListByDbName(List<String> recordNames, String dbName);

    List<String> getRecordNamesFromListByDbNameAndUnitStatuses(List<String> recordNames, String dbName,
                                                               List<Integer> unitStatuses);

    /**
     * Returns list of available last issue published values for specified DB.
     * The list will be empty if EntireDBEntity records are not exists.
     * @param dbId db id
     * @return last issue published list
     */
    List<Integer> getLastIssuePublishedList(int dbId);

    /**
     * Returns only list of ids that belong to specified issue.
     * The list will be empty if records satisfying the condition wasn't found.
     * @param lastIssuePublished last issue published
     * @param recIds list of record ids
     * @return list of records belong to specified issue
     */
    List<Integer> getIdsBelongToIssue(int lastIssuePublished, List<Integer> recIds);

    Collection<String> clearDb(Integer clDbId, boolean clearPdf) throws Exception;

    void clearRecord(int clDbId, String recordName, boolean clearPdf) throws Exception;

    void clearRecord(int clDbId, String recordName, boolean clearPdf, boolean restoreBackup) throws Exception;

    List<Integer> findIssuesWithEditorials();

    List<String> findEditorialRecordNames();

    List<String> findEditorialRecordNames(int issueId);

    int getEditorialCountRecordsByIssue(int issuePublished);

    List<EntireDBEntity> getEditorialRecordsByIssue(int issuePublished, int size, int offset);

    /**
     * Returns a unique list of record names of EntireDBEntity for specified DB.
     * Max count of the records can be restricted by specifying limit parameter. If limit parameter
     * is below 0 or equals to it the list will contain all records available in the table for specified DB.
     * If offset is positive value, the records will be retrieved from specified position.
     * @param dbName db name
     * @param offset position of the first result
     * @param limit max count of returned records
     * @return
     */
    List<String> findRecordNames(String dbName, int offset, int limit);

    EntireRecordVO findRecordByName(Integer clDbId, String recordName);

    EntireDBEntity findRecordByName(DatabaseEntity database, String recordName);

    //EntireDBEntity findRecordBySortTitle(DatabaseEntity database, String unitTitle);

    //EntireRecordVO findRecordBySortTitle(int clDbId, String unitTitle);

    List<EntireDBEntity> getRecordsByIds(Collection<Integer> ids);

    List<EntireDBEntity> getRecordsOrderById(DatabaseEntity database, int startId);

    List<RecordPublishVO> getRecordPublishVOByRecordIdsAndStates(List<Integer> recIds, List<Integer> states);

    /**
     * Returns record id list from EntireRecordPublishEntity table related with specified record ids from
     * EntireDBEntity table and states.
     * @param recIds record ids from EntireDBEntity table
     * @param state acceptable record publish state
     * @return record id list
     */
    List<Integer> getRecordIds(List<Integer> recIds, int state);

    /**
     * Returns record id list from EntireRecordPublishEntity table related with specified record ids from
     * EntireDBEntity table and states.
     * @param recIds record ids from EntireDBEntity table
     * @param states list of acceptable record publish states
     * @return record id list
     */
    List<Integer> getRecordIds(List<Integer> recIds, List<Integer> states);

    /**
     * Persists EntireRecordPublishEntity to DB.
     * @param recIdsMap id->VO map representing the record identifiers related to persisting entities
     */
    void persistRecordPublish(Map<Integer, RecordPublishVO> recIdsMap);

    /**
     * Removes EntireRecordPublishEntity from DB.
     * @param recIds record ids related to the removing entities
     */
    void deleteRecordPublish(Collection<Integer> recIds);

    void updateRecordPublishStateByRecordIds(Date date, int state, Collection<Integer> recordIds);
}