package com.wiley.cms.cochrane.cmanager.data;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaCdsrDoiViewEntity;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.EpochAwaitingTimeoutChecker;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public interface IResultsStorage {

    int createIssue(String title,
                    Date date,
                    int number,
                    int year,
                    Date publishDate);

    boolean isExistsIssueByYearAndNumber(int id, int year, int number, boolean equalAndMore);

    void mergeIssue(IssueEntity issue);

    List<RecordEntity> getIssueRecordEntityList(int issueId);

    Map<String, String> getSimilarRecordsMap(int recordId);

    int getIssueListCount();

    List<IssueEntity> getIssueList();

    List<IssueEntity> getIssueList(int beginIndex, int limit);

    List<IssueEntity> getIssuesWithEditorials();

    IssueEntity getIssue(int issueId);

    List<DeliveryFileEntity> getDeliveryFileList(int issueId, int interval);

    List<DeliveryFileEntity> getLastDeliveryFileList(int issueId, int amount);

    List<DeliveryFileVO> getDeliveryFileList(int dbId, Integer... statuses);

    DeliveryFileEntity getDeliveryFileEntity(Integer deliveryFileEntityId);

    void deleteDeliveryFileEntity(int deliveryFileEntityId);

    ClDbEntity getDb(Integer clDbId);

    void mergeDb(ClDbEntity db);

    int createDb(int issueId, String title, int priority);

    ClDbEntity createDb(IssueEntity issueEntity, DatabaseEntity database, int priority);

    int findDb(int issueId, String title);

    int findOpenDb(int issueId, String title) throws CmsException;

    int findOpenDb(int issueYear, int issueNumber, String dbName) throws CmsException;

    List<ClDbEntity> getDbList(int issueId);

    List<ClDbEntity> getDbList(Integer issueId, boolean withSpd);

    void setDbApproved(int clDbId, boolean approved);

    RenderingEntity getRendering(int id);

    List<RenderingEntity> getRenderingList(int id);

    void mergeRendering(RenderingEntity rendering);

    void mergeRecord(RecordEntity record);

    void mergeRecord(RecordEntity record, String title);

    void mergeRecord(RecordWrapper record);

    Map<String, ICDSRMeta> findLatestMetadata(Collection<String> cdNumbers, boolean withHistory);

    ICDSRMeta findLatestMetadata(String cdNumber, boolean withHistory);

    ICDSRMeta findMetadataToIssue(int issueNumber, String cdNumber, boolean onlyIssueEqual);

    ICDSRMeta findLatestMetadata(String cdNumber, int pubNumber);

    List<ICDSRMeta> findAllMetadata(String dbName, int skip, int batchSize);

    ICDSRMeta getMetadata(int recordId);

    List<String> getOpenAccessCDSRNames(int issueNumber);

    List<RecordMetadataEntity> findRecordMetadata(String recordName);

    ICDSRMeta findPreviousMetadata(String cdNumber, Integer historyNumber);

    ICDSRMeta findPreviousMetadata(int issueNumber, String cdNumber, int pubNumber);

    List<? extends ICDSRMeta> findLatestMetadataHistory(String cdNumber);

    List<RecordMetadataEntity> findRecordsMetadata(int issueId, String dbName) throws CmsException;

    ICDSRMeta getCDSRMetadata(String cdNumber, int pubNumber);

    RecordEntity getRecord(int recordId);

    void removeRecord(BaseType baseType, int recordId, boolean spd, boolean canceled);

    void clearRecordsRenderings(RecordEntity r);

    RecordEntity getRecord(int issue, String db, String name);

    List<Record> getRecordsByDfAndNames(int dfId, Collection<String> names);

    List<RecordEntity> getRecordsByDb(Integer dbId, Collection<String> cdNumbers);

    int getApprovedRecordCount(int dbId);

    int getRejectedRecordCount(int dbId);

    boolean existsDoubleRecord(int issueId, int dbId, String recordName);

    int findRecord(int dbId, String recordName);

    RecordEntity getRecord(int dbId, String recordName);

    void createRecordsWithoutManifests(SortedMap<String, String> recs, int dbId, int deliveryFileId);

    void createRecords(Map<String, String> recs, int dbId, int deliveryFile, Set<String> recordsWithRawData);

    void createRecords(Map<String, String> recs, int deliveryFileId, Set<String> recordsWithRawData,
                       Set<String> goodNames, Set<String> delNames, boolean ta);

    void createRecords(Map<String, IRecord> records, int dbId, int dfId);

    int createDeliveryFile(String packageFileName, int statusBegin);

    int createDeliveryFile(String packageFileName, int type, int statusBegin, int interimStatus);

    int createDeliveryFile(int dbId, String name, int type, int status, int interimStatus, String vendor);

    String setDeliveryFileStatus(int deliveryFileId, int status, boolean isInterim);

    void setDeliveryFileStatus(Integer deliveryFileId, Integer status, Integer iStatus, Integer mStatus);

    String setDeliveryFileStatus(int deliveryFileId, int status, boolean isInterim, int type);

    void updateDeliveryFileStatus(int deliveryFileId, int status, Set<Integer> notChangeableStatuses);

    DeliveryFileEntity updateDeliveryFile(int id, int issueId, String vendor, int dbId);

    List<RecordLightVO> getRecordLightVOList(List<String> list, int issueId, String dbName);

    List<RecordLightVO> getRecordLightVOListForRecords(List<Record> records, int issueId, String dbName);

    int getRecordCount(int dbId);

    int getRecordCount(int dbId, String name);

    List<RecordEntity> getRecords(int dbId);

    List<RecordEntity> getRecordsOrderById(int dbId, int startId);

    int getDeliveryFilePrevStatus(int pckId);

    int findIssue(int year, int issueNumber);

    int findOpenIssue(int year, int issueNumber) throws CmsException;

    IssueEntity findOpenIssueEntity(int year, int issueNumber) throws CmsException;

    @Deprecated
    Date getLastPublishedDateByRecordNameAndPublishType(String recordName, String publishType);

    Date[] getLastPublishedCCADate(String recordName);

    RecordMetadataEntity findRecordMetadataForEDIAndCCAFirstOnline(BaseType bt, String cdNumber);

    void updatePublishedCCA(Collection<Integer> ids, boolean success);

    // <Publish>
    PublishTypeEntity getPublishType(String name);


    List<PublishEntity> findPublishesByDbAndType(Integer dbId, Integer type);


    PublishEntity updatePublish(int publishId, boolean sending, boolean isSent, Date sendingDate, boolean waiting);

    PublishEntity updatePublish(int publishId, String fileName, boolean generating, boolean isGenerated,
                                Date generationDate, boolean waiting, long size);

    PublishEntity updatePublishUnpacking(int publishId, boolean unpacking, Date unpackingDate, boolean waiting);

    void setStartSendingAndUnpackingDates(Date date, int publishId, boolean sending, boolean unpacking);

    void setDeleting(int publishId, boolean deleting, boolean waiting);

    PublishEntity setGenerating(int publishId, boolean generating, boolean waiting, long size);

    PublishEntity setSending(int publishId, boolean sending, boolean waiting, boolean onSentFailed);

    PublishEntity setUnpacking(int publishId, boolean unpacking, boolean waiting);

    boolean isDeleting(int dbId, String type);

    boolean isGenerating(int dbId, String type);

    String getGenerationDate(int dbId, String type);

    boolean isSending(int dbId, String type);

    String getSendingDate(int dbId, String type);

    List<PublishEntity> findPublish(int dbId, String type);

    PublishEntity findPublish(int publishId);

    void createSearchQuery(String text, String area, String fileStatus, int systemStatus, Date date) throws Exception;

    List<SearchQueryEntity> getSearchQueryList(int beginIndex, int amount);

    int getSearchQueryListCount();

    List<ClDbEntity> listClDbEntityByIssueId(Integer issueId) throws Exception;

    List<Integer> listIssueIdForArchiving(int liveCount) throws Exception;

    DeliveryFileEntity findLastPackageByIssueAndName(int issueId, String name);

    DeliveryFileEntity findLastPackageByTypeAndName(int packageType, String name);

    List<DeliveryFileEntity> findNotCompletedPackage();

    List<Object[]> getRecordsByDeliveryFile(int deliveryId);

    List<Integer> getNotCompletedJobId(int df);

    void deleteStartedJobId(int jobId);

    void deleteStartedJobQaId(int jobId);

    DeliveryFileVO getDeliveryFileVO(int issueId, String db, String name);

    DeliveryFileVO getDeliveryFileVO(int id);

    List<DeliveryFileVO> getDeliveryFiles(int clDbId);

    List<Integer> getStartedJobForPlan(Integer dfId, String plan);

    List<Integer> getNotCompletedQaJobId(Integer id);

    long findNotCompletedPackageByDb(int dbId);

    //IssueEntity getLastLoadedSysrevIssue();

    //IssueEntity getLastLoadedDatabaseIssue(String dbName);

    IssueEntity getLastApprovedDatabaseIssue(String dbName);

    IssueEntity getLastNonPublishedIssue();

    DatabaseEntity getDatabaseEntity(String name);

    UnitStatusEntity getUnitStatus(String name, boolean isCDSR);

    List<CcaEntity> getCcaEntitiesByName(String name);

    CcaEntity saveCcaEntity(String name, Date date);

    void saveDoiEntity(CcaEntity cca, String doi, String name);

    void createWhenReadyPublishSuccess(int dfId, String exportType);

    void createWhenReadyPublishFailure(int dfId, String exportType, String message);

    int setWhenReadyPublishStateByDeliveryFile(int oldState, int newState, int dfId);

    int setWhenReadyPublishStateByDeliveryFile(int newState, int dfId, boolean successful);

    int setWhenReadyPublishState(int oldState, int newState, int dbId);

    void setWhenReadyPublishState(int newState, int recordId);

    int getRecordCountByDeliveryFileAndState(Integer dfId, int state);

    /**
     * Returns list of record names and their sending dates represented as RecordSendingDate object.
     * This list is collected from PublishedAbstractEntity and PublishEntity tables and includes only records
     * which ones are in sending stage.
     * @param skip position of the first result
     * @param batchSize max count of returned records
     * @return list of RecordSendingDate objects
     */
    @Deprecated
    List<EpochAwaitingTimeoutChecker.RecordSendingDate> getRecSendDateWhenReadyUnpublished(int wrType,
                                                                                           int skip, int batchSize);

    List<PublishedAbstractEntity> getPublishedAbstracts(Collection<Integer> recordNumbers, Integer dbId);

    List<CcaCdsrDoiViewEntity> getCcaCdsrDoiViewByCdsrName(String cdsrName);

    String getRecordTitle(Integer titleId);

    OpStats getPublishStatsOnSent(Integer publishId, Integer dbId, Collection<Integer> publishTypesIds);

    void getTotalPublishStatsOnPublished(Integer dbId, Collection<Integer> publishTypesIds, OpStats stats);


    OpStats getPublishStatsOnOffline(BaseType baseType, Collection<Integer> offlineRecordNumbers);

    int getDeletedRecordsCount(BaseType baseType, int dfId);

    Collection<String> getDeletedRecordNames(BaseType baseType, int dfId);
}