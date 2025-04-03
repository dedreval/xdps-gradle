package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishTypeEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.entity.PublishWaitEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.ModelController;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.cochrane.services.PublishDate;
import com.wiley.cms.cochrane.services.WREvent;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.NoResultException;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/15/2016
 */
@Stateless
@Local(IPublishStorage.class)
public class PublishStorage extends ModelController implements IPublishStorage {

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PublishEntity takePublishByDbAndType(int dbId, Integer type) {
        ClDbEntity db = find(ClDbEntity.class, dbId);
        List<PublishEntity> list = findPublishesByDbAndType(db.getId(), type);
        return list.isEmpty() ? new PublishEntity(db, type) : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PublishEntity takeLatestSentPublishByDbAndPubTypes(String dbName, List<Integer> pubTypeIds) {
        List<PublishEntity> entity = PublishEntity.queryLatestSentPublishEntityByDbAndPubTypes(
                dbName, pubTypeIds, getManager()).setMaxResults(1).getResultList();
        return entity.isEmpty() ? null : entity.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PublishEntity takeEntirePublishByDbAndType(String dbName, Integer type) {
        return takeEntirePublishByDbAndType((DatabaseEntity) DatabaseEntity.queryDatabase(
                dbName, getManager()).getSingleResult(), type);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PublishEntity takeEntirePublishByDbAndType(DatabaseEntity db, Integer pubType) {
        List<PublishEntity> list = (List<PublishEntity>) PublishEntity.queryPublishEntireEntity(db, pubType,
                getManager()).getResultList();
        return list.isEmpty() ? createPublishEntire(db, pubType) : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishEntity> findPublishesByDbAndType(Integer dbId, Integer pubType) {
        return PublishEntity.queryPublishEntityByDb(dbId, pubType, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishEntity> findPublishesByDbName(String dbName) {
        return (List<PublishEntity>) PublishEntity.queryPublishEntity(dbName, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Integer> findPublishesByFileName(int dbType, String fileName) {
        List<PublishEntity> list = fileName == null ? Collections.emptyList()
                : PublishEntity.queryPublishEntity(dbType, fileName, getManager()).getResultList();
        return list.isEmpty() ? Collections.emptyList() : Collections.singletonList(list.get(0).getId());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishEntity> getRelatedPublishList(Integer parentId, Integer publishId) {
        return PublishEntity.queryPublishEntity(parentId, publishId, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findSentPublishRecords(int minNumber, int maxNumber,
        Collection<Integer> pubTypeIds, Integer skipPubId) {
        return PublishRecordEntity.querySent(minNumber, maxNumber, pubTypeIds, skipPubId, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findSentPublishRecords(Collection<Integer> recordNumbers, Integer dbId,
                                                            Collection<Integer> pubTypesIds) {
        return PublishRecordEntity.querySentByDb(recordNumbers, dbId, pubTypesIds, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findSentPublishRecords(int minNumber, int maxNumber,
            List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPubId) {
        List<PublishRecordEntity> ret;
        if (DbUtils.isOneCommit(recordNumbers.size())) {
            ret = (List<PublishRecordEntity>) PublishRecordEntity.querySent(minNumber, maxNumber, recordNumbers,
                    pubTypesIds, skipPubId, getManager()).getResultList();
        } else {
            ret = new ArrayList<>();
            DbUtils.commitListByIds(recordNumbers, (list) -> ret.addAll(PublishRecordEntity.querySent(
                    minNumber, maxNumber, list, pubTypesIds, skipPubId, getManager()).getResultList()));
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findPublishRecords(int minNumber, int maxNumber, int start, int limit) {
        return PublishRecordEntity.queryAll(minNumber, maxNumber, start, limit, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Object[]> findSentPublishRecordAndPubNumbers(int minRecordNumber, int maxRecordNumber,
            List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPubId) {
        if (recordNumbers == null) {
            return PublishRecordEntity.querySent(minRecordNumber, maxRecordNumber, pubTypesIds, skipPubId,
                    PublishRecordEntity.QUERY_SELECT_SENT_NUMBER_PUBS_BETWEEN_NUMBERS, getManager()).getResultList();
        }
        List<Object[]> ret;
        if (DbUtils.isOneCommit(recordNumbers.size())) {
            ret = PublishRecordEntity.querySent(minRecordNumber, maxRecordNumber, recordNumbers, pubTypesIds,
                skipPubId, PublishRecordEntity.QUERY_SELECT_SENT_NUMBER_PUBS_BY_NUMBERS, getManager()).getResultList();
        } else {
            ret = new ArrayList<>();
            DbUtils.commitListByIds(recordNumbers, (list) -> ret.addAll(PublishRecordEntity.querySent(
                minRecordNumber, maxRecordNumber, list, pubTypesIds, skipPubId,
                    PublishRecordEntity.QUERY_SELECT_SENT_NUMBER_PUBS_BY_NUMBERS, getManager()).getResultList()));
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Integer> findSentPublishRecordNumbers(int minRecordNumber, int maxRecordNumber,
            List<Integer> recordNumbers, Collection<Integer> pubTypesIds, Integer skipPubId) {
        if (recordNumbers == null) {
            return PublishRecordEntity.querySent(minRecordNumber, maxRecordNumber, pubTypesIds, skipPubId,
                    PublishRecordEntity.QUERY_SELECT_SENT_NUMBERS_BETWEEN_NUMBERS, getManager()).getResultList();
        }
        List<Integer> ret;
        if (DbUtils.isOneCommit(recordNumbers.size())) {
            ret = PublishRecordEntity.querySent(
                    minRecordNumber, maxRecordNumber, recordNumbers, pubTypesIds, skipPubId,
                        PublishRecordEntity.QUERY_SELECT_SENT_NUMBERS_BY_NUMBERS, getManager()).getResultList();
        } else {
            ret = new ArrayList<>();
            DbUtils.commitListByIds(recordNumbers, (list) -> ret.addAll(PublishRecordEntity.querySent(
                    minRecordNumber, maxRecordNumber, list, pubTypesIds, skipPubId,
                        PublishRecordEntity.QUERY_SELECT_SENT_NUMBERS_BY_NUMBERS, getManager()).getResultList()));
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<PublishRecordEntity> setPublishRecordsFailed(Collection<Integer> pubTypesIds,
                                                             int baseType, String publishFileName, Date afterDate) {
        List<Integer> publishIds = PublishEntity.querySentPublishIdsByDbAndPubTypes(
                pubTypesIds, baseType, publishFileName, afterDate, getManager()).getResultList();
        return publishIds.isEmpty() ? Collections.emptyList() : setPublishRecordsFailed(null,
                getPublishRecords(publishIds));
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<PublishRecordEntity> setPublishRecordsFailed(int recordNumber, int pub, Collection<Integer> pubTypesIds,
                                                             int baseType, String publishFileName, Date afterDate) {
        return setPublishRecordsFailed(publishFileName, PublishRecordEntity.querySentUnpublished(
                recordNumber, pub, pubTypesIds, baseType, afterDate, getManager()).getResultList());
    }

    private List<PublishRecordEntity> setPublishRecordsFailed(String publishFileName, List<PublishRecordEntity> list) {
        Iterator<PublishRecordEntity> it = list.iterator();
        while (it.hasNext())  {
            PublishRecordEntity pre = it.next();
            if (pre.isFailed() || pre.isPublished() || (publishFileName != null && !publishFileName.isEmpty()
                    && !publishFileName.equals(pre.getPublishPacket().getFileName()))) {
                it.remove();
                continue;
            }
            pre.setFailed();
            getManager().merge(pre);
        }
        return list;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Collection<String> findPublishCdNumbers(Integer publishId) {
        return findPublishCdAndPubNumbers(publishId, false, null).keySet();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Collection<String> findPublishCdAndPubNumbers(Integer publishId) {
        return findPublishCdAndPubNumbers(publishId, true, null).keySet();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Map<String, Boolean[]> findPublishCdAndPubNumbers(Integer publishId, Integer dbId) {
        return findPublishCdAndPubNumbers(publishId, true, dbId);
    }

    private Map<String, Boolean[]> findPublishCdAndPubNumbers(Integer publishId, boolean addPubNumber, Integer dbId) {
        List<PublishRecordEntity> entities = getPublishRecords(Collections.singletonList(publishId));
        Map<String, Boolean[]> ret = new HashMap<>();
        boolean toEmulate = dbId != null && addPubNumber;
        Set<String> cdNumbers = toEmulate ? new HashSet<>() : null;
        for (PublishRecordEntity pre: entities) {
            String cdNumber = RecordHelper.buildCdNumber(pre.getNumber());
            if (cdNumbers != null) {
                cdNumbers.add(cdNumber);
            }
            if (addPubNumber) {
                cdNumber = RevmanMetadataHelper.buildPubName(cdNumber, pre.getPubNumber());
            }
            ret.put(cdNumber, null);
        }
        if (!ret.isEmpty() && dbId != null) {
            boolean spd = CmsUtils.isScheduledDb(entities.get(0).getPublishPacket().getDb().getId());
            List<RecordEntity> list = RecordEntity.queryRecords(dbId, cdNumbers != null ? cdNumbers : ret.keySet(),
                    getManager()).getResultList();
            for (RecordEntity re: list) {
                RecordMetadataEntity rme = re.getMetadata();
                Boolean[] pubOpt =
                    {Boolean.TRUE, spd ? Boolean.FALSE : (re.isPublishingCancelled() ? Boolean.TRUE : null)};
                if (rme != null) {
                    ret.put(addPubNumber ? rme.getPublisherId() : re.getName(), pubOpt);
                    pubOpt[0] = rme.getVersion().isNewDoi();
                } else {
                    ret.put(addPubNumber ? findPubName(re.getName(), ret.keySet()) : re.getName(), pubOpt);
                }
            }
        } else if (toEmulate) {
            LOG.debug(String.format("nothing found to emulate by %d publish records [%d]", entities.size(), publishId));
        }
        return ret;
    }

    private String findPubName(String cdNumber, Set<String> set) {
        Optional<String> ret = set.stream().filter(r -> r.startsWith(cdNumber)).findFirst();
        return ret.isPresent() ? ret.get() : cdNumber;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findPublishByRecAndPubAndDfId(int recordNumber, int pub, int dfId) {
        return PublishRecordEntity.queryPublishByRecordAndPubAndDfId(
                recordNumber, pub, dfId, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Integer> findPublishWait(String fileName) {
        return fileName == null ? Collections.emptyList() : PublishWaitEntity.queryWaitForPublishIds(
                fileName, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Integer> findPublishWait() {
        return PublishWaitEntity.queryWaitForPublishIds(getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findSentPublishRecordsAfterDate(String dbName, List<Integer> pubTypeIds,
                                                                     Date date, int offset, int limit) {
        return PublishRecordEntity.querySentAfterDate(dbName, pubTypeIds, date, offset, limit, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void acceptHWDeliveryForCentral(ClDbEntity db, List<PublishRecordEntity> publishRecords,
                                           String responseDeliveryId, Date responseDate, boolean responseFullGroup,
                                           Date handledDate, OpStats statsByDf) {
        if (acceptLiteratumDelivery(db, Constants.FIRST_PUB, publishRecords, responseDate, handledDate,
                responseFullGroup, statsByDf)) {
            LOG.debug("CENTRAL CONTENT_ONLINE 'fullGroup'=%s, 'deliveryId'='%s' marks some DOIs failed by "
                    + "LOAD_TO_PUBLISH previously as successful", responseFullGroup, responseDeliveryId);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void acceptLiteratumDeliveryByPubNumber(int pubNumber, List<PublishRecordEntity> publishRecords,
            Date responseDate, Date handledDate, OpStats statsByDf) {
        acceptLiteratumDelivery(null, pubNumber, publishRecords, responseDate, handledDate, true, statsByDf);
    }

    private boolean acceptLiteratumDelivery(ClDbEntity db, int pub, Iterable<PublishRecordEntity> list,
            Date responseDate, Date handledDate, boolean overrideFailure, OpStats statsByDf) {
        Set<String> names = db != null && !db.isEntire() ? new HashSet<>() : null;
        boolean wasFailedPreviously = false;
        for (PublishRecordEntity pre: list) {
            if ((pre.getPubNumber() == pub || (pre.getPubNumber() == 0 && pub == Constants.FIRST_PUB))
                    && (!pre.isFailed() || overrideFailure)) {
                pre.setHandledDate(handledDate);
                pre.setDate(responseDate);
                pre.setPublished();
                getManager().merge(pre);
                if (names != null) {
                    names.add(RecordHelper.buildCdNumber(pre.getNumber()));
                }
                statsByDf.addTotalCompletedByKey(pre.getDeliveryId(), pre.getNumber());
                if (!wasFailedPreviously && pre.isFailed()) {
                    wasFailedPreviously = true;
                }
            }
        }
        if (names != null && !names.isEmpty()) {
            RecordEntity.setRecordsStateByDb(RecordEntity.STATE_DS_PUBLISHING, db.getId(), names, -1, getManager());
            LOG.debug(String.format("%s records were set as DS publishing ready", names.size()));
        }
        return wasFailedPreviously;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity createPublish(int dbId, Integer publishType, Integer publishId) {
        PublishEntity parent = find(PublishEntity.class, publishId);
        if (!parent.hasRelation()) {
            parent.setParentId(publishId);
        }
        ClDbEntity clDb = find(ClDbEntity.class, dbId);
        PublishEntity ret = new PublishEntity(clDb, publishType);
        ret.setGenerating(true);
        ret.setParentId(publishId);
        ret.setHwFrequency(parent.getHwFrequency());

        getManager().persist(ret);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createPublishWait(int publishId, boolean disabledStaticContent) {
        PublishEntity pe = find(PublishEntity.class, publishId);
        PublishWaitEntity pwe = new PublishWaitEntity();
        pwe.setPublish(pe);
        pwe.setFileName(pe.getFileName());
        pwe.setStaticContentDisabled(disabledStaticContent);
        getManager().persist(pwe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity createPublish(int dbId, Integer publishType, PublishEntity template, int hwFrequency,
                                       boolean generating) {
        ClDbEntity clDb = find(ClDbEntity.class, dbId);
        PublishEntity ret = new PublishEntity(clDb, publishType);
        persistPublish(ret, template, hwFrequency, generating);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity createPublishEntire(String dbName, String type, PublishEntity template, int hwFrequency,
                                             boolean generating) {
        DatabaseEntity db = (DatabaseEntity) DatabaseEntity.queryDatabase(dbName, getManager()).getSingleResult();
        PublishEntity ret = createPublishEntire(db, PublishTypeEntity.getNamedEntityId(type));
        persistPublish(ret, template, hwFrequency, generating);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updatePublishForWaiting(int publishId, boolean waiting) {
        PublishEntity publish = find(PublishEntity.class, publishId);
        if (publish == null) {
            return;
        }
        publish.setWaiting(waiting);
        flush(publish);
        if (!waiting && publish.hasRelation()) {
            List<PublishEntity> related = getRelatedPublishList(publish.getParentId(), publishId);
            for (PublishEntity pe: related) {
                if (pe.isWaiting()) {
                    pe.setWaiting(false);
                    flush(pe);
                }
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int markForWhenReadyPublish(int recordNumber, int pubNumber, Date epochDate, Integer ptype,
                                       Collection<Integer> dbIds) {
        return PublishRecordEntity.querySetEpochDate(
                recordNumber, pubNumber, epochDate, ptype, dbIds, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int markForWhenReadyPublish(int recordNumber, int pubNumber, Date epochDate, String deliveryId,
                                       Collection<Integer> publishTypes, Collection<Integer> dbIds) {
        return PublishRecordEntity.querySetPubEventDate(
                recordNumber, pubNumber, epochDate, deliveryId, publishTypes, dbIds, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int markForWhenReadyPublish(int publishId) {
        Calendar cl = CmsUtils.getArchieDownloaderCalendar();
        int ret = PublishRecordEntity.querySetAbstractsDate(publishId, cl.getTime(), em).executeUpdate();
        PublishEntity entity = find(PublishEntity.class, publishId);
        if (entity.hasRelation()) {
            List<PublishEntity> related = getRelatedPublishList(entity.getParentId(), publishId);
            for (PublishEntity pe: related) {
                ret += PublishRecordEntity.querySetAbstractsDate(pe.getId(), cl.getTime(), em).executeUpdate();
            }
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> getPublishRecords(Collection<Integer> publishIds) {
        return PublishRecordEntity.queryAll(publishIds, 0, 0, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> getPublishRecordsForFlow(BaseType bt, Collection<Integer> publishIds) {
        List<PublishRecordEntity> list = getPublishRecords(publishIds);
        if (!bt.isCDSR() || list.size() < 2) {
            return list;
        }
        List<PublishRecordEntity> ret = new ArrayList<>();
        PublishRecordEntity prev = null;
        for (PublishRecordEntity pre: list) {
            if (prev == null || (prev.getNumber() == pre.getNumber() && pre.getPubNumber() > prev.getPubNumber())) {
                prev = pre;

            } else if (prev.getNumber() != pre.getNumber()) {
                ret.add(prev);
                prev = pre;
            }
        }
        ret.add(prev);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> getPublishRecords(int recordNumber, int dfId, Collection<Integer> pubTypesIds) {
        return PublishRecordEntity.queryPublishedRecordsByDf(recordNumber, dfId, pubTypesIds, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishRecordEntity> findWhenReadyMarkedForPublish(Collection<Integer> dbIds) {
        return PublishRecordEntity.queryReadyToPublish(dbIds, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public RecordEntity handleWhenReadyEvent(WREvent event, PublishDestination dest, Date date,
        Collection<Integer> dbIds, boolean registered, Map<Integer, List<PublishedAbstractEntity>> results)
            throws CmsJTException {
        String cdNumber = event.getRecordName();
        if (cdNumber != null) {
            return handleWhenReadyEventForDoi(event, dest, date,
                    findUnhandledPublishRecords(event, dbIds, registered), results);
        }
        List<PublishRecordEntity> nowPublished = findWhenReadyMarkedForPublish(dbIds);
        if (!nowPublished.isEmpty()) {
            handleWhenReadyEvent(nowPublished, event, dest, date, dbIds, results);
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishedAbstractEntity> getWhenReadyByIds(Collection<Integer> ids) {
        return PublishedAbstractEntity.queryAbstractsByIds(ids, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishedAbstractEntity> getWhenReady(int dbId) {
        return PublishedAbstractEntity.queryAbstractsByDb(dbId, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishedAbstractEntity> getWhenReadyUnpublished(Collection<Integer> dbIds) {
        return PublishedAbstractEntity.queryUnpublishedAbstracts(dbIds, em).getResultList();
    }

    private List<PublishRecordEntity> findUnhandledPublishRecords(WREvent event, Collection<Integer> dbIds,
                                                                  boolean registered) {
        return registered ? PublishRecordEntity.queryReadyToPublish(event.getRecordNumber(), event.getPubNumber(),
                dbIds, event.getDest().getOnPubEventTypeIds(event.getBaseType().isCDSR()), em).getResultList()
                : PublishRecordEntity.queryUnhandled(event.getRecordNumber(), event.getPubNumber(),
                event.getDest().getOnPubEventTypeIds(event.getBaseType().isCDSR()), em).getResultList();
    }

    private void handleWhenReadyEvent(List<PublishRecordEntity> nowPublished, WREvent event, PublishDestination dest,
            Date date, Collection<Integer> dbIds, Map<Integer, List<PublishedAbstractEntity>> results) {
        List<PublishedAbstractEntity> unpublished =
                PublishedAbstractEntity.queryUnpublishedAbstracts(dbIds, em).getResultList();
        if (unpublished.isEmpty()) {
            setHandledDate(nowPublished, event, date);
            return;
        }
        Map<String, List<PublishRecordEntity>> nowPublishedByDoi = new HashMap<>();
        for (PublishRecordEntity pre: nowPublished) {
            String doi = pre.getPubName();
            List<PublishRecordEntity> list = nowPublishedByDoi.computeIfAbsent(doi, f -> new ArrayList<>());
            list.add(pre);
        }
        Map<String, List<PublishedAbstractEntity>> unpublishedByDoi = new HashMap<>();
        for (PublishedAbstractEntity pre: unpublished) {
            String doi = pre.getPubName();
            List<PublishedAbstractEntity> list = unpublishedByDoi.computeIfAbsent(doi, f -> new ArrayList<>());
            list.add(pre);
        }
        nowPublishedByDoi.forEach((doi, list) -> {
                if (unpublishedByDoi.containsKey(doi)) {
                    handleEpochEventForDoi(unpublishedByDoi.get(doi), list, dest, date, event.getEventDate(), results);
                } else {
                    setHandledDate(list, event, date);
                }
            });
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createPublishRecord(int recordNumber, int pub, Integer dfId, int publishId, Integer recordId) {
        PublishEntity pe = find(PublishEntity.class, publishId);
        if (pe == null) {
            return DbEntity.NOT_EXIST_ID;
        }
        PublishRecordEntity pre = new PublishRecordEntity(recordNumber, pub, dfId, pe);
        getManager().persist(pre);
        return pre.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deletePublishes(Collection<Integer> ids, Collection<Integer> publishWaitIds) {
        if (publishWaitIds != null && !publishWaitIds.isEmpty()) {
            PublishWaitEntity.deleteWaitForPublish(publishWaitIds, getManager()).executeUpdate();
        }
        if (ids != null && !ids.isEmpty()) {
            PublishEntity.queryDeletePublish(ids, getManager()).executeUpdate();
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void resetParentPublish(int publishId) {
        PublishEntity pe = find(PublishEntity.class, publishId);
        if (pe != null) {
            pe.setParentId(DbEntity.NOT_EXIST_ID);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void addDeliveryNotificationFileName(Integer id, String fileName) {
        PublishedAbstractEntity pae = find(PublishedAbstractEntity.class, id);
        if (pae != null){
            pae.setCochraneNotificationFileName(fileName);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishedAbstractEntity updateWhenReadyOnPublished(Integer id, Integer ackId, boolean setNotified) {
        PublishedAbstractEntity pae = find(PublishedAbstractEntity.class, id);
        if (pae != null) {
            if (ackId != null) {
                pae.setAcknowledgementId(ackId);
            }
            if (setNotified && !PublishedAbstractEntity.isNotifiedOnPublished(pae.getNotified())) {
                pae.setNotified(pae.getNotified() + PublishedAbstractEntity.PUB_NOTIFIED);
            }
        }
        return pae;
    }

    public PublishedAbstractEntity findWhenReadyByManuscriptNumber(String manuscriptNumber, Integer ackId,
                                                                   Collection<Integer> dbIds) {
        List<String> manuscriptNumbers =  new ArrayList<>();
        manuscriptNumbers.add(manuscriptNumber);
        List<PublishedAbstractEntity> list = PublishedAbstractEntity.queryAbstractsNoAcknowledgement(
                dbIds, manuscriptNumbers, getManager()).getResultList();
        PublishedAbstractEntity ret = null;
        for (PublishedAbstractEntity pae: list) {
            if (!pae.getManuscriptNumber().equals(manuscriptNumber)) {
                continue;
            }
            boolean exists = DbUtils.exists(pae.getAcknowledgementId());
            if (exists && pae.getAcknowledgementId().equals(ackId)) {
                ret = pae;
                break;
            } else if (!exists) {
                ret = pae;
            }
        }
        return ret;
    }

    public PublishedAbstractEntity findWhenReadyByAcknowledgementId(Integer recordNumber, Integer ackId) {
        List<PublishedAbstractEntity> list = PublishedAbstractEntity.queryPublishedAbstracts(
                recordNumber, getManager()).getResultList();
        for (PublishedAbstractEntity pae: list) {
            if (ackId.equals(pae.getAcknowledgementId())) {
                return pae;
            }
        }
        return null;
    }


    public PublishedAbstractEntity findWhenReadyByCochraneNotification(String notificationFileName) {
        return (PublishedAbstractEntity) PublishedAbstractEntity
                .queryAbstractsByCochraneNotification(notificationFileName, getManager()).getSingleResult();
    }

    public PublishedAbstractEntity findArticleByDbAndName(int dbId, String recordName) {
        return (PublishedAbstractEntity) PublishedAbstractEntity.queryAbstractByDbAndName(
                dbId, recordName, getManager()).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<PublishedAbstractEntity> updateWhenReadyOnPublished(Map<Integer, Integer> ids, int notified,
                                                                    boolean setNotified) {
        List<PublishedAbstractEntity> list = getWhenReadyByIds(ids.keySet());
        for (PublishedAbstractEntity pae: list) {
            boolean merge = false;
            if (setNotified) {
                int updatedNotified = notified;
                if (PublishDestination.SEMANTICO.ordinal() == notified) {
                    updatedNotified = PublishedAbstractEntity.PUB_NOTIFIED + notified;
                }
                pae.setNotified(updatedNotified);
                merge = true;
            }
            Integer ackId = ids.get(pae.getId());
            if (ackId != null) {
                pae.setAcknowledgementId(ackId);
                merge = true;
            }
            if (merge) {
                getManager().merge(pae);
            }
        }
        return list;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishedAbstractEntity updateWhenReadyOnPublished(Integer paeId, Integer ackId, int notified,
                                                              boolean setNotified) {
        PublishedAbstractEntity pae = find(PublishedAbstractEntity.class, paeId);

        boolean merge = false;
        if (setNotified) {
            int updatedNotified = notified;
            if (PublishDestination.SEMANTICO.ordinal() == notified) {
                updatedNotified = PublishedAbstractEntity.PUB_NOTIFIED + notified;
            }
            if (PublishedAbstractEntity.DUPLICATED_HW_RECEIVING == notified
                    && PublishedAbstractEntity.DUPLICATED_HW_RECEIVING == pae.getNotified()) {
                updatedNotified = PublishedAbstractEntity.PUB_NOTIFIED + PublishDestination.LITERATUM_HW.ordinal();
            }
            pae.setNotified(updatedNotified);
            merge = true;
        }

        if (ackId != null) {
            pae.setAcknowledgementId(ackId);
            merge = true;
        }
        if (merge) {
            getManager().merge(pae);
        }
        return pae;
    }

    private List<PublishRecordEntity> setHandledDate(List<PublishRecordEntity> published, WREvent event, Date date) {
        String dfId = event.getDeliveryId();
        List<PublishRecordEntity> ret =
                event.getDest() == PublishDestination.SEMANTICO ? Collections.emptyList() : null;
        for (PublishRecordEntity pre: published) {
            if (pre.getHandledDate() != null || (dfId != null && !dfId.equals(pre.getPublishPacket().getFileName()))) {
                continue;
            }
            pre.setHandledDate(date);
            pre.setPublished();
            pre.setDate(event.getEventDate());
            getManager().merge(pre);
            if (ret != null && dfId != null) {
                if (ret.isEmpty()) {
                    ret = new ArrayList<>();
                }
                ret.add(pre);
            }
        }
        return ret;
    }

    private RecordEntity handleWhenReadyEventForDoi(WREvent event, PublishDestination dest, Date date,
            @NotNull List<PublishRecordEntity> registered,
            Map<Integer, List<PublishedAbstractEntity>> results) throws CmsJTException {
        if (!registered.isEmpty()) {
            List<PublishedAbstractEntity> unpublished = PublishedAbstractEntity.queryUnpublishedAbstracts(
                    event.getRecordNumber(), event.getPubNumber(), getManager()).getResultList();

            List<PublishedAbstractEntity> withoutDuplicate = new ArrayList<>(Collections.emptyList());
            if (!unpublished.isEmpty()) {
                unpublished.forEach(pae -> getDuplicateEntity(withoutDuplicate, pae));
            }
            unpublished = withoutDuplicate.stream()
                    .filter(this::isNotifiedInRange).collect(Collectors.toList());
            return unpublished.isEmpty() && event.getBaseType().isCCA()  // to support legacy flow
                    ? handleEventForDoi(setHandledDate(registered, event, date), event)
                    : handleWhenReadyEventForDoi(unpublished, registered, dest, date, event, results);
        }
        return null;
    }

    private void getDuplicateEntity(List<PublishedAbstractEntity> withoutDuplicate, PublishedAbstractEntity pae) {
        List<PublishedAbstractEntity> entities;
        entities = pae.hasLanguage() ? getDuplicateTranslation(pae) : getDuplicateReview(pae);
        boolean hasPublishedReviews = entities.stream()
                .anyMatch(pe -> pe.getNotified() == 16 && pe.getPublishedDate() != null);

        if (!hasPublishedReviews) {
            Optional<PublishedAbstractEntity> latestElement = entities.stream()
                    .max(Comparator.comparing(PublishedAbstractEntity::getDate));
            latestElement.ifPresent(withoutDuplicate::add);
        }
    }

    private List<PublishedAbstractEntity> getDuplicateTranslation(PublishedAbstractEntity pae) {
        return PublishedAbstractEntity.queryDuplicatesAbstract(
                pae.getNumber(), pae.getPubNumber(), pae.getVersion(), pae.getLanguage(),
                pae.getSID(), getManager()).getResultList();
    }

    private List<PublishedAbstractEntity> getDuplicateReview(PublishedAbstractEntity pae) {
        return PublishedAbstractEntity.queryDuplicatesAbstract(
                pae.getNumber(), pae.getPubNumber(), pae.getVersion(), getManager()).getResultList();
    }

    private boolean isNotifiedInRange(PublishedAbstractEntity entity) {
        int notified = entity.getNotified();
        return notified < PublishedAbstractEntity.PUB_NOTIFIED || (notified >= 1000 && notified <= 1004);
    }

    private RecordEntity handleEventForDoi(List<PublishRecordEntity> published, WREvent event) throws CmsJTException {
        if (published != null) {
            for (PublishRecordEntity pre: published) {
                if (pre.getPublishPacket().getFileName().equals(event.getDeliveryId())) {
                    return updatePublishDates4Metadata(pre, event);
                }
            }
        }
        return null;
    }

    private void handleEpochEventForDoi(List<PublishedAbstractEntity> unpublished, List<PublishRecordEntity> published,
            PublishDestination dest, Date date, Date eventDate, Map<Integer, List<PublishedAbstractEntity>> results) {
        for (PublishedAbstractEntity pae: unpublished) {
            int initialNotified = pae.getNotified();
            int notified = initialNotified;
            for (PublishRecordEntity pre: published) {
                int resultNotified = dest.checkNotified(notified, pre.getPublishPacket().getPublishType());
                if (resultNotified > 0) {
                    notified = setPublishEventHandlingDate(pre, eventDate, date, resultNotified);
                }
            }
            if (initialNotified < notified) {
                pae.setNotified(PublishedAbstractEntity.getPubNotified(notified));
                pae.setPublishedDate(eventDate);
                getManager().merge(pae);
                List<PublishedAbstractEntity> list = results.computeIfAbsent(notified, f -> new ArrayList<>());
                list.add(pae);
            }
        }
    }

    private RecordEntity handleWhenReadyEventForDoi(List<PublishedAbstractEntity> unpublished,
            List<PublishRecordEntity> publishRecords, PublishDestination dest, Date date, WREvent event,
            Map<Integer, List<PublishedAbstractEntity>> results) throws CmsJTException {
        RecordEntity ret = null;
        for (PublishedAbstractEntity pae: unpublished) {
            int initialNotified = pae.getNotified() % PublishedAbstractEntity.CANCEL_NOTIFIED;
            pae.setNotified(initialNotified);
            getManager().merge(pae);
            int notified = initialNotified;
            Iterator<PublishRecordEntity> it = publishRecords.iterator();
            while (it.hasNext()) {
                PublishRecordEntity pre = it.next();
                boolean sameDelivery = pre.getPublishPacket().getFileName().equals(event.getDeliveryId());
                if (!isPublishRecordMatchToEvent(pae.getDate(), pre, event.getEventDate(), event.getDeliveryId(),
                        sameDelivery)) {
                    it.remove(); // not our event
                    LOG.debug(String.format(
                        "event %s was omitted: startDate=%s eventDate=%s generationDate=%s sendingDate=%s same=%s",
                            event, pae.getDate(), event.getEventDate(), pre.getPublishPacket().getGenerationDate(),
                                    pre.getPublishPacket().getSendingDate(), sameDelivery));
                    continue;
                }
                int resultNotified = dest.checkNotified(notified, pre.getPublishPacket().getPublishType());
                if (resultNotified > 0) {
                    notified = setPublishEventHandlingDate(pre, event.getEventDate(), date, resultNotified);
                } else if (sameDelivery) {
                    resultNotified = dest.checkNotified(notified, event.getDest().getWhenReadyTypeId());
                    if (resultNotified > 0) {
                        notified = setPublishEventHandlingDate(pre, event.getEventDate(), date, resultNotified);
                    }
                }
            }
            if (initialNotified < notified) {
                pae.setNotified(PublishedAbstractEntity.getPubNotified(notified));
                pae.setPublishedDate(event.getEventDate());
                getManager().merge(pae);
                results.computeIfAbsent(notified, f -> new ArrayList<>()).add(pae);
                if (event.getDest() == PublishDestination.SEMANTICO) {
                    ret = ret != null || pae.hasLanguage() ? ret : updatePublishDates4Metadata(pae, event);
                }
            }
        }
        return ret;
    }

    private boolean isPublishRecordMatchToEvent(Date startDate, PublishRecordEntity pre, Date eventDate,
                                                String deliveryId, boolean sameDelivery) throws CmsJTException {
        PublishEntity pe = pre.getPublishPacket();
        Date genDate = pe.getGenerationDate();
        return genDate != null && eventDate.after(genDate) && startDate.before(genDate) && (sameDelivery
                || deliveryId == null || (pe.getSendingDate() != null && !eventDate.before(pe.getSendingDate())));
    }

    private RecordEntity updatePublishDates4Metadata(PublishedAbstractEntity pae, WREvent event) throws CmsJTException {
        RecordEntity re = getManager().find(RecordEntity.class, pae.getRecordId());
        return re != null && checkPublishDates(re, re.getMetadata(), pae, pae.getDate(), event) != null ? re : null;
    }

    private RecordEntity updatePublishDates4Metadata(PublishRecordEntity pre, WREvent event) throws CmsJTException {
        List<RecordEntity> list = RecordEntity.queryRecords(pre.getPublishPacket().getDb().getId(),
                event.getRecordName(), getManager()).getResultList();
        if (!list.isEmpty()) {
            RecordEntity re = list.get(0);
            return re.getMetadata() != null && checkPublishDates(re,
                    re.getMetadata(), null, re.getDeliveryFile().getDate(), event) != null ? re : null;
        }
        return null;
    }

    private ICDSRMeta checkPublishDates(RecordEntity re, RecordMetadataEntity rme, PublishedAbstractEntity pae,
                                        Date startDate, WREvent event) throws CmsJTException {
        ICDSRMeta ret = null;
        boolean spd = rme.isScheduled();
        if (event.isContentOffline()) {
            if (!spd || pae == null || !pae.sPD().off()) {
                throw new CmsJTException(String.format("%s is not expected to be cancelled with %s",
                        rme.getPublisherId(), CochraneCMSPropertyNames.getLiteratumEventOfflineFilter()));
            }
            pae.sPD(false);
            getManager().merge(pae);
            ret = rme;
        } else if (rme.getPublishedOnlineFinalForm() == null || spd) {
            if (pae != null && pae.sPD().off()) {
                throw new CmsJTException(String.format("%s is canceled, %s cannot be applied for canceled SPD articles",
                    rme.getPublisherId(), CochraneCMSPropertyNames.getLiteratumEventOnlineFilter()));
            }
            PublishDate dateOnlineFinalForm = event.getPublishDate(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM);
            PublishDate dateFirstOnline = event.getPublishDate(LiteratumEvent.WRK_EVENT_FIRST_ONLINE);
            PublishDate dateOnlineCitation = event.getPublishDate(LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE);
            ret = setPublishDates(rme, startDate, dateOnlineFinalForm, dateFirstOnline, dateOnlineCitation,
                    event.isFirstPublishedOnline(), event);
            if (pae != null && spd) {
                moveScheduledRecordToActualIssue(event.getBaseType(), rme, pae);
                ret = rme;
            }
        } else {
            LOG.warn("'%s' date of %s was already set, current state: %s",
                    LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, rme, rme.getPublishDatesStr());
        }
        if (event.getBaseType().isEditorial()) {
            DeliveryFileEntity de = re.getDeliveryFile();
            if (!de.isAriesSFTP() && DeliveryPackage.isPreQA(de.isWml3g(), de.getName())) {
                MessageSender.sendPreLifePublishingMessage(event.getBaseType().getId(), de.getName(),
                    event.getDeliveryId(), re.getName(), de.getIssue().getYear(), de.getIssue().getNumber(),
                        event.getPublishDate(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM).get());
                ret = null;
            }
        }
        return ret != null && !ret.isJats() && event.getBaseType().isCDSR() ? null : ret;   
    }

    private void moveScheduledRecordToActualIssue(BaseType bt, RecordMetadataEntity rme,
                                                  PublishedAbstractEntity pae) throws CmsJTException {
        RecordEntity re = find(RecordEntity.class, pae.getRecordId());
        String publisherId = pae.getPubName();
        if (re == null || !CmsUtils.isScheduledIssue(re.getDb().getIssue().getId())) {
            LOG.warn(String.format("cannot find scheduled %s by [%d]", publisherId, pae.getRecordId()));
            return;
        }
        int actualIssue = rme.getPublishedIssue();
        int issueYear = CmsUtils.getYearByIssueNumber(actualIssue);
        int issueNumber = CmsUtils.getIssueByIssueNumber(actualIssue);
        IssueEntity ie;
        List<IssueEntity> issueList = IssueEntity.getIssue(issueYear, issueNumber, em).getResultList();
        if (issueList.isEmpty()) {
            throw new CmsJTException(new ErrorInfo<>(publisherId, ErrorInfo.Type.RECORD_BLOCKED, String.format(
                "the event will be completed later because issue %d is has been not created yet", actualIssue)));
        }
        ie = issueList.get(0);
        List<ClDbEntity> dbList = ClDbEntity.queryClDb(bt.getDbId(), ie.getId(), em).getResultList();
        ClDbEntity clDb = dbList.get(0);
        IRepository rp = RepositoryFactory.getRepository();
        try {
            RecordHelper.copyContentFromIssueToIssue(bt.getId(), Constants.SPD_ISSUE_ID, ie.getId(), re, rp);
            LOG.debug(String.format("scheduled %s is moved to its actual issue %d", publisherId, actualIssue));
        } catch (Exception e) {
            LOG.error(String.format("cannot move scheduled %s to its actual issue %d", publisherId, actualIssue),
                    e.getMessage());
            return;
        }
        RecordHelper.deleteContentFromIssue(bt.getId(), Constants.SPD_ISSUE_ID, re, rp);
        List<RecordEntity> list = RecordEntity.queryRecords(clDb.getId(), re.getName(), em).getResultList();
        RecordEntity prevRecord = list.isEmpty() ? null : list.get(0);
        if (prevRecord != null) {
            em.remove(prevRecord);
            if (!prevRecord.isRenderingCompleted()) {
                clDb.setRenderedCount(clDb.getRenderedCount() + 1);
            }
        } else {
            clDb.setAllCount(clDb.getAllCount() + 1);
            clDb.setRenderedCount(clDb.getRenderedCount() + 1);
        }
        dbList = ClDbEntity.queryClDb(bt.getDbId(), Constants.SPD_ISSUE_ID, em).getResultList();
        ClDbEntity clSpdDb = dbList.get(0);
        clSpdDb.setAllCount(clSpdDb.getAllCount() - 1);
        clSpdDb.setRenderedCount(clSpdDb.getRenderedCount() - 1);
        re.setDb(clDb);
        DeliveryFileEntity de = re.getDeliveryFile();
        de.setIssue(ie);
        de.setDb(clDb);
        pae.setDbId(clDb.getId());
        rme.setIssue(actualIssue);
        em.merge(de);
        em.merge(re);
        em.merge(pae);
        em.merge(clSpdDb);
        em.merge(rme);
    }

    private ICDSRMeta setPublishDates(RecordMetadataEntity rme, Date startDate, PublishDate dateOnlineFinalForm,
        PublishDate dateFirstOnline, PublishDate dateOnlineCitation, boolean firstOnline, WREvent event)
            throws CmsJTException {
        if (dateOnlineFinalForm.isEmpty()) {
            return null;   // publication dates support is disabled
        }
        boolean needToReload = PublishHelper.setOutputPublishDates(
                rme, startDate, dateOnlineFinalForm, dateFirstOnline, dateOnlineCitation, firstOnline, event);
        getManager().merge(rme);
        LOG.debug("publication dates of %s were accepted (reload %s), current state: %s", rme, needToReload,
                rme.getPublishDatesStr());
        return needToReload ? rme : null;
    }

    private int setPublishEventHandlingDate(PublishRecordEntity pre, Date eventDate, Date date, int resultNotified) {
        pre.setDate(eventDate);
        pre.setHandledDate(date);
        getManager().merge(pre);
        return resultNotified;
    }

    private PublishEntity createPublishEntire(DatabaseEntity db, Integer publishType) {
        ClDbEntity clDb;
        try {
            clDb = (ClDbEntity) ClDbEntity.queryClDb(db, getManager()).getSingleResult();
        } catch (NoResultException nre) {
            clDb = ResultStorageFactory.getFactory().getInstance().createDb(
                    null, db, CochraneCMSPropertyNames.getPriority(db.getName()));
        }
        return new PublishEntity(clDb, publishType);
    }

    private void persistPublish(PublishEntity entity, PublishEntity template, int hwFrequency, boolean onGenerating) {
        if (template != null) {
            entity.copyByTemplate(template, onGenerating);
        } else {
            entity.setGenerating(onGenerating);
        }
        entity.setHwFrequency(hwFrequency);
        getManager().persist(entity);
    }
}