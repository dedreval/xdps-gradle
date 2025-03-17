package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.services.WREvent;
import com.wiley.cms.process.IModelController;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/15/2016
 */
public interface IPublishStorage extends IModelController {

    void updatePublishForWaiting(int publishId, boolean wait);

    void resetParentPublish(int publishId);

    void deletePublishes(Collection<Integer> ids, Collection<Integer> publishWaitIds);

    /**
     * Get an initial entire publish entity to work with
     * @param db
     * @param publishType
     * @return an existing publish entity or a new template
     */
    PublishEntity takeEntirePublishByDbAndType(DatabaseEntity db, Integer publishType);

    PublishEntity takeEntirePublishByDbAndType(String db, Integer publishType);

    PublishEntity takePublishByDbAndType(int dbId, Integer publishType);

    PublishEntity takeLatestSentPublishByDbAndPubTypes(String dbName, List<Integer> pubTypeIds);

    List<PublishEntity> findPublishesByDbAndType(Integer dbId, Integer type);

    List<PublishEntity> findPublishesByDbName(String dbName);

    List<Integer> findPublishesByFileName(int dbType, String fileName);

    PublishEntity createPublish(int dbId, Integer publishType, Integer parentId);

    PublishEntity createPublish(int dbId, Integer publishType, PublishEntity template, int hwFrequency,
                                boolean onGenerating);

    PublishEntity createPublishEntire(String dbName, String type, PublishEntity template, int hwFrequency,
                                      boolean onGenerating);

    int markForWhenReadyPublish(int recordNumber, int pubNumber, Date epochDate, Integer publishType,
                                Collection<Integer> dbIds);

    int markForWhenReadyPublish(int recordNumber, int pubNumber, Date epochDate, String deliveryId,
                                Collection<Integer> publishTypes, Collection<Integer> dbIds);

    int markForWhenReadyPublish(int publishId);

    RecordEntity handleWhenReadyEvent(WREvent event, PublishDestination dest, Date date, Collection<Integer> dbIds,
         boolean registered, Map<Integer, List<PublishedAbstractEntity>> results) throws CmsJTException;

    List<PublishedAbstractEntity> getWhenReadyByIds(Collection<Integer> ids);

    List<PublishedAbstractEntity> getWhenReady(int clDbId);

    List<PublishedAbstractEntity> getWhenReadyUnpublished(Collection<Integer> clDbIds);

    List<PublishRecordEntity> findWhenReadyMarkedForPublish(Collection<Integer> clDbIds);

    List<PublishRecordEntity> getPublishRecords(Collection<Integer> publishIds);

    List<PublishRecordEntity> getPublishRecordsForFlow(BaseType bt, Collection<Integer> pubTypesIds);

    List<PublishRecordEntity> getPublishRecords(int recordNumber, int dfId, Collection<Integer> pubTypesIds);

    List<PublishEntity> getRelatedPublishList(Integer parentId, Integer publishId);

    int createPublishRecord(int recordNumber, int pub, Integer dfId, int publishId, Integer recordId);

    List<PublishRecordEntity> findPublishRecords(int minRecordNumber, int maxRecordNumber, int start, int limit);

    List<PublishRecordEntity> findSentPublishRecords(int minRecordNumber, int maxRecordNumber,
        List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPublishId);

    List<PublishRecordEntity> findSentPublishRecords(int minRecordNumber, int maxRecordNumber,
                                                     Collection<Integer> pubTypesIds, Integer skipPublishId);

    List<PublishRecordEntity> findSentPublishRecords(Collection<Integer> recordNumbers, Integer dbId,
                                                     Collection<Integer> pubTypesIds);

    List<Object[]> findSentPublishRecordAndPubNumbers(int minRecordNumber, int maxRecordNumber,
        List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPublishId);

    List<Integer> findSentPublishRecordNumbers(int minRecordNumber, int maxRecordNumber,
        List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPublishId);

    List<PublishRecordEntity> setPublishRecordsFailed(int recordNumber, int pub, Collection<Integer> pubTypesIds,
                                                      int baseType, String publishFileName, Date afterDate);

    List<PublishRecordEntity> setPublishRecordsFailed(Collection<Integer> pubTypesIds, int baseType, String fileName,
                                                      Date afterDate);

    Collection<String> findPublishCdNumbers(Integer publishId);

    Collection<String> findPublishCdAndPubNumbers(Integer publishId);

    /**
     * Returns cdNumbers & pubNumbers mapped to some related record's properties: new doi, spd, publication canceled
     * @param publishId   The publishing package sent
     * @param dbId        The Issue database Identifier
     * @return   cdNumber.pubNumber -> null | [<TRUE: new doi | FALSE>, <null | FALSE:spd, TRUE:publication cancelled>]
     */
    Map<String, Boolean[]> findPublishCdAndPubNumbers(Integer publishId, Integer dbId);

    List<PublishRecordEntity> findPublishByRecAndPubAndDfId(int recordNumber, int pub, int dfId);

    void acceptHWDeliveryForCentral(ClDbEntity db, List<PublishRecordEntity> publishRecords,
        String responseDeliveryId, Date responseDate, boolean responseFullGroup, Date handledDate, OpStats statsByDf);

    void acceptLiteratumDeliveryByPubNumber(int pubNumber, List<PublishRecordEntity> publishRecords,
        Date responseDate, Date handledDate, OpStats statsByDf);


    void createPublishWait(int publishId, boolean disabledStaticContent);

    List<Integer> findPublishWait(String fileName);

    List<Integer> findPublishWait();

    List<PublishRecordEntity> findSentPublishRecordsAfterDate(String dbName,
                                                              List<Integer> pubTypeIds,
                                                              Date date,
                                                              int offset,
                                                              int limit);

    PublishedAbstractEntity updateWhenReadyOnPublished(Integer id, Integer ackId, boolean setNotified);
    void addDeliveryNotificationFileName(Integer id, String fileName);

    List<PublishedAbstractEntity> updateWhenReadyOnPublished(Map<Integer, Integer> ids, int notified,
                                                             boolean setNotified);

    PublishedAbstractEntity updateWhenReadyOnPublished(Integer paeId, Integer askId, int notified,
                                                             boolean setNotified);

    PublishedAbstractEntity findWhenReadyByManuscriptNumber(String manuscriptNumber, Integer ackId,
                                                            Collection<Integer> dbIds);
    PublishedAbstractEntity findWhenReadyByAcknowledgementId(Integer recordNumber, Integer ackId);

    PublishedAbstractEntity findWhenReadyByCochraneNotification(String notificationFileName);

    PublishedAbstractEntity findArticleByDbAndName(int dbId, String recordName);
}
