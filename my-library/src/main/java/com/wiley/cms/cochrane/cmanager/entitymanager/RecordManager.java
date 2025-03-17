package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DbRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.GroupEntity;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entity.DbRecordEntity;
import com.wiley.cms.cochrane.cmanager.entity.LastRecordEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entity.VersionEntity;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.process.ModelController;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.PublishDate;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.CollectionCommitter;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/25/2018
 */
@Local(IRecordManager.class)
@Stateless
public class RecordManager extends ModelController implements IRecordManager {

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setMetadataImported(Integer metaId, Integer existedMetaId, int issue, int toImport)
            throws CmsJTException {
        RecordMetadataEntity rm = em.find(RecordMetadataEntity.class, metaId);
        if (rm == null || !rm.isJats()) {
            throw new CmsJTException(String.format("no proper metadata found by [%d]", metaId));
        }
        RecordMetadataEntity existed = existedMetaId != null ? em.find(RecordMetadataEntity.class, metaId) : null;
        if (toImport > 0) {
            if (rm.getMetaType() > toImport) {
                throw new CmsJTException(String.format("import of metadata [%d] already was started", metaId));
            }
            rm.setMetaType(1 + toImport);
            rm.setIssue(issue);
            if (existed != null) {
                rm.getVersion().setFutureHistoryNumber(existed.getVersion().getFutureHistoryNumber());
                existed.getVersion().setHistoryNumber(RecordEntity.VERSION_INTERMEDIATE);
                em.merge(existed);
            }
        } else {
            if (rm.getMetaType() < RecordMetadataEntity.IMPORTED + toImport) {
                throw new CmsJTException(String.format("metadata [%d] was not imported to be restored", metaId));
            }
            rm.setMetaType(rm.getMetaType() - 1);
            rm.setIssue(Constants.IMPORT_JATS_ISSUE_NUMBER);
            if (existed != null && RecordEntity.VERSION_INTERMEDIATE == existed.getHistoryNumber()) {
                existed.getVersion().setHistoryNumber(rm.getHistoryNumber());
                em.merge(existed);
            }
        }
        em.merge(rm);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createRecord(BaseType baseType, CDSRMetaVO meta, GroupVO groupVO, int dfId, boolean statsData,
                            boolean wr, int highPriority) throws PreviousVersionException {
        DeliveryFileEntity de = em.find(DeliveryFileEntity.class, dfId);
        boolean autReprocess = DeliveryPackage.isAutReprocess(de.getName());
        RecordMetadataEntity rm = createMetadata(baseType, meta, groupVO, wr,
                autReprocess || DeliveryPackage.isRepeat(de.getName()));
        int ret = createRecord(meta, meta.getUnitStatusId(), rm, de, null, statsData,
                DeliveryPackage.isNoCDSRAut(baseType, de, wr));
        if (wr) {
            createWR(baseType, meta, de, ret, autReprocess, meta.isScheduled(), highPriority);
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createCDSRRecord(TranslatedAbstractVO tvo, ICDSRMeta meta, int dfId, Integer taDbId)
            throws PreviousVersionException {
        DeliveryFileEntity df = em.find(DeliveryFileEntity.class, dfId);
        RecordMetadataEntity rm = em.find(RecordMetadataEntity.class, meta.getId());
        // CPP5-926 -Support JATS translation to legacy RevMan source
        boolean statsExisted = RecordHelper.setExistingCDSRSrcPath(df.getDb().getTitle(), meta.getName(),
                tvo, meta.getHistoryNumber(), rm.isJats(), RepositoryFactory.getRepository());
        return createRecord(tvo, UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS, rm, df, taDbId, statsExisted, false);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int updateSPDRecordToCancel(ArchieEntry entry, int dbType, int dfId) throws CmsJTException {
        Integer dbId = findDb(Constants.SPD_ISSUE_ID, dbType);
        RecordEntity re = getRecordEntity(dbId, null, entry, false);
        PublishedAbstractEntity found = null;
        if (re != null) {
            List<PublishedAbstractEntity> paeList = PublishedAbstractEntity.queryAbstractsByDf(
                        re.getDeliveryFileId(), entry.getName(), em).getResultList();
            for (PublishedAbstractEntity pae: paeList) {
                if (pae.getPubNumber() != entry.getPubNumber() || !pae.sPD().is()) {
                    continue;
                }
                if (!PublishedAbstractEntity.isNotifiedOnPublished(pae.getNotified())) {
                    found = pae;
                }
                break;
            }
        }
        if (found != null) {
            re.setState(RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION);
            re.setDeliveryFile(em.find(DeliveryFileEntity.class, dfId));
            em.merge(re);
            found.setDeliveryId(dfId);
            found.setNotified(PublishDestination.WOLLIT.ordinal());
            found.sPD(false);
            em.merge(found);
            entry.setId(found.getId());
            return re.getId();
        }
        throw CmsJTException.createForContent(entry, CochraneCMSPropertyNames.getSPDNotFoundMsg());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createRecord(BaseType bt, ArchieEntry ae, int dfId, Integer taDbId, boolean statsData, boolean wr)
            throws PreviousVersionException {
        DeliveryFileEntity de = em.find(DeliveryFileEntity.class, dfId);
        boolean jats = DeliveryFileEntity.isJats(de.getType());
        boolean ml3g = !jats && DeliveryFileEntity.isWml3g(de.getType());
        boolean ta = taDbId != null;
        boolean statsExisted = statsData;
        RecordMetadataEntity rm = checkPrevious(ae, (ta || !bt.isCDSR() || ml3g) ? null : jats,
                RecordMetadataEntity.queryRecordMetadataLast(ae.getName(), em).getResultList());
        if (ta) {  // CPP5-926 -Support JATS translation to legacy RevMan source
            PreviousVersionException.checkTranslationRecord(ae, rm);
            statsExisted = RecordHelper.setExistingCDSRSrcPath(de.getDb().getTitle(), ae.getName(), ae,
                    RecordEntity.VERSION_LAST, rm.isJats(), RepositoryFactory.getRepository());
            // replace an empty translation title with article's one
            ae.setTitle(ae.getTitle() == null ? rm.getTitle() : ae.getTitle());
        }
        int ret = jats ? createRecord(ae, UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS, rm, de, taDbId,
            statsExisted, false) : ml3g ? createRecord(ae, UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED, rm, de, null,
            statsExisted, DeliveryPackage.isNoCDSRAut(bt, de, wr)) : createRecord(ae, null, rm, de, taDbId,
            statsExisted, false);
        if (wr) {
            createWR(bt, ae, de, ret, DeliveryPackage.isAutReprocess(de.getName()), ae.isScheduled(), 0);
        }
        return ret;
    }

    private int createRecord(ArchieEntry entry, Integer unitStatusId, RecordMetadataEntity rm,
        DeliveryFileEntity de, Integer taDbId, boolean statsData, boolean noCdsrAut) throws PreviousVersionException {
        boolean ta = taDbId != null;
        boolean mesh = unitStatusId != null && UnitStatusEntity.isMeshtermUpdated(unitStatusId);
        RecordEntity re = getRecordEntity(de.getDb().getId(), de.getId(), entry,
                CmsUtils.isImportIssue(de.getDb().getIssue().getId()));
        if (re != null)  {
            if (unitStatusId != null && UnitStatusEntity.isSystem(unitStatusId)
                && re.getUnitStatus() != null && !UnitStatusEntity.isSystem(re.getUnitStatus().getId())) {
                // for ta or mesh terms need to override the status and the record path with the previous one
                updateRecord(entry, de, re);
                updateRecordAttrs(entry, re.getUnitStatus().getId(), rm, statsData, ta || mesh, mesh || noCdsrAut, re);
            } else {
                updateRecord(entry, unitStatusId, rm, statsData, de, re, mesh || noCdsrAut);
            }
            em.merge(re);
            DbRecordPublishEntity.qDeleteByRecordIds(Collections.singletonList(re.getId()), em).executeUpdate();
        } else {
            re = createRecord(entry, unitStatusId, rm, statsData, de, ta, mesh || noCdsrAut);
        }
        if (ta) {
            createTaDbRecord(entry, em.find(ClDbEntity.class, taDbId), de.getId(), entry.isJatsTa());
        }
        if (rm != null) {
            entry.setStage(rm.getStage());
        }
        return re.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void addDbRecord(DbRecordVO dbVO, Date date, int dbType) {
        DbRecordEntity dbe = createDbRecord(dbVO.getLabel(), dbVO.getNumber(), dbVO.getLanguage(), dbVO.getStatus(),
                dbVO.getVersion(), em.find(ClDbEntity.class, dbVO.getDbId()), dbVO.getDfId());
        dbe.setDate(date);
        em.merge(dbe);
        if (!dbe.isHistorical() && !dbe.isDeleted()) {
            LastRecordEntity le = new LastRecordEntity(dbe, find(ClDbEntity.class, dbType));
            em.persist(le);
        }
        dbVO.setId(dbe.getId());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void addTranslation(TranslatedAbstractVO tr, int dfId, Integer taDbId, @NotNull Integer recId,
                               boolean wr) throws PreviousVersionException {
        DeliveryFileEntity de = em.find(DeliveryFileEntity.class, dfId);
        RecordEntity re = em.find(RecordEntity.class, recId);
        if (re.getMetadata() != null) {
            PreviousVersionException.checkTranslationRecord(tr, re.getMetadata());
        }
        if (wr) {
            createWR(BaseType.getCDSR().get(), tr, de, recId, DeliveryPackage.isAutReprocess(de.getName()), false, 0);
        }
        createTaDbRecord(tr, em.find(ClDbEntity.class, taDbId), dfId, tr.isJats());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateWhenReadyOnReceived(List<ArchieEntry> list, int dfId, boolean ok, boolean cancel, boolean spd) {
        int notified = ok ? PublishedAbstractEntity.REC_NOTIFIED : (cancel ? PublishedAbstractEntity.NOT_NOTIFIED
                + PublishedAbstractEntity.CANCEL_NOTIFIED : PublishedAbstractEntity.REC_NOTIFIED_ON_FAIL);
        int state = ok ? RecordEntity.STATE_PROCESSING : RecordEntity.STATE_WR_ERROR_FINAL;
        if (DbUtils.isOneCommit(list.size())) {
            List<Integer> ids = new ArrayList<>();
            for (ArchieEntry re: list) {
                if (re.getId() != DbEntity.NOT_EXIST_ID) {
                    ids.add(re.getId());
                    continue;
                }
                createOnFailed(re, dfId, notified, spd);
            }
            if (!ids.isEmpty()) {
                PublishedAbstractEntity.querySetTaAbstractsReceived(ids, notified, state, em).executeUpdate();
            }
        } else {
            CollectionCommitter<Integer> committer = new CollectionCommitter<Integer>() {
                @Override
                public void commit(Collection<Integer> list) {
                    PublishedAbstractEntity.querySetTaAbstractsReceived(list, notified, state, em).executeUpdate();
                }
            };
            for (ArchieEntry re: list) {
                if (re.getId() != DbEntity.NOT_EXIST_ID) {
                    committer.commit(re.getId());
                    continue;
                }
                createOnFailed(re, dfId, notified, spd);
            }
            committer.commitLast();
        }
    }

    private void createOnFailed(ArchieEntry ae, int dfId, int notified, boolean spd) {
        if (notified == PublishedAbstractEntity.REC_NOTIFIED_ON_FAIL) {
            try {
                PublishedAbstractEntity p = createWR(BaseType.getCDSR().get(), ae,
                        em.find(DeliveryFileEntity.class, dfId), null, false, spd, 0);
                p.setNotified(notified);
                em.merge(p);
            } catch (PreviousVersionException pe) {
                LOG.error(pe.getMessage());
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateWhenReadyOnPublished(List<ArchieEntry> list, int dfId, boolean ok) {
        if (list.isEmpty()) {
            return;
        }
        Date date = null;
        Set<String> names = null;
        if (ok) {
            date = new Date();
        } else {
            names = new HashSet<>();
        }
        for (ArchieEntry re: list) {
            List<PublishedAbstractEntity> paeList = PublishedAbstractEntity.queryAbstractsByDf(
                    dfId, re.getName(), em).getResultList();
            for (PublishedAbstractEntity pae: paeList) {
                if (re.same(pae)) {
                    pae.setNotified(pae.getPubNotified() + PublishedAbstractEntity.PUB_NOTIFIED);
                    pae.setPublishedDate(date);
                    em.merge(pae);
                    break;
                }
            }
            if (names != null) {
                names.add(re.getName());
            }
        }
        if (!ok) {
            RecordEntity.setRecordsStateByDf(RecordEntity.STATE_WR_ERROR, RecordEntity.STATE_WR_ERROR_FINAL,
                    dfId, names, em);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishedAbstractEntity> getWhenReadyByDeliveryPackage(int dfId) {
        return PublishedAbstractEntity.queryAbstractsByDf(dfId, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PublishedAbstractEntity> getWhenReadyByDeliveryPackage(String cdNumber, int dfId) {
        return PublishedAbstractEntity.queryAbstractsByDf(dfId, cdNumber, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void cancelWhenReady(int recordId, String cdNumber, int recordNumber, int dfId) {
        RecordEntity.querySetRecordsStateByRecord(RecordEntity.STATE_UNDEFINED, recordId, em).executeUpdate();
        PublishedAbstractEntity.querySetAbstractsCanceledNotified(cdNumber, dfId, em).executeUpdate();
        PublishRecordEntity.queryDeleteUnPublishedRecords(recordNumber, dfId, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RecordEntity> getUnfinishedRecords(int dbId) {
        return RecordEntity.queryRecordsUnfinished(null, dbId, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordState(Collection<Integer> oldStates, int newState, int clDbId, Collection<String> names) {
        return RecordEntity.querySetRecordsStateByDb(oldStates, newState, clDbId, names, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordState(int oldState, int newState, int clDbId, Collection<String> names) {
        return RecordEntity.querySetRecordsStateByDb(oldState, newState, clDbId, names, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordState(int newState, int dbId, Collection<String> cdNumbers) {
        return RecordEntity.setRecordsStateByDb(newState, dbId, cdNumbers,
                RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION, em);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public RecordEntity setRecordState(int newState, int clDbId, String cdNumber) {
        List<RecordEntity> list = RecordEntity.queryRecord(clDbId, cdNumber, em).getResultList();
        if (!list.isEmpty()) {
            RecordEntity re = list.get(0);
            re.setState(newState);
            em.merge(re);
            return re;
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordStateByDeliveryPackage(int oldState, int newState, int dfId, Collection<String> names) {
        return RecordEntity.setRecordsStateByDf(oldState, newState, dfId, names, em);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordFailed(Collection<Integer> oldStates, int clDbId, Collection<String> names,
                               boolean qasCompleted, boolean qas, boolean rendCompleted) {
        return RecordEntity.setRecordsStateFailedByDb(oldStates, clDbId, names, qasCompleted, qas, rendCompleted, em);
    }

    private PublishedAbstractEntity setWhenReadyRecordsReprocessed(PublishedAbstractEntity we, ArchieEntry ae,
            int dbId, boolean prev) throws PreviousVersionException {
        List<PublishedAbstractEntity> paeList = PublishedAbstractEntity.queryAbstractsByDb(
                dbId, ae.getName(), em).getResultList();
        PublishedAbstractEntity found = null;
        for (PublishedAbstractEntity pe: paeList) {
            if (!pe.same(we)) {
                continue;
            }
            int notified = pe.getNotified();
            boolean wasCanceled = PublishedAbstractEntity.isCanceled(notified);
            int realNotified = PublishedAbstractEntity.clearCanceled(notified);
            if (found == null) {
                found = pe;
                if (PublishedAbstractEntity.isNotifiedOnPublished(realNotified)) {
                    throw new PreviousVersionException(String.format(
                            "%s cannot be re-processed as it was already notified on published", pe.getPubName()));
                }
                if (PublishedAbstractEntity.isNotifiedOnReceivedSuccess(realNotified)) {
                    we.setNotified(PublishedAbstractEntity.REC_NOTIFIED);
                    ae.setWasNotified(true);
                    LOG.debug(String.format("%s is going reprocessing without Archie notifying on received", pe));
                } else {
                    LOG.debug(String.format("%s is going reprocessing, Archie will be notified on received", pe));
                }
            }
            if (!wasCanceled) {
                pe.setNotified(notified + PublishedAbstractEntity.CANCEL_NOTIFIED);
                em.merge(pe);
            }
        }
        if (found == null) {
            if (prev) {
                LOG.warn(String.format("cannot find an old when ready record for %s to be re-processed", we));
            } else {
                ClDbEntity prevClDb = findPreviousClDbEntity(dbId);
                return prevClDb != null ? setWhenReadyRecordsReprocessed(we, ae, prevClDb.getId(), true) : null;
            }
        }
        return found;
    }

    private ClDbEntity findPreviousClDbEntity(int dbId) {
        ClDbEntity clDb = em.find(ClDbEntity.class, dbId);
        int year = clDb.getIssue().getYear();
        int issue = clDb.getIssue().getNumber();
        List<IssueEntity> lI = IssueEntity.getPreviousIssue(year, issue, em).getResultList();
        if (!lI.isEmpty()) {
            List<ClDbEntity> lD = ClDbEntity.queryClDb(BaseType.find(clDb.getTitle()).get().getDbId(),
                    lI.get(0).getId(), em).getResultList();
            if (!lD.isEmpty() && lD.get(0).getIssue().getFullNumber() == CmsUtils.getPreviousIssueNumber(year, issue)) {
                return lD.get(0);
            }
        }
        return null;
    }

    private PublishedAbstractEntity createWR(BaseType bt, ArchieEntry ae, DeliveryFileEntity de, Integer recId,
            boolean reprocess, boolean spd, int highPriority) throws PreviousVersionException {
        PublishedAbstractEntity ret = ae.createWhenReadyEntity(bt, de.getId(), de.getDb().getId(), recId);
        if (spd) {
            ret.sPD(true);
        }
        if (reprocess) {
            PublishedAbstractEntity old = setWhenReadyRecordsReprocessed(ret, ae, de.getDb().getId(), false);
            if (old != null) {
                ret.setInitialDeliveryId(old.getInitialDeliveryId());
                ret.setManuscriptNumber(old.getManuscriptNumber());
                ret.setAcknowledgementId(old.getAcknowledgementId());
                ret.setHighPriority(old.getHighPriority());
            }
        }
        if (highPriority > 0) {
            ret.setHighPriority(highPriority);
        }
        if (!bt.isCDSR())  {
            ret.setNotified(PublishedAbstractEntity.REC_NOTIFIED);
        }
        em.persist(ret);
        ae.setId(ret.getId());
        return ret;
    }

    private RecordEntity getRecordEntity(int dbId, @NotNull Integer dfId, ArchieEntry ae, boolean toImport)
            throws PreviousVersionException {
        List<RecordEntity> list = RecordEntity.queryRecords(dbId, ae.getName(), em).getResultList();
        for (RecordEntity re: list) {
            if (!toImport) {
                return re;
            }
            RecordMetadataEntity rm = re.getMetadata();
            if (rm != null && rm.getPubNumber() == ae.getPubNumber() && !dfId.equals(re.getDeliveryFile().getId())
                    && rm.isJatsImported()) {
                throw new PreviousVersionException(new ErrorInfo<>(ae, ae.getName() + "already imported"));
            }
        }
        return null;
    }

    private RecordEntity createRecord(ArchieEntry record, Integer unitStatusId, RecordMetadataEntity rm,
            boolean statsData, DeliveryFileEntity deliveryFile, boolean isTa, boolean processing) {
        RecordEntity re = new RecordEntity();
        updateRecord(record, unitStatusId, rm, statsData, deliveryFile, re, processing);
        if (isTa && unitStatusId == null) {
            re.setUnitStatus(em.find(UnitStatusEntity.class, UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS));
        }
        em.persist(re);
        return re;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getLastRecords(String recName, int dbType) {
        return LastRecordEntity.queryRecordVOs(RecordHelper.buildRecordNumber(recName, 2), dbType, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getLastRecords(Collection<String> recordNames, int dbType) {
        List<Integer> list = new ArrayList<>();
        recordNames.forEach(name -> list.add(RecordHelper.buildRecordNumber(name, 2)));
        return LastRecordEntity.queryRecordVOs(list, dbType, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<DbRecordVO> restoreTranslations(int number, Integer dfId, Integer version) throws CmsJTException {
        int dbType = DatabaseEntity.CDSR_TA_KEY;
        boolean previous = !version.equals(RecordEntity.VERSION_LAST);
        List<LastRecordEntity> lastList = previous ? Collections.emptyList()
                : LastRecordEntity.queryRecords(number, dbType, em).getResultList();
        List<DbRecordEntity> fullList = DbRecordEntity.queryRecords(number, dbType, version, em).getResultList();
        Map<String, DbRecordEntity> oldMap = new HashMap<>();
        Map<String, DbRecordEntity> importedMap = new HashMap<>();
        List<DbRecordVO> ret = new ArrayList<>();
        Date date = new Date();
        for (DbRecordEntity dbe: fullList) {
            if (dbe.getVersion() == version) {
                if (dfId.equals(dbe.getDeliveryId())) {
                    importedMap.put(dbe.getLanguage(), dbe);
                } else if (dbe.getStatus() == DbRecordEntity.STATUS_DELETED_BY_IMPORT) {
                    oldMap.put(dbe.getLanguage(), dbe);
                }
            }
        }
        for (LastRecordEntity le: lastList) {
            DbRecordEntity dbe = le.getRecord();
            String lang = dbe.getLanguage();
            DbRecordEntity imp = importedMap.get(lang);
            if (imp == null || imp.getId() != dbe.getId()) {
                throw new CmsJTException(String.format("%s.%s is newer than the imported one", dbe.getLabel(), lang));
            }
            DbRecordEntity old = oldMap.remove(lang);
            if (old == null) {
                em.remove(le);
            } else {
                updateDbRecord(old, null, DbRecordEntity.STATUS_NORMAL, date, ret);
                updateLastRecord(le, old, DbRecordEntity.STATUS_NORMAL, ret);
            }
        }
        ClDbEntity db = find(ClDbEntity.class, dbType);
        for (DbRecordEntity old: oldMap.values()) {
            updateDbRecord(old, null, DbRecordEntity.STATUS_NORMAL, null, ret);
            if (!previous) {
                LastRecordEntity le = new LastRecordEntity(old, db);
                em.persist(le);
            }
        }
        importedMap.values().forEach(imp -> updateDbRecordAndType(imp, RecordEntity.VERSION_SHADOW, 1, date, ret));
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<DbRecordVO> restoreTranslationsFromBackUp(int issueId, String recordName, Set<String> langsExisted) {
        int dbType = DatabaseEntity.CDSR_TA_KEY;
        int number = RecordHelper.buildRecordNumber(recordName, 2);
        List<DbRecordVO> ret = new ArrayList<>();
        Integer dbId = findDb(issueId, dbType);
        if (dbId == null) {
            LOG.warn(String.format("cannot find db type [%d], issue [%d]", dbType, issueId));
            return null;
        }
        Map<String, DbRecordEntity> prevMap = new HashMap<>();
        for (String lang: langsExisted) {
            List<DbRecordEntity> prevList = DbRecordEntity.queryTranslationBefore(number, lang, issueId,
                    em).getResultList();
            if (prevList.isEmpty()) {
                LOG.warn(String.format("cannot restore a translation %s of %d, issue [%d]", lang, number, issueId));
                continue;
            }
            prevMap.put(lang, prevList.get(0));
        }
        Date date = new Date();
        List<LastRecordEntity> lastList = LastRecordEntity.queryRecords(number, dbType, em).getResultList();
        for (LastRecordEntity le: lastList) {
            DbRecordEntity dbe = le.getRecord();
            String lang = dbe.getLanguage();
            DbRecordEntity prev = prevMap.remove(lang);
            if (prev == null) {
                deleteLastRecord(le, RecordEntity.VERSION_LAST, DbRecordEntity.STATUS_DELETED_BY_RESTORE, date, ret);
                em.remove(dbe);
                continue;
            }
            if (prev.getId() != dbe.getId()) {
                updateLastRecord(le, prev, DbRecordEntity.STATUS_NORMAL, null);
                if (prev.isDeleted()) {
                    updateDbRecord(prev, null, date);
                } else if (prev.isHistorical()) {
                    updateDbRecord(prev, RecordEntity.VERSION_LAST, date);
                }
            }
        }
        ClDbEntity db = find(ClDbEntity.class, dbType);  // restore deleted
        for (Map.Entry<String, DbRecordEntity> entry: prevMap.entrySet()) {
            DbRecordEntity prev = entry.getValue();
            LastRecordEntity le = new LastRecordEntity(prev, db);
            em.persist(le);
            updateDbRecord(prev, prev.isHistorical() ? RecordEntity.VERSION_LAST : null, date);
        }
        List<DbRecordEntity> delList = DbRecordEntity.queryRecordsByDb(number, dbId, em).getResultList();
        delList.forEach(del -> em.remove(del));
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<DbRecordVO> importTranslations(int number, Integer version, boolean removeExisting,
                                               Map<Integer, DbRecordVO> existed, Map<Integer, DbRecordVO> imported) {
        int dbType = DatabaseEntity.CDSR_TA_KEY;
        Date date = new Date();
        List<DbRecordEntity> newList = imported.keySet().isEmpty() ? new ArrayList<>()
                : DbRecordEntity.queryRecords(imported.keySet(), em).getResultList();
        List<DbRecordVO> ret = new ArrayList<>();
        if (!version.equals(RecordEntity.VERSION_LAST)) {
            List<DbRecordEntity> oldList = DbRecordEntity.queryRecords(existed.keySet(), em).getResultList();
            oldList.forEach(dbe -> updateDbRecord(dbe, null, DbRecordEntity.STATUS_DELETED_BY_IMPORT, date, ret));
            newList.forEach(dbe -> updateDbRecordAndType(dbe, version, RecordMetadataEntity.IMPORTED, date, ret));
            return ret;
        }
        List<LastRecordEntity> lastList = LastRecordEntity.queryRecords(number, dbType, em).getResultList();
        lastList.removeIf(le -> !existed.containsKey(le.getRecord().getId()));
        if (newList.isEmpty()) {
            if (!lastList.isEmpty() && removeExisting) {  // remove existing translations
                deleteTranslations(lastList, version, DbRecordEntity.STATUS_DELETED_BY_IMPORT, date, ret);
            }
        } else {  // replace existing translations, add new translations
            updateTranslations(lastList, newList, dbType, DbRecordEntity.STATUS_DELETED_BY_IMPORT,
                    RecordMetadataEntity.IMPORTED, removeExisting, ret);
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<DbRecordVO> moveTranslationsToEntire(String recordName, int dfId, Integer version, boolean updated) {
        int dbType = DatabaseEntity.CDSR_TA_KEY;
        int number = RecordHelper.buildRecordNumber(recordName, 2);
        Date date = new Date();
        List<DbRecordEntity> newList = DbRecordEntity.queryRecordsByDf(number, dfId, em).getResultList();
        List<LastRecordEntity> lastList = LastRecordEntity.queryRecords(number, dbType, em).getResultList();
        List<DbRecordVO> ret = new ArrayList<>();
        boolean vChanged = version != null;
        if (vChanged) {
            deleteTranslations(lastList, version, DbRecordEntity.STATUS_NORMAL, date, ret);
            lastList.clear();
        }
        if (newList.isEmpty()) {                               // no fresh translations to update
            if (!vChanged && updated && !lastList.isEmpty()) { // remove existing translations
                deleteTranslations(lastList, RecordEntity.VERSION_LAST, DbRecordEntity.STATUS_DELETED, date, ret);
            }
        } else if (!updated) {                                // add new translations to the existed ones
            updateTranslations(lastList, newList, dbType, DbRecordEntity.STATUS_NORMAL, -1, true, ret);
        } else {                                              // remove existing translations, add new translations
            updateTranslations(lastList, newList, dbType, DbRecordEntity.STATUS_DELETED, -1, true, ret);
        }
        return ret;
    }

    private void deleteTranslations(Collection<LastRecordEntity> lastList, int version, int status, Date date,
                                    List<DbRecordVO> ret) {
        lastList.forEach(le -> deleteLastRecord(le, version, status, date, ret));
    }

    private void updateTranslations(List<LastRecordEntity> lastList, List<DbRecordEntity> newList, int dbType,
        int status, int type, boolean removeRemained, List<DbRecordVO> ret) {
        Date date = new Date();
        Map<String, LastRecordEntity> map = new HashMap<>();
        lastList.forEach(le -> map.put(le.getRecord().getLanguage(), le));
        ClDbEntity db = find(ClDbEntity.class, dbType);
        for (DbRecordEntity dbe: newList) {
            String lang = dbe.getLanguage();
            LastRecordEntity le = map.remove(lang);
            if (dbe.isDeleted()) {
                if (le != null) {
                    deleteLastRecord(le, RecordEntity.VERSION_LAST, status, date, null);
                }
            } else if (le == null) {
                le = new LastRecordEntity(dbe, db);
                em.persist(le);
            } else {
                updateLastRecord(le, dbe, status, ret);
            }
            dbe.setVersion(RecordEntity.VERSION_LAST);
            if (type > 0 && type != dbe.getType()) {
                dbe.setType(type);
            }
            em.merge(dbe);
            ret.add(new DbRecordVO(dbe));
        }
        if (removeRemained && status >= DbRecordEntity.STATUS_DELETED && !map.isEmpty()) {
            deleteTranslations(map.values(), RecordEntity.VERSION_LAST, status, date, ret);
        }
    }

    private void deleteLastRecord(LastRecordEntity le, Integer version, int status, Date date, List<DbRecordVO> ret) {
        updateDbRecord(le.getRecord(), version, status, date, ret);
        em.remove(le);
    }

    private void updateLastRecord(LastRecordEntity le, DbRecordEntity dbe, int status, List<DbRecordVO> ret) {
        if (status == DbRecordEntity.STATUS_DELETED_BY_IMPORT) {
            updateDbRecord(le.getRecord(), null, DbRecordEntity.STATUS_DELETED_BY_IMPORT, null, ret);
        }
        le.setRecord(dbe);
        le.setIssue(dbe.getDb().getIssue().getFullNumber());
        le.setLabel(dbe.getLabel());
        em.merge(le);
    }

    private void updateDbRecordAndType(DbRecordEntity dbe, Integer v, int type, Date date, List<DbRecordVO> ret) {
        updateDbRecord(dbe, v, DbRecordEntity.STATUS_NORMAL, date, ret);
        dbe.setType(type);
        em.merge(dbe);
    }

    private void updateDbRecord(DbRecordEntity dbe, Integer v, Date date) {
        updateDbRecord(dbe, v, DbRecordEntity.STATUS_NORMAL, date, null);
    }

    private void updateDbRecord(DbRecordEntity dbe, Integer v, int status, Date date, List<DbRecordVO> ret) {
        if (v != null) {
            dbe.setVersion(v);
        }
        if (date != null) {
            dbe.setDate(date);
        }
        dbe.setStatus(status);
        if (ret != null) {
            ret.add(new DbRecordVO(dbe));
        }
        em.merge(dbe);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getLanguages(String recordName) {
        return LastRecordEntity.queryLanguages(RecordHelper.buildRecordNumber(recordName, 2),
                DatabaseEntity.CDSR_TA_KEY, 0, RecordMetadataEntity.IMPORTED, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getLanguages(String recordName, boolean jats) {
        return LastRecordEntity.queryLanguages(RecordHelper.buildRecordNumber(recordName, 2),
                DatabaseEntity.CDSR_TA_KEY, jats ? 1 : 0, jats ? RecordMetadataEntity.IMPORTED : 0, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getLanguages(String recordName, int version, boolean jats) {
        return DbRecordEntity.queryLanguages(RecordHelper.buildRecordNumber(recordName, 2), DatabaseEntity.CDSR_TA_KEY,
                version, jats ? 1 : 0, jats ? RecordMetadataEntity.IMPORTED : 0, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getTranslationCount(String lang) {
        return getSingleResultIntValue(LastRecordEntity.queryTranslationCount(lang, DatabaseEntity.CDSR_TA_KEY, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getTranslationUniqueCount() {
        return getSingleResultIntValue(LastRecordEntity.queryTranslationUniqueCount(DatabaseEntity.CDSR_TA_KEY, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getTranslations(String lang, int skip, int batch) {
        return LastRecordEntity.queryTranslationVOs(lang, DatabaseEntity.CDSR_TA_KEY, skip, batch, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getTranslations(int recordNumber, int dfId) {
        List<DbRecordEntity> list = DbRecordEntity.queryRecordsByDf(recordNumber, dfId, em).getResultList();
        return list.isEmpty() ? Collections.emptyList()
                : list.stream().map(DbRecordVO::new).collect(Collectors.toList());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getTranslationHistory(String lang, int skip, int batchSize) {
        return DbRecordEntity.queryHistoryTranslationVOs(lang, skip, batchSize, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DbRecordVO getTranslation(int recordNumber, String lang) {
        List<DbRecordVO> list = LastRecordEntity.queryTranslationVO(recordNumber, lang,
                DatabaseEntity.CDSR_TA_KEY, em).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DbRecordVO getTranslation(int recordNumber, String lang, int dbId) {
        List<DbRecordVO> list = DbRecordEntity.queryTranslationVOsByDb(recordNumber, lang, dbId, em).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getTranslationHistory(int recordNumber, Integer version) {
        return version == null ? DbRecordEntity.queryHistoryTranslationVOs(recordNumber, em).getResultList()
                : DbRecordEntity.queryHistoryTranslationVOs(recordNumber, version, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getLastTranslations(int recordNumber) {
        return LastRecordEntity.queryRecordVOs(recordNumber, DatabaseEntity.CDSR_TA_KEY, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getLastTranslations(int skip, int batchSize) {
        return LastRecordEntity.queryAllRecordVOs(skip, batchSize, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DbRecordVO> getAllTranslationHistory(int skip, int batchSize) {
        return DbRecordEntity.queryHistoryTranslationVOs(skip, batchSize, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Integer findDb(int issueId, int dbType) {
        List<ClDbEntity> list = ClDbEntity.queryClDb(dbType, issueId, em).getResultList();
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private void checkGroupTitle(CDSRMetaVO meta, GroupVO gvo, GroupEntity ge, boolean cdsrImport) {
        String groupTitle = meta.getGroupTitle();
        if (groupTitle == null || groupTitle.trim().isEmpty()) {
            LOG.warn(String.format("%s - empty CRG group title", meta));
            return;
        }
        if (!gvo.getUnitTitle().equals(groupTitle)) {
            if (cdsrImport) {
                LOG.warn(String.format("%s - CRG group title changed. %s => %s", meta, gvo.getUnitTitle(), groupTitle));
                return;
            }
            LOG.info(String.format("CRG group title changed! %s => %s", gvo.getUnitTitle(), groupTitle));
            gvo.setUnitTitle(groupTitle);
            ge.setTitle(groupTitle);
            em.merge(ge);
        }
    }

    private RecordMetadataEntity getMetadataToReprocessSPD(BaseType baseType, CDSRMetaVO meta)
            throws PreviousVersionException {
        List<RecordMetadataEntity> prevList = findPreviousMetadata(baseType, meta.getIssue(), meta.getCdNumber(), true);
        if (prevList.isEmpty()) {
            throw new PreviousVersionException(String.format("no metadata found to re-process SPD %s",
                meta.getPubName()));
        }
        RecordMetadataEntity ret = prevList.get(0);
        if (ret.getPubNumber() != meta.getPubNumber() || ret.getVersion().isVersionLatest()) {
            throw new PreviousVersionException(String.format("no proper metadata found to re-process SPD %s",
                meta.getPubName()));
        }
        meta.setId(ret.getId());
        meta.setTitleId(ret.getTitleEntity());
        return ret;
    }

    private RecordMetadataEntity createMetadata(BaseType bt, CDSRMetaVO meta, GroupVO group, boolean wr,
                                                boolean reprocess) throws PreviousVersionException {
        GroupEntity ge = em.find(GroupEntity.class, group.getId());
        boolean cdsrImport = CmsUtils.isImportIssueNumber(meta.getIssue());
        PublishDate spdDate = null;
        if (!cdsrImport && meta.isScheduled()) {
            if (reprocess) {
                return getMetadataToReprocessSPD(bt, meta);
            }
            spdDate = new PublishDate(meta.getPublishedOnlineFinalForm());
        }
        checkGroupTitle(meta, group, ge, cdsrImport);
        RecordMetadataEntity prev = null;
        if (!cdsrImport) {
            List<RecordMetadataEntity> prevList = findPreviousMetadata(bt,
                    spdDate != null ? spdDate.issue() : meta.getIssue(), meta.getCdNumber(), false);
            if (!prevList.isEmpty()) {
                prev = checkPrevious(meta, null, prevList);
            }
        }
        boolean isPresumableNewDoi = prev == null || prev.getVersion().getPubNumber() < meta.getPubNumber()
                        || (prev.getFirstOnline() == null && prev.getVersion().isNewDoi());
        VersionEntity ve = new VersionEntity(RecordHelper.buildRecordNumber(meta.getCdNumber()), meta.getPubNumber(),
                meta.getCochraneVersion(), isPresumableNewDoi);
        em.persist(ve);
        TitleEntity ute = TitleEntity.checkEntity(meta.getTitle(), prev != null ? prev.getTitleEntity() : null, em);
        RecordMetadataEntity rme = new RecordMetadataEntity(meta, ve, ute, ge, prev);
        if (bt.isActualPublicationDateSupported()) {
            PublishHelper.setInputPublishDates(bt, meta, rme, prev, !reprocess && !cdsrImport, spdDate, wr);
        }
        em.persist(rme);
        meta.setId(rme.getId());
        meta.setTitleId(ute);
        return rme;
    }

    private List<RecordMetadataEntity> findPreviousMetadata(BaseType bt, int issue, String cdNumber, boolean repSPD) {
        List<RecordMetadataEntity> ret = RecordMetadataEntity.queryRecordMetadataByIssue(
                issue, cdNumber, 1, em).getResultList();
        if (!ret.isEmpty() && !repSPD && !ret.get(0).getVersion().isVersionFinal()) {
            ret = bt.isCDSR() ? RecordMetadataEntity.queryRecordMetadataByIssue(issue, cdNumber, 0, em).getResultList()
                : RecordMetadataEntity.queryRecordMetadataHistory(cdNumber, Constants.FIRST_PUB, em).getResultList();
            ret.removeIf(rme -> !rme.getVersion().isVersionFinal());
        }
        return ret;
    }

    private RecordMetadataEntity checkPrevious(ArchieEntry ae, Boolean jats, List<RecordMetadataEntity> prevList)
            throws PreviousVersionException {
        if (prevList.isEmpty()) {
            throw PreviousVersionException.createForMetadata(ae, "no proper metadata found");
        }
        RecordMetadataEntity prev = prevList.get(0);
        if (ae.getPubNumber() == 0) {
            ae.setPubNumber(prev.getPubNumber());
        } else {
            PreviousVersionException.checkPreviousRecord(ae, prev);
        }
        if (jats != null && prev.isJats() != jats) {
            throw PreviousVersionException.createForMetadata(ae, "previous metadata has no proper type");
        }
        return prev;
    }

    private void createTaDbRecord(ArchieEntry ae, ClDbEntity db, Integer dfId, boolean jats) {
        DbRecordEntity dbe = createDbRecord(ae.getPubName(), RecordHelper.buildRecordNumberCdsr(ae.getName()),
                ae.getLanguage(), ae.isDeleted() ? DbRecordEntity.STATUS_RETRACTED
                        : DbRecordEntity.STATUS_NORMAL, RecordEntity.VERSION_SHADOW, db, dfId);
        if (jats) {
            dbe.setTitleEntity(TitleEntity.checkEntity(ae.getTitle(), em));
            ae.setTitleId(dbe.getTitleEntity());
            dbe.setType(1);
            em.merge(dbe);
        }
    }

    private DbRecordEntity createDbRecord(String label, int number, String lang, int status, int version,
                                          ClDbEntity db, Integer dfId) {
        DbRecordEntity re = new DbRecordEntity(label, number, lang, status, version, db, dfId);
        em.persist(re);
        return re;
    }

    private void updateRecord(ArchieEntry ae, Integer unitStatusId, RecordMetadataEntity rm,
                              boolean statsData, DeliveryFileEntity de, RecordEntity re, boolean processing) {
        updateRecord(ae, de, re);
        setRecordAttrs(ae.getPath(), unitStatusId, rm, statsData, re);
        if (processing) {
            re.setState(RecordEntity.STATE_PROCESSING);
        }
    }

    private void updateRecordAttrs(ArchieEntry record, Integer unitStatusId, RecordMetadataEntity rm,
                                   boolean statsData, boolean taOrMesh, boolean processing, RecordEntity re) {
        RecordMetadataEntity rmPrev = re.getMetadata();
        if (rm != null && rm.isJats() && taOrMesh && (rmPrev == null || rmPrev.wasUploaded())) {
            // CPP5-671 - to save the path of the article uploaded before
            setRecordAttrs(re.getRecordPath(), unitStatusId, rm, re.isRawDataExists(), re);
            record.setPath(re.getRecordPath());
        } else {
            setRecordAttrs(record.getPath(), unitStatusId, rm, statsData, re);
        }
        if (processing) {
            re.setState(RecordEntity.STATE_PROCESSING);
        }
    }

    private void updateRecord(ArchieEntry ae, DeliveryFileEntity de, RecordEntity re) {
        re.setName(ae.getName());
        re.setDb(de.getDb());
        re.setDeliveryFile(de);
    }

    private void setRecordAttrs(String path, Integer unitStatusId, RecordMetadataEntity rm, boolean statsData,
                                RecordEntity re) {
        if (unitStatusId == null) {
            resetRecordAttrs(re);
        } else {
            re.setUnitStatus(find(UnitStatusEntity.class, unitStatusId));
            re.setRawDataExists(statsData);
            re.setRecordPath(path);
            re.setQasCompleted(true);
            re.setQasSuccessful(true);
            resetRecordAttrs4Update(re);
        }
        if (rm != null) {
            re.setMetadata(rm);
            re.setTitleEntity(rm.getTitleEntity());
            Integer subtitleId = rm.getSubTitle();
            if (subtitleId != null) {
                re.setProductSubtitle(getManager().find(ProductSubtitleEntity.class, subtitleId));
            }
        }
    }

    private void resetRecordAttrs(RecordEntity re) {
        re.setQasCompleted(false);
        re.setQasSuccessful(false);
        re.setRecordPath(null);
        re.setRawDataExists(false);
        re.setTitleEntity(null);
        re.setProductSubtitle(null);
        resetRecordAttrs4Update(re);
    }

    private void resetRecordAttrs4Update(RecordEntity re) {
        re.setApproved(false);
        re.setRejected(false);
        re.setRenderingCompleted(false);
        re.setRenderingSuccessful(false);
    }
}
