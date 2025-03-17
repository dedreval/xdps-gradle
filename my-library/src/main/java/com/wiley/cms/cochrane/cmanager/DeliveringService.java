package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.StatsUpdater;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.df.DfStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitymanager.RevmanSource;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.process.RenderingHelper;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.tes.util.Extensions;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;
import com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.CreatorXlsWithNewOrUpdated;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.ArrayUtils;

import static com.wiley.cms.cochrane.cmanager.MessageSender.MSG_PARAM_DATABASE;
/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 23.05.12
 */
@Stateless
@Local(IDeliveringService.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class DeliveringService implements IDeliveringService {
    private static final Logger LOG = Logger.getLogger(DeliveringService.class);

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "PreviousVersionLoading")
    private IPreviousVersionLoading previousVersionLoading;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recStorage;

    @EJB(beanName = "PublishService")
    private IPublishService pbService;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage dbStorage;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    private final IRepository rp = RepositoryFactory.getRepository();

    public void finishUpload(int issueId, int deliveryFileId, Collection<Record> records) {
        DeliveryFileEntity df = rs.getDeliveryFileEntity(deliveryFileId);
        if (df.getDb() == null) {
            LOG.error(String.format("Package %s has null Db. Cannot finish delivery.", df.getName()));
            return;
        }
        LOG.info(String.format("Finishing delivery %s has  been started. Records: %d", df.getName(), records.size()));
        finishUpload(issueId, new DeliveryFileVO(df), df.getDb().getTitle(), records, 0);
    }

    public void finishUpload(int issueId, int dfId, String dbName, int jobId) {
        DeliveryFileVO df = DfStorageFactory.getFactory().getInstance().getDfVO(dfId);
        if (!df.isEmpty()) {
            BaseType bt = BaseType.find(dbName).get();
            setCompletedStatus(bt, df, jobId);
            finishUpload(bt, issueId, df, jobId);
        }
    }

    public void finishUpload(BaseType bt, int issueId, DeliveryFileVO df, int jobId) {
        LOG.debug("try to last load content..");
        int dfId = df.getId();
        int dbId = df.getDbId();

        boolean cdsr = bt.isCDSR();
        boolean jats = cdsr && DeliveryFileEntity.isJats(df.getType());
        try {
            if (df.isLoadingFinishedSuccessful() && !jats && !DeliveryFileEntity.isWml3g(df.getType())) {
                if (cdsr) {
                    CreatorXlsWithNewOrUpdated.createXlsWithNewOrUpdated(dbId, issueId, bt.getId());
                }
            }
            if (df.isLoadingFinished()) {
                //int statusId;
                //if (cdsr || bt.isEditorial() || bt.isCCA()) {
                onEnd(bt, dbId, df, !jats && DeliveryFileEntity.isRevman(df.getType()), jats,
                            CmsUtils.isScheduledIssue(df.getIssue()), bt.isEditorial());
                //int statusId = df.getStatus();
                //}
                //else {
                //    Hooks.captureRecords(df.getId(), Hooks.UPLOAD_END_SUCCESS);
                //    statusId = correctStatus(df);
                //}
                logFinishingMessage(bt, dfId, df.getName(), df.getStatus(), df.getInterimStatus());
                LOG.debug("last load content finished ");
            }
        } catch (Exception e) {
            exceptionToLog(e, df, jobId);
        }
    }

    public void finishUpload(int issueId, DeliveryFileVO df, String dbName, Collection<Record> records, int jobId) {
        BaseType baseType = BaseType.find(dbName).get();
        try {
            synchronized (DeliveringService.class) {
                if (baseType.isCDSR()) {
                    notifyFailedRecords(previousVersionLoading.handlePreviousVersions(baseType, records, false), df);
                }
                loadContent(baseType, df, records);
            }
        } catch (Exception e) {
            exceptionToLog(e, df, jobId);
        }
    }

    private void notifyFailedRecords(Map<String, String> fails, DeliveryFileVO df) {
        if (fails != null && !fails.isEmpty()) {
            rm.setRecordStateByDeliveryPackage(RecordEntity.STATE_PROCESSING, RecordEntity.STATE_WR_ERROR,
                    df.getId(), fails.keySet());
            StringBuilder sb = new StringBuilder();
            for (String name : fails.keySet()) {
                MessageSender.addMessage(sb, name, fails.get(name));
            }
            sb.delete(sb.length() - 2, sb.length());
            if (sb.length() > 0) {
                MessageSender.sendFailedLoadPackageMessage(df.getName(), sb.toString(), df.getDbName(), null);
            }
        }
    }

    public void reloadContent(Integer dfId, Collection<IRecord> records, boolean cdsr, int jobId) {
        DeliveryFileVO df = rs.getDeliveryFileVO(dfId);
        if (records.isEmpty() || CmsUtils.isScheduledIssue(df.getIssue())) {
            return;
        }
        try {
            boolean jats = cdsr && DeliveryFileEntity.isJats(df.getType());
            boolean revman = !jats && cdsr && DeliveryFileEntity.isRevman(df.getType());
            BaseType baseType = BaseType.find(df.getDbName()).get();

            if (!revman && DeliveryFileEntity.isAriesSFTP(df.getType())) {
                notifyFailedRecords(previousVersionLoading.handlePreviousVersions(baseType, records, true), df);
            }
            loadContent(baseType, df, records, cdsr, revman, jats);

        }  catch (Exception e) {
            exceptionToLog(e, df, jobId);
        }
    }

    public void loadScheduledContent(RecordEntity re) {
        DeliveryFileVO df =  new DeliveryFileVO(re.getDeliveryFile());
        BaseType baseType = BaseType.find(df.getDbName()).get();
        boolean cdsr = baseType.isCDSR();
        IRecord record = new Record(re);
        previousVersionLoading.handlePreviousVersions(baseType, record, baseType.hasSFLogging());
        loadContent(baseType, df, Collections.singletonList(record), cdsr, false,
                cdsr && DeliveryFileEntity.isJats(df.getType()));
    }

    public void loadContent(DeliveryFileVO df, Collection<Record> records, boolean cdsr, boolean dashboard, int jobId) {
        if (records.isEmpty() || CmsUtils.isScheduledIssue(df.getIssue())) {
            return;
        }
        BaseType baseType = BaseType.find(df.getDbName()).get();
        try {
            if (cdsr || baseType.isEditorial() || baseType.isCCA()) {
                notifyFailedRecords(previousVersionLoading.handlePreviousVersions(baseType, records, dashboard), df);
            }
            boolean jats = cdsr && DeliveryFileEntity.isJats(df.getType());
            boolean revman = !jats && cdsr && DeliveryFileEntity.isRevman(df.getType());
            loadContent(baseType, df, records, cdsr, revman, jats);

        } catch (Exception e) {
            exceptionToLog(e, df, jobId);
        }
    }

    private void loadContent(BaseType baseType, DeliveryFileVO df, Collection<? extends IRecord> records, boolean cdsr,
                             boolean revman, boolean jats) {
        LOG.debug("try to load content...");
        int dbId = df.getDbId();
        loadContentToEntire(baseType, dbId, records, df, cdsr, revman, jats);
        LOG.debug("load content finished");
    }

    private void loadContent(BaseType baseType, DeliveryFileVO df, Collection<? extends IRecord> records) {
        String dfName = df.getName();
        boolean cdsr = baseType.isCDSR();
        boolean jats = cdsr && DeliveryFileEntity.isJats(df.getType());
        boolean revman = !jats && cdsr && DeliveryFileEntity.isRevman(df.getType());

        if (!records.isEmpty()) {
            loadContent(baseType, df, records, cdsr, revman, jats);
        }
        if (df.isLoadingFinished()) {
            LOG.debug("try to last load content...");
            int dbId = df.getDbId();
            int dfId = df.getId();
            if (cdsr) {
                onEnd(baseType, dbId, df, revman, jats, false, false);
            }
            logFinishingMessage(baseType, dfId, dfName, df.getStatus(), df.getInterimStatus());
            LOG.debug("last load content finished");
        }
    }

    private void onEnd(BaseType bt, int dbId, DeliveryFileVO df, boolean rm, boolean jats, boolean spd, boolean ed) {
        int dfId = df.getId();
        int dfType = df.getType();
        String dfName = df.getName();
        boolean topicChanged = rm && copyRevmanTopicsToEntire(df, bt.getId())
                || jats && copyJatsTopicsToEntire(df.getIssue(), rp, flowLogger.getRecordCache());
        boolean ariesSFTP = DeliveryFileEntity.hasAries(dfType);
        if (ariesSFTP && DeliveryFileEntity.isAriesAcknowledge(dfType)) {
            return;
        }
        boolean aut = ariesSFTP || DeliveryPackage.isAut(dfName);
        //boolean autCommon = aut
        //        && (ariesSFTP || CochraneCMSPropertyNames.canArchieAut() || DeliveryPackage.isAutReprocess(dfName));
        boolean mesh = !ariesSFTP && !bt.isCentral() && DeliveryPackage.isMeshterm(dfName);
        boolean pu = !mesh && !ariesSFTP && !bt.isCentral() && DeliveryPackage.isPropertyUpdate(dfName);
        boolean puMl3g = pu && DeliveryPackage.isPropertyUpdateMl3g(dfName); 
        boolean ml3g = !jats && DeliveryFileEntity.isWml3g(df.getType()); // mesh || pu || ed

        if (!aut && ed && DeliveryPackage.isPreQA(ml3g, dfName)) {
            aut = true;
        }
        boolean dashboard = bt.hasSFLogging() && (ariesSFTP || jats || (aut && !pu && !mesh) || bt.isCentral());

        Collection<String> cdNumbers = new HashSet<>();
        if (aut && CochraneCMSPropertyNames.isPublishAfterUpload()) {
            if (checkOnEnd(bt, df, jats, ml3g, true, dashboard, cdNumbers)
                || (df.getStatus() != IDeliveryFileStatus.STATUS_INVALID_CONTENT && topicChanged)) {

                LOG.debug(String.format("try %s [%d] publishing... ", dfName, dfId));

                df.setStatus(IDeliveryFileStatus.STATUS_PUBLISHING_STARTED);
                df.setInterimStatus(IDeliveryFileStatus.STATUS_PUBLISHING_STARTED);
                rs.updateDeliveryFileStatus(dfId, df.getStatus(), DeliveryFileVO.FINAL_FAILED_STATES);
                rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_STARTED, true);
                if (dashboard && !bt.isCentral()) {
                    flowLogger.onProductsPublishingStarted(cdNumbers, true, spd);
                }
                pbService.publishWhenReady(bt, df, mesh, pu, !puMl3g, ariesSFTP);
            }
        } else {
            checkOnEnd(bt, df, jats, ml3g, false, false, cdNumbers);
        }
    }

    private boolean checkOnEnd(BaseType bt, DeliveryFileVO df, boolean jats, boolean ml3g, boolean aut,
                               boolean dashboard, Collection<String> cdNumbers) {
        int dfId = df.getId();
        LOG.debug(String.format("%s is checking ...", df.getName()));
        if (bt.isCentral()) {
            return df.getStatus() != IDeliveryFileStatus.STATUS_VALIDATION_FAILED
                    || rs.getDeletedRecordsCount(bt, dfId) > 0;
        }

        DeliveryFileEntity de = rs.getDeliveryFileEntity(dfId);
        List<RecordEntity> records = recStorage.getRecordsByDFile(de, false);
        Map<String, ErrorInfo.Type> failures = Collections.emptyMap();
        boolean spd = CmsUtils.isScheduledIssue(df.getIssue());

        int successCount = 0;
        for (RecordEntity rec : records) {
            String cdNumber = rec.getName();
            if (!rec.isQasSuccessful()) {
                failures = addFailure(cdNumber, ErrorInfo.Type.QA_STAGE, failures);
            } else if (!rec.isRenderingSuccessful()) {
                failures = addFailure(cdNumber, ErrorInfo.Type.RENDERING_STAGE, failures);
            } else if (!jats && !ml3g && (rec.getRecordPublishEntity() == null    // just an old approach supporting
                    || rec.getRecordPublishEntity().getState() == RecordPublishEntity.CONVERSION_FAILED)) {
                failures = addFailure(cdNumber, ErrorInfo.Type.ML3G_CONVERSION, failures);
            } else if ((aut && bt.isCDSR() && !rec.isProcessed()) || rec.isProcessedError()) {
                failures = addFailure(cdNumber, ErrorInfo.Type.SYSTEM, failures);
            } else {
                successCount++;
                cdNumbers.add(cdNumber);
                if (dashboard) {
                    flowLogger.onProductSaved(cdNumber, rec.getMetadata().getVersion().isNewDoi(), dashboard, spd);
                }
            }
        }

        if (!failures.isEmpty() && aut) {
            rm.setRecordStateByDeliveryPackage(RecordEntity.STATE_PROCESSING, RecordEntity.STATE_WR_ERROR, dfId,
                    failures.keySet());
        }

        correctStatus(successCount, failures.size(), 0, df);

        Hooks.captureRecords(df.getDbId(), cdNumbers, Hooks.UPLOAD_END_SUCCESS);
        Hooks.captureRecords(df.getDbId(), failures.keySet(), Hooks.UPLOAD_END_FAILURE);

        if (!aut) {
            rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_PROCESSING,
                    RecordEntity.STATE_UNDEFINED, dfId);
            rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_WR_ERROR, RecordEntity.STATE_UNDEFINED, dfId);
            flowLogger.onFlowCompleted(cdNumbers);
            flowLogger.onFlowCompleted(failures.keySet());
        }
        LOG.debug(String.format("%s checked - failed: %d from %d", df.getName(), failures.size(), records.size()));
        return successCount > 0;
    }

    private Map<String, ErrorInfo.Type> addFailure(String cdNumber, ErrorInfo.Type errType,
                                                   Map<String, ErrorInfo.Type> cdNumbers) {
        Map<String, ErrorInfo.Type> ret = cdNumbers.isEmpty() ? new HashMap<>() : cdNumbers;
        ret.put(cdNumber, errType);
        return ret;
    }

    private void correctStatus(int successCount, int failedCount, int delCount, DeliveryFileVO df) {
        if (successCount == 0 && df.getStatus() == IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS) {
            if (failedCount > 0) {
                df.setStatus(IDeliveryFileStatus.STATUS_RND_FAILED);
                rs.setDeliveryFileStatus(df.getId(), df.getStatus(), false);

            } else if (delCount == 0) {
                df.setStatus(IDeliveryFileStatus.STATUS_PACKAGE_LOADED);
                rs.setDeliveryFileStatus(df.getId(), df.getStatus(), false);
            }
        }
    }

    private void loadContentToEntire(BaseType baseType, int dbId, Collection<? extends IRecord> records,
                                     DeliveryFileVO df, boolean cdsr, boolean revman, boolean jats) {
        String dbName = baseType.getId();
        LOG.debug(String.format("Loading content to entire DB %s started", dbName));

        DatabaseEntity database = rs.getDatabaseEntity(dbName);
        String revmanDirPath = FilePathBuilder.getPathToEntireRevman();
        Map<String, String> fails = null;
        Set<String> set = new HashSet<>();
        records.forEach(r -> set.add(r.getName()));

        boolean central = !cdsr && baseType.isCentral();
        int dfType = df.getType();
        if (!set.isEmpty()) {
            dbStorage.getRecordExists(database.getId(), df.getFullIssueNumber(), set);
        }
        Integer issueId = df.getIssue();
        boolean noWml21 = jats || DeliveryFileEntity.isWml3g(df.getType());
        Boolean hasJatsReview = jats && !DeliveryFileEntity.onlyTranslation(dfType);
        Boolean dashBoard = jats && (DeliveryFileEntity.hasAries(df.getType())
                || DeliveryPackage.isAut(df.getName())) ? Boolean.TRUE : (jats ? Boolean.FALSE : null);
        for (IRecord rec : records) {
            if (!rec.isCompleted() || !rec.isSuccessful()) {
                continue;
            }
            if (set.contains(rec.getName())) {
                makeBackup(rec, df, dbName, revmanDirPath, baseType, cdsr, central);
            }
            if (revman) {
                // can be removed after a full migration to JATS
                copyRevmanToEntire(issueId, df.getId(), rec, dashBoard, rm);
                RepositoryUtils.deleteDirQuietly(ContentLocation.ENTIRE.getPathToJatsSrcDir(
                        null, dbName, null, null, rec.getName()), rp);
            }
            if (jats) {
                copyJatsToEntire(issueId, dbName, df.getName(), rec, hasJatsReview, dashBoard, rm);
                // can be removed after a full migration to JATS
                if (rec.isJats()) {
                    removeNonJatsContent(issueId, dbName, rec.getName(), ContentLocation.ISSUE);
                    removeNonJatsContent(null, dbName, rec.getName(), ContentLocation.ENTIRE);
                }
            }
            if (!rec.isDeleted()) {
                copyFromCurrentIssueToEntire(baseType, rec, issueId, df, noWml21, jats, dashBoard);
            }
            if (!rec.isSuccessful()) {
                if (fails == null) {
                    fails = new HashMap<>();
                }
                fails.put(rec.getName(), rec.getMessages());
                rec.setSuccessful(true, "");
            }
        }

        StatsUpdater.onUpdate(dbName);
        notifyFailedRecords(fails, df);
        LOG.debug("Loading content to entire DB finished");
    }

    private void removeNonJatsContent(Integer issueId, String dbName, String cdNumber, ContentLocation cl) {
        RepositoryUtils.deleteDirQuietly(cl.getPathToPdfTex(issueId, dbName, null, cdNumber), rp);
        RepositoryUtils.deleteDirQuietly(cl.getPathToHtml(issueId, dbName, null, cdNumber, false), rp);
    }

    private void copyTranslationsToEntire(int issueId, int dfId, IRecord rec, RevmanSource revman, Boolean dashBoard) {
        List<DbRecordVO> changed = rm.moveTranslationsToEntire(rec.getName(), dfId, rec.getHistoryNumber(),
                revman.revmanFile != null && !rec.isUnchanged());
        if (!changed.isEmpty()) {
            Set<String> removed = TranslatedAbstractsHelper.updateAbstractsToEntire(
                    issueId, dfId, rec, changed, revman, rp);
            if (removed != null && dashBoard != null) {
                flowLogger.onTranslationDeleted(rec.getName(), removed, dashBoard);
            }
        }
    }

    private void copyFromCurrentIssueToEntire(BaseType baseType, IRecord rec, int issueId, int dbId, int dfId,
                                              boolean jats, boolean setApproved) throws Exception {
        String dbName = baseType.getId();
        String cdNumber = rec.getName();
        boolean cca = baseType.isCCA();
        if (cca) {
            copyImagesFromCurrentIssueToEntire(FilePathBuilder.getPathToSrcDir(FilePathBuilder.getPathToIssuePackage(
                issueId, dbName, dfId), cdNumber),
                    FilePathBuilder.getPathToEntireSrcRecord(dbName, cdNumber, false), true);
        } else if (!jats && baseType.isEditorial()) {
            copyImagesFromCurrentIssueToEntire(FilePathBuilder.getPathToSrcDir(FilePathBuilder.getPathToIssuePackage(
                issueId, dbName, dfId), cdNumber),
                    FilePathBuilder.getPathToEntireSrcRecord(dbName, cdNumber, false), false);
        }
        if (baseType.canPdfFopConvert()) {
            RecordHelper.copyPdfFOPToEntire(issueId, dbName, cdNumber, rp);
        }
        if (!cca && baseType.canMl3gConvert()) {
            RecordHelper.copyWML3GToEntire(issueId, dbName, cdNumber, baseType, true, rp);
        }
        dbStorage.updateRecord(dbId, cdNumber, setApproved);
    }

    private void copyImagesFromCurrentIssueToEntire(String imageDirPath, String entireSrcPath, boolean withRecord)
            throws Exception {
        String pathTo = entireSrcPath.replace(Extensions.XML, "");
        rp.deleteDir(pathTo);
        if (rp.isFileExistsQuiet(entireSrcPath)) {
            rp.deleteFile(entireSrcPath);
        }
        if (withRecord) {
            InputStream is = null;
            try {
                is = rp.getFile(imageDirPath + Extensions.XML);
                rp.putFile(entireSrcPath, is);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        File[] files = rp.getFilesFromDir(imageDirPath);
        if (ArrayUtils.isNotEmpty(files)) {
            CmsUtils.writeDir(imageDirPath, pathTo);
        }
    }

    private void copyFromCurrentIssueToEntire(BaseType baseType, IRecord rec, Integer issueId, DeliveryFileVO df,
                                              boolean noWml21, boolean jats, Boolean dashboard) {
        InputStream is = null;
        String dbName = baseType.getId();
        try {
            if (noWml21) {
                copyFromCurrentIssueToEntire(baseType, rec, issueId, df.getDbId(), df.getId(), jats, df.isAut());
                return;
            }
            String filePath = rec.getRecordPath();
            is = rp.getFile(filePath);
            String pathToSourceEntire = FilePathCreator.getFilePathToSourceEntire(dbName, rec.getName());
            rp.putFile(pathToSourceEntire, is);
            String dirPath = filePath.replace(Extensions.XML, "");
            File[] files = rp.getFilesFromDir(dirPath);
            String pathTo, pathFrom;
            pathTo = pathToSourceEntire.replace(Extensions.XML, "");
            rp.deleteDir(pathTo);
            if (ArrayUtils.isNotEmpty(files)) {
                CmsUtils.writeDir(dirPath, pathTo);
            }
            if (baseType.canHtmlConvert()) {
                pathTo = FilePathCreator.getRenderedFilePathEntire(pathToSourceEntire, RenderingPlan.HTML, dbName,
                        rec.getName());
                pathFrom = FilePathCreator.getRenderedDirPath(filePath, RenderingPlan.HTML);
                rp.deleteDir(pathTo);
                CmsUtils.writeDir(pathFrom, pathTo);
            }
            if (baseType.canPdfFopConvert()) {
                pathTo = FilePathCreator.getRenderedFilePathEntire(pathToSourceEntire, RenderingPlan.PDF_FOP, dbName,
                        rec.getName());
                pathFrom = FilePathCreator.getRenderedDirPath(filePath, RenderingPlan.PDF_FOP);
                rp.deleteDir(pathTo);
                CmsUtils.writeDir(pathFrom, pathTo);
            }
            if (baseType.canMl3gConvert()) {
                RecordHelper.copyWML3GToEntire(issueId, dbName, rec.getName(), baseType, true, rp);
            }
            dbStorage.updateRecord(df.getDbId(), rec.getName(), df.isAut());

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            rec.setSuccessful(false, e.getMessage());
            if (dashboard != null) {
                flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, null, rec.getName(),
                    "Record copying from current issue to entire: " + e.getMessage(), true,
                        dashboard, CmsUtils.isScheduledIssue(issueId));
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private boolean copyJatsTopicsToEntire(Integer issueId, IRepository rp, IRecordCache cache) {
        String topicsEntireRootPath = FilePathCreator.getDirPathForRevmanEntire();
        String root = rp.getRepositoryPlace();
        Iterator<String> groups = cache.getCRGGroupCodes();
        boolean editorialChanged = false;
        try {
            while (groups.hasNext()) {
                String groupName = groups.next();
                String issuePath = FilePathBuilder.getPathToTopics(issueId, groupName);
                String entirePath = FilePathBuilder.getPathToTopics(groupName);
                editorialChanged = copyJatsTopicToEntire(groupName, issuePath, entirePath,
                        topicsEntireRootPath, root, !editorialChanged, rp);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        return editorialChanged;
    }

    private boolean copyJatsTopicToEntire(String groupName, String pathFrom, String pathTo,
        String topicsEntireRootPath, String root, boolean checkEditorial, IRepository rp) throws Exception {

        boolean ret = checkEditorial;
        synchronized (RevmanPackage.class) {
            if (!rp.isFileExists(pathFrom)) {
                return ret;
            }
            long dtTo = rp.isFileExists(pathTo) ? RepositoryUtils.getRealFile(root, pathTo).lastModified() : 0;
            long dtFrom = RepositoryUtils.getRealFile(root, pathFrom).lastModified();
            if (dtTo < dtFrom) {
                if (checkEditorial) {
                    ret = !RevmanPackage.checkEditorial(groupName, pathTo, topicsEntireRootPath, rp);
                }
                rp.deleteFile(pathTo);
                rp.putFile(pathTo, rp.getFile(pathFrom));
            }
        }
        return ret;
    }

    private boolean copyRevmanTopicsToEntire(DeliveryFileVO df, String dbName) {
        String revmanDir = RevmanPackage.getRevmanFolder(String.valueOf(df.getId()), df.getIssue(), dbName);
        try {
            File[] groups = rp.getFilesFromDir(revmanDir);
            if (groups != null && groups.length > 0) {
                synchronized (RevmanPackage.class) {
                    return copyRevmanTopicsToEntire(groups, revmanDir, FilePathCreator.getDirPathForRevmanEntire());
                }
            }
        } catch (Exception e) {
            LOG.error("Error during copy revman topics to entire", e);
        }
        return false;
    }

    private boolean copyRevmanTopicsToEntire(File[] groups, String revmanDir, String revmanDirPath) throws Exception {
        boolean checkEditorial = true;
        boolean ret = false;
        for (File group: groups) {
            if (!group.isDirectory()) {
                continue;
            }
            String groupName = group.getName();
            String topicPath = FilePathCreator.getRevmanTopicSource(groupName);
            String topicFrom = revmanDir + FilePathCreator.SEPARATOR + topicPath;
            if (!rp.isFileExists(topicFrom)) {
                continue;
            }
            ret = true;
            String topicTo = revmanDirPath + topicPath;
            if (rp.isFileExists(topicTo)) {

                String root = rp.getRepositoryPlace();
                long dtFrom = RepositoryUtils.getRealFile(root, topicFrom).lastModified();
                long dtTo = RepositoryUtils.getRealFile(root, topicTo).lastModified();
                if (dtTo > dtFrom) {
                    LOG.warn(String.format("Date of %s is newer than %s", dtTo, dtFrom));
                    continue;
                }

                if (checkEditorial) {
                    checkEditorial = !RevmanPackage.checkEditorial(groupName, topicTo, revmanDirPath, rp);
                }
                rp.deleteFile(topicTo);
            }
            rp.putFile(topicTo, rp.getFile(topicFrom), true);
            rp.deleteFile(topicFrom);
        }
        return ret;
    }

    private void copyJatsToEntire(Integer issueId, String dbName, String dfName, IRecord rec,
                                  boolean hasReview, Boolean dashboard, IRecordManager rm) {
        String cdNumber = rec.getName();
        boolean updatedSourceExists = false;
        Integer dfId = rec.getDeliveryFileId();
        try {
            if (hasReview) {
                String pathFrom = FilePathBuilder.JATS.getPathToSrcDir(issueId, dfId, cdNumber);
                updatedSourceExists = rp.isFileExists(pathFrom);
                if (updatedSourceExists) {
                    String pathTo = FilePathBuilder.JATS.getPathToEntireDir(dbName, cdNumber);
                    rp.deleteDir(pathTo);
                    CmsUtils.writeDir(pathFrom, pathTo, true, rp);
                }
            }

            List<DbRecordVO> changedTa = rm.moveTranslationsToEntire(rec.getName(), dfId, rec.getHistoryNumber(),
                    updatedSourceExists && !rec.isUnchanged());
            if (!changedTa.isEmpty()) {
                Set<String> removed  = TranslatedAbstractsHelper.updateJatsAbstractsToEntire(
                        issueId, dfId, rec.getName(), changedTa, rp);
                if (removed != null && dashboard != null) {
                    flowLogger.onTranslationDeleted(cdNumber, removed, dashboard);
                }
            }

        } catch (Exception e) {
            LOG.error("Error during copy jats to entire", e);
            rec.setSuccessful(false, e.getMessage());
            if (dashboard != null) {
                flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, dfName, cdNumber,
                    "Record copying to entire: " + e.getMessage(), true, dashboard, CmsUtils.isScheduledIssue(issueId));
            }
        }
    }

    private void copyRevmanToEntire(int issueId, int dfId, IRecord rec, Boolean dashBoard, IRecordManager rm) {
        RevmanSource ret = null;
        try {
            String group = rec.getGroupSid();
            if (group == null || group.isEmpty()) {
                LOG.error("Revman group is null for " + rec.getName());
                rec.setSuccessful(false, "revman group is null");
                return;
            }
            String recPath = rec.getName();

            ret = RecordHelper.findInitialSourcesForRevmanPackage(recPath,
                new File(rp.getRealFilePath(FilePathBuilder.RM.getPathToRevmanGroup(issueId, dfId, group))));

            if (ret.revmanFile != null) {

                String metaPath = FilePathBuilder.buildMetadataRecordName(recPath);
                recPath += Extensions.XML;

                String pathRevmanFrom = FilePathBuilder.RM.getPathToRevmanSrc(issueId, group);
                String pathRevmanMetaFrom = pathRevmanFrom + metaPath;
                pathRevmanFrom += recPath;

                String pathTo = FilePathBuilder.getPathToEntireRevmanSrc(group);

                copyFile(pathRevmanFrom, pathTo, recPath);
                copyFile(pathRevmanMetaFrom, pathTo, metaPath);
            }

        } catch (Exception e) {
            LOG.error("Error during copy revman to entire", e);
            rec.setSuccessful(false, e.getMessage());
        }
        if (ret != null) {
            copyTranslationsToEntire(issueId, dfId, rec, ret, dashBoard);
        }
    }

    private void copyFile(String pathFrom, String pathTo, String recName) throws Exception {

        if (rp.isFileExists(pathFrom)) {

            String path = pathTo + recName;
            if (rp.isFileExists(path)) {
                rp.deleteFile(path);
            }
            rp.putFile(path, rp.getFile(pathFrom));

        } else {
            LOG.error("this file is not exists in: " + pathFrom);
        }
    }

    private void makeBackup(IRecord rec, DeliveryFileVO df, String dbName, String revmanDirPath, BaseType baseType,
                            boolean cdsr, boolean central) {
        String recName = rec.getName();
        String pathTo;
        String pathFrom;
        int issueId = df.getIssue();
        String prefixForCopy = FilePathBuilder.getPathToBackup(issueId, dbName);
        String srcPath = FilePathBuilder.getPathToEntireSrcRecord(dbName, recName, central);
        String srcPathTo = FilePathBuilder.getPathToBackupSrcRecord(issueId, dbName, recName, central);
        try {
            if (cdsr) {
                pathTo = FilePathBuilder.JATS.getPathToBackupDir(issueId, dbName, recName);
                if (!rp.isFileExists(pathTo)) {
                    pathFrom = FilePathBuilder.JATS.getPathToEntireDir(dbName, recName);
                    CmsUtils.writeDir(pathFrom, pathTo, true, rp);
                    backupRevman(rec, df, revmanDirPath, prefixForCopy);
                    TranslatedAbstractsHelper.makeBackup(issueId, recName, rp);
                } else {
                    srcPathTo = null;
                }
            }
            if (srcPathTo == null || rp.isFileExists(srcPathTo)) {
                return;
            }

            if (rp.isFileExists(srcPath)) {
                rp.putFile(srcPathTo, rp.getFile(srcPath));
            }
            if (baseType.canMl3gConvert()) {
                String ml3gPathTo = FilePathBuilder.ML3G.getPathToBackupMl3gRecord(issueId, dbName, recName, central);
                if (rp.isFileExists(ml3gPathTo)) {
                    // a backup was already done
                    return;
                }
                backupWml3gContent(dbName, issueId, recName, ml3gPathTo, baseType);
            }
            CmsUtils.writeDir(srcPath.replace(Extensions.XML, ""), srcPathTo.replace(Extensions.XML, ""), true, rp);

            if (baseType.canPdfFopConvert()) {
                pathTo = FilePathCreator.getRenderedDirPathCopy(issueId, dbName, RenderingPlan.PDF_FOP) + "/" + recName;
                pathFrom = FilePathCreator.getRenderedFilePathEntire(srcPath, RenderingPlan.PDF_FOP, dbName, recName);
                CmsUtils.writeDir(pathFrom, pathTo, true, rp);
            }

            if (baseType.canHtmlConvert()) {
                pathTo = FilePathBuilder.getPathToBackupHtml(issueId, dbName, recName, central);
                pathFrom = FilePathBuilder.getPathToEntireHtml(dbName, recName, central);
                CmsUtils.writeDir(pathFrom, pathTo, true, rp);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());

        }
    }

    private void backupWml3gContent(String dbName, int issueId, String recName, String destPath, BaseType baseType) {
        String srcUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        String destUri = destPath;
        StringBuilder errs = null;
        InputStream is;
        try {
            is = rp.getFile(srcUri);
            rp.putFile(destUri, is);

        } catch (Exception e) {
            errs = new StringBuilder(String.format(RecordHelper.FAILED_COPY_WML3G_MSG_TEMP, Constants.XML_STR, srcUri,
                    destUri, e));
        }
        if (baseType.hasStandaloneWml3g()) {
            srcUri = FilePathCreator.getFilePathForEntireMl3gAssets(dbName, recName);
            destUri = destPath.replace(Extensions.XML, Extensions.ASSETS);
            try {
                is = rp.getFile(srcUri);
                rp.putFile(destUri, is);

            } catch (Exception e) {
                errs = (errs == null ? new StringBuilder() : errs).append(String.format(
                        RecordHelper.FAILED_COPY_WML3G_MSG_TEMP, Constants.ASSETS_STR, srcUri, destUri, e));
            }
        }
        if (errs != null) {
            LOG.error(errs);
        }
    }

    private void backupRevman(IRecord rec, DeliveryFileVO df, String revmanDirPath, String prefixForCopy)
            throws Exception {
        if (DeliveryFileEntity.isWml3g(df.getType())) {
            return;
        }
        String recName = rec.getName();
        String group = rec.getGroupSid();
        if (group == null || group.isEmpty()) {
            LOG.error("Revman group is  null for " + rec.getName());
            return;
        }

        String recPath = FilePathCreator.SEPARATOR + recName;
        String metadataPath = FilePathBuilder.buildMetadataRecordName(recPath);
        recPath = recPath + Extensions.XML;

        String pathFrom = FilePathCreator.getDirForRevmanReviews(revmanDirPath + group);
        String pathMetadataFrom = pathFrom + metadataPath;
        String pathRecordFrom = pathFrom + recPath;
        String pathTo = FilePathCreator.getDirForRevmanReviews(prefixForCopy + FilePathCreator.REVMAN_DIR + group);

        if (rp.isFileExists(pathRecordFrom)) {
            InputStream is = rp.getFile(pathRecordFrom);
            rp.putFile(pathTo + recPath, is, true);
        }
        if (rp.isFileExists(pathMetadataFrom)) {
            InputStream is = rp.getFile(pathMetadataFrom);
            rp.putFile(pathTo + metadataPath, is, true);
        }
    }

    private void logFinishingMessage(BaseType baseType, int dfId, String dfName, int statusId, int secondStatusId) {

        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MessageSender.MSG_PARAM_DELIVERY_FILE, dfName);
        String txt;
        if (DeliveryFileVO.SUCCESS_STATES.contains(statusId)) {
            txt = CochraneCMSPropertyNames.getLogCommentLoadingSuccessfulMsg();
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_FINISHED, notifyMessage);

        } else if (DeliveryFileVO.PARTIALLY_SUCCESS_STATES.contains(statusId)) {
            txt = CochraneCMSPropertyNames.getLogCommentLoadingSomeErrorsMsg();
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_FINISHED, notifyMessage);

        } else {
            txt = CochraneCMSPropertyNames.getLogCommentLoadingSomeErrorsMsg();
            if (statusId == IDeliveryFileStatus.STATUS_INVALID_CONTENT) {
                notifyMessage.put(MessageSender.MSG_PARAM_DELIVERY_FILE, dfName
                        + " - no any workable records in the package, content is invalid");
            }
            notifyMessage.put(MSG_PARAM_DATABASE, baseType.getShortName());
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_FAILED, notifyMessage);
        }

        String logName = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

        flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.FILE,
                ILogEvent.LOADING_COMPLETED, dfId, dfName, logName, txt);

        if (statusId == IDeliveryFileStatus.STATUS_PUBLISHING_STARTED
                || secondStatusId == IDeliveryFileStatus.STATUS_PUBLISHING_STARTED) {

            flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.FILE,
                    ILogEvent.PUBLISH, dfId, dfName, logName, "");

        } else if (statusId == IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS) {
            rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_RND_SUCCESS_AND_FULL_FINISH, false);

        } else {
            rs.setDeliveryFileStatus(dfId, statusId, false);
        }
        LOG.debug(txt + "  " + dfName);
    }

    private void exceptionToLog(Exception e, DeliveryFileVO deliveryFile, int jobId) {
        LOG.error(e, e);
        flowLogger.getActivityLog().error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.ACCEPT_RND_RESULTS_FAILED,
            deliveryFile.getId(), deliveryFile.getName(),
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME), "jobId=" + jobId);
    }

    private void setCompletedStatus(BaseType bt, DeliveryFileVO df, int processId) {
        int completedState;
        int dfId = df.getId();
        //long allCount = recStorage.getCountRenderingByDfId(dfId);   // QA success + QA not completed
        long allCount = recStorage.getRecordCountByDf(dfId);
        long successfulCount = recStorage.getCountRenderingSuccessfulByDfId(dfId);

        if (successfulCount == allCount) {
            completedState = successfulCount == 0 ? df.getStatus() : IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS;
            LOG.debug(String.format("all processed=%d, successfulCount=%d", allCount, successfulCount));

        } else {
            long allCompletedCount = recStorage.getCountRenderingCompletedByDfID(dfId);
            LOG.info(String.format("processing completed: %d from %d, processId=%d, package %s[%d]",
                    allCompletedCount, allCount, processId, df.getName(), dfId));
            completedState = RenderingHelper.defineRenderingCompletedStatus(processId, dfId, df.getName(), allCount,
                    recStorage.getCountRenderingFailedByDfId(dfId), bt.canPdfFopConvert() || bt.canHtmlConvert());
        }
        if (successfulCount > 0) {
            DbStorageFactory.getFactory().getInstance().updateRenderedRecordCount(df.getDbId());
        }
        df.setStatus(completedState);
    }
}
