package com.wiley.cms.cochrane.cmanager.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.TinyRecordVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.IRenderingStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.RenderFOPHandler;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.CollectionCommitter;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 */
@Stateless
@Local(IRenderManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class   RenderManager implements IRenderManager {
    private static final Logger LOG = Logger.getLogger(RenderManager.class);

    @EJB(beanName = "CMSProcessManager")
    private ICMSProcessManager pm;

    @EJB(beanName = "RenderingStorage")
    private IRenderingStorage rs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recStorage;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage resultsStorage;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    public void startFOPRendering(ClDbVO dbVO, String owner) {
        List<RecordEntity> records = recStorage.getDbRecordList(
                dbVO.getId(), 0, 0, null, 0, null, SearchRecordOrder.NAME, false);
        List<IRecord> recordsToRender = new ArrayList<>();
        boolean cdsr = CochraneCMSPropertyNames.getCDSRDbName().equals(dbVO.getTitle());
        boolean spd = CmsUtils.isScheduledDb(dbVO.getId());
        for (RecordEntity re: records) {
            String cdNumber = re.getName();
            if (!re.isQasSuccessful() || (spd && (re.isPublishingCancelled() || !re.isAwaitingPublication()))
                    || (!spd && re.isProcessed())) {
                LOG.warn(String.format("%s seems not to be fully processed to be re-rendered", cdNumber));
                continue;
            }
            TinyRecordVO rvo = cdsr ? (spd ? checkMetadataAndTranslations(re)
                    : checkMetadataAndTranslations(dbVO.getIssue().getFullNumber(), re)) : new TinyRecordVO(re);
            if (rvo != null) {
                rvo.setRecordPath(ContentLocation.ISSUE.getPathToMl3g(dbVO.getIssue().getId(), dbVO.getTitle(), null,
                        cdNumber, false));
                recordsToRender.add(rvo);
            }
        }
        startFOPRendering(dbVO, recordsToRender, owner);
    }

    public void startFOPRendering(ClDbVO dbVO, Collection<IRecord> records, String owner) {
        startFOPRendering(new DbHandler(dbVO.getIssue().getFullNumber(), dbVO.getTitle(), dbVO.getId(),
                        DbEntity.NOT_EXIST_ID, dbVO.getIssue().getId()), records, owner);
    }

    public void startFOPRendering(DeliveryFileVO df, Collection<IRecord> records, String owner) {
        startFOPRendering(new DbHandler(df.getFullIssueNumber(), df.getDbName(), df.getDbId(), df.getId(),
                df.getName(), df.getIssue()), records, owner);
    }

    private void startFOPRendering(DbHandler dbHandler, Collection<IRecord> records, String owner) {
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_RENDER_FOP).get();

        RenderFOPHandler mainHandler = new RenderFOPHandler(dbHandler);

        Map<String, IRecord> inputData = new HashMap<>();
        records.forEach(r -> inputData.put(r.getName(), r));

        mainHandler.acceptResult(inputData);
        pm.startProcess(mainHandler, pt, owner);
    }

    public void createFOPRendering(Collection<IRecord> records) {
        rs.createRenderingRecords(records, RenderingPlan.PDF_FOP.id());
    }

    public void resetRendering(Collection<IRecord> records) {
        if (!records.isEmpty()) {
            List<Integer> recordIds = new ArrayList<>(records.size());
            records.forEach(r -> recordIds.add(r.getId()));
            rs.deleteRecordsByIds(recordIds);
        }
    }

    public void resetJatsRendering(Collection<IRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        Collection<Integer> jatsIds = null;
        Collection<Integer> rmIds = null;

        for (IRecord record: records) {
            if (record.isJats()) {
                jatsIds = CollectionCommitter.addToNullableCollection(record.getId(), jatsIds, ArrayList::new);
            } else {
                rmIds = CollectionCommitter.addToNullableCollection(record.getId(), rmIds, ArrayList::new);
            }
        }
        if (jatsIds != null) {
            rs.deleteRecordsByIds(jatsIds);
        }
        if (rmIds != null) {
            rs.deleteRecordsByIds(rmIds, RenderingPlan.PDF_FOP.id());
        }
    }

    public void updateRendering(int planId, Collection<Integer> recordIds, boolean success) {
        updateRendering(planId, recordIds, true, false);
    }

    public void updateRendering(int planId, Collection<Integer> successfulRecordIds,
                                            Collection<Integer> failedRecordIds, boolean lastStage) {
        int trueCount = updateRendering(planId, successfulRecordIds, true, lastStage);
        int falseCount = updateRendering(planId, failedRecordIds, false, lastStage);
    }

    private int updateRendering(int planId, Collection<Integer> recordIds, boolean success, boolean lastStage) {
        if (recordIds.isEmpty()) {
            return 0;
        }
        if (DbUtils.exists(planId)) {
            rs.updateRenderings(planId, recordIds, success);
        }
        return lastStage ? recStorage.setRenderResults(recordIds, success, true) : 0;
    }

    private TinyRecordVO checkMetadataAndTranslations(RecordEntity re) {
        TinyRecordVO rvo = new TinyRecordVO(re);
        ICDSRMeta meta = re.getMetadata();
        rvo.initMetaFields(meta.getPubNumber(), meta.getStatus(), meta.getCochraneVersion(),
                meta.getHistoryNumber(), meta.isJats());
        return rvo;
    }

    private TinyRecordVO checkMetadataAndTranslations(int fullIssueNumber, RecordEntity re) {
        TinyRecordVO rvo = new TinyRecordVO(re);
        String cdNumber = re.getName();
        ICDSRMeta lastMeta = resultsStorage.findLatestMetadata(cdNumber, false);
        ICDSRMeta meta = re.getMetadata();
        if (meta == null) {
            meta = resultsStorage.findMetadataToIssue(fullIssueNumber, re.getName(), false);
            if (meta == null || meta.getPubNumber() > lastMeta.getPubNumber()) {
                LOG.warn(String.format("%s has %s metadata, but the latest one has %s", cdNumber, meta, lastMeta));
                return null;
            }
            rvo.initMetaFields(meta.getPubNumber(), meta.getStatus(), meta.getCochraneVersion(),
                               meta.getHistoryNumber(), meta.isJats());

            //rvo.initPublishDates(meta.getPublishedIssue(), null, meta.getCitationIssue(), null, null,
            //        meta.getSelfCitationIssue());
        }

        boolean previous = lastMeta.getPubNumber() > meta.getPubNumber();
        int recordNumber = RecordHelper.buildRecordNumberCdsr(cdNumber);
        List<DbRecordVO> list = previous ? rm.getTranslationHistory(recordNumber, meta.getHistoryNumber())
                        : rm.getLastTranslations(recordNumber);
        if (!list.isEmpty()) {
            String pubName = RevmanMetadataHelper.buildPubName(cdNumber, meta.getPubNumber());
            Set<String> languages = new HashSet<>();
            for (DbRecordVO tr: list) {
                if (!tr.isDeleted() && (!tr.isHistorical() || tr.getLabel().equals(pubName))) {
                    languages.add(tr.getLanguage());
                }
            }
            rvo.setLanguages(languages);
        }
        return rvo;
    }
}
