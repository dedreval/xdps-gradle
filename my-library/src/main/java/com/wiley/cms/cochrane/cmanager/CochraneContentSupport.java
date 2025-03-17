package com.wiley.cms.cochrane.cmanager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;
import com.wiley.cms.cochrane.archiver.Cleaner;
import com.wiley.cms.cochrane.cmanager.central.IPackageDownloader;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesConnectionManager;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesImportFile;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.EntireRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.PackageUploadHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
@Local(ICochraneContentSupport.class)
@Singleton
@Startup
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CochraneContentSupport extends JMXHolder implements ICochraneContentSupport, CochraneContentSupportMXBean {
    private static final int CAPACITY = 40;

    private static final Logger LOG = Logger.getLogger(CochraneContentSupport.class);

    @EJB(beanName = "PackageDownloader")
    private IPackageDownloader pd;

    @EJB(beanName = "ConverterAdapter")
    private IConverterAdapter converter;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recs;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    @EJB(beanName = "VersionManager")
    private IVersionManager vm;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    @EJB(beanName = "ContentManager")
    private IContentManager cm;

    private final IRepository rp = RepositoryFactory.getRepository();
    private String systemUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

    public CochraneContentSupport() {
        resetPrefix();
    }

    @PostConstruct
    public void start() {
        registerInJMX();
    }

    @PreDestroy
    public void stop() {
        unregisterFromJMX();
    }

    public void reloadComponentFactories() {
        CochraneCMSBeans.reload();
    }

    public String cleanRepository(int actualIssueLimit, int actualMonthsLimit) {
        Cleaner cleaner = new Cleaner();
        Cleaner.Stats stats = null;
        String ret = "done.";
        try {
            stats = new Cleaner.Stats() {
                @Override
                public int getLastIssuesLimit() {
                    return actualIssueLimit;
                }

                @Override
                public int getExpirationMonths() {
                    return actualMonthsLimit;
                }
            };
            cleaner.cleanOldData(stats);

        } catch (Throwable e) {
            LOG.error(e);
            ret = "error: " + e.getMessage();

        } finally {
            if (stats != null) {
                ret += ("\n" + stats);
                stats.clear();
            }
        }
        return ret;
    }

    @Override
    public String completeFlowLogEvents() {
        SFEvent[] ret = flowLogger.completeFlowLogEvents();
        return ret.length > 0 ? String.format("%d Snow Flake evens are acknowledged and completed", ret.length)
                : "no new Snow Flake events to be completed";
    }

    @Override
    public String reproduceFlowLogEvent(Long flowLogId) {
        if (flowLogger.reproduceFlowLogEvent(flowLogId)) {
            return String.format("[%d] has been reproduced to kafka", flowLogId);
        }
        return String.format("[%d] has not been found", flowLogId);
    }

    @Override
    public String reproduceFlowLogEvents() {
        return String.format("%d flow logs were reproduced", flowLogger.reproduceFlowLogEvents());
    }

    public String reprocessWhenReady(int issueNumber, String cdnumberListStr, boolean upload) throws Exception {

        int issueId = rs.findIssue(CmsUtils.getYearByIssueNumber(issueNumber),
                        CmsUtils.getIssueByIssueNumber(issueNumber));
        String dbName = BaseType.getCDSR().get().getId();

        StringBuilder ret = new StringBuilder();
        Set<Integer> revmanIds = new HashSet<>();
        Set<Integer> jatsIds = new HashSet<>();

        String[] cdnumberList = splitNames(cdnumberListStr);

        IRecordCache cache = CochraneCMSPropertyNames.lookupRecordCache();
        boolean spd = CmsUtils.isScheduledIssue(issueId);
        for (String cdnumber: cdnumberList) {

            String cdNumber = splitCDNumber(cdnumber);
            RecordEntity re = rs.getRecord(issueId, dbName, cdNumber);
            if (re == null) {
                ret.append("cannot find ").append(cdnumber).append("\n");
                continue;

            }
            RecordMetadataEntity rme = re.getMetadata();
            if (rme != null && rme.getVersion().isVersionFinal()) {
                ret.append("cannot re-process successfully uploaded record ").append(cdnumber).append("\n");
                continue;
            }
            cache.removeRecord(cdNumber, spd);
            if (rme != null && rme.isJats()) {
                jatsIds.add(re.getId());
            } else {
                revmanIds.add(re.getId());
            }
        }
        if (!revmanIds.isEmpty()) {
            ret.append(pd.exportCDSRWhenReady(issueId, revmanIds, upload, false)).append("\n");
        }
        if (!jatsIds.isEmpty()) {
            ret.append(pd.exportCDSRWhenReady(issueId, jatsIds, upload, false));
        }
        return ret.toString();
    }

    @Lock(LockType.WRITE)
    public String downloadFromLocal(String path) {
        return pd.downloadFromLocal(path.trim());
    }

    @Lock(LockType.WRITE)
    public String downloadCentralToLocal(int issueNumber, String fileName) {
        return pd.downloadCentralToLocal(issueNumber, fileName);
    }

    public void uploadAriesPackages(Integer issueId, int fullIssueNumber) {
        List<File> packages = AriesConnectionManager.checkForNewDeliveryPackages(fullIssueNumber);

        String prefix = IssueDate.getTranslationPackagePrefix();

        List<File> translations = packages.stream()
                .filter(f -> f.getName().startsWith(prefix))
                .collect(Collectors.toList());

        if (!translations.isEmpty()) {
            ArchiePackage.sendNewPackageReceived(cm, translations);
            packages.removeAll(translations);
        }

        if (!packages.isEmpty()) {
            Map<String, Integer> openDbIds = new HashMap<>();
            PackageType packageType = PackageType.findBaseAries().get();
            packages.forEach(fl -> uploadAriesPackage(issueId, fullIssueNumber, fl, packageType, openDbIds));
        }

        packages = AriesConnectionManager.checkForNewDeliveryPackagesForCancel(fullIssueNumber);
        if (!packages.isEmpty()) {
            PackageType packageType = PackageType.findBaseAries().get();
            packages.forEach(fl -> uploadAriesPackageOnCancel(fl, packageType));
        }

        if (CochraneCMSPropertyNames.isAriesVerificationSupported()) {
            packages = AriesConnectionManager.checkNewDeliveryPackagesForVerification(fullIssueNumber);
            if (!packages.isEmpty()) {
                ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_UPLOAD_EDI).get();
                String dbName = CochraneCMSPropertyNames.getEditorialDbName();
                packages.forEach(fl -> uploadMl3gPackage(fl.getName(), fl.toURI(), dbName, pt));
            }
        }
    }

    private void uploadAriesPackageOnCancel(File packageFile, PackageType packageType) {
        try {
            AriesImportFile importFile = AriesHelper.checkImportFile(packageFile, packageType);
            if (importFile == null) {
                return;
            }
            BaseType bt = importFile.getDbTypeByArticleType();
            if (bt == null || !bt.isScheduledPublicationSupported()) {
                LOG.warn(String.format("%s - article-type '%s' is not supported for the 'cancel' folder",
                        packageFile.getName(), importFile.getArticleType()));
                return;
            }
            if (!importFile.isDeliver()) {
                startUploadAriesPackage(packageFile.getName(), packageFile.toURI(), bt.getId(), rs.findOpenDb(
                    Constants.SPD_ISSUE_ID, bt.getId()), Constants.SPD_ISSUE_ID, Constants.SPD_ISSUE_NUMBER,
                        ProcessType.find(bt.getProductType().getProcessTypeOnUpload()).get());
            } else {
                LOG.warn(String.format("%s - a production task '%s' does not relevant to the 'cancel' folder",
                        packageFile.getName(), importFile.getProductionTaskDescription()));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    private void uploadAriesPackage(Integer issueId, int fullIssueNumber, File packageFile,
                                    PackageType packageType, Map<String, Integer> openDbIds) {
        try {
            AriesImportFile importFile = AriesHelper.checkImportFile(packageFile, packageType);
            if (importFile == null) {
                return;
            }
            BaseType bt = importFile.getDbTypeByArticleType();
            if (bt == null) {
                LOG.warn(String.format("%s - cannot match article-type '%s' to a database, CDSR will be used",
                        packageFile.getName(), importFile.getArticleType()));
                bt = BaseType.getCDSR().get();
            }
            boolean spd = importFile.isScheduled();
            if (spd && bt.isScheduledPublicationSupported()) {
                startUploadAriesPackage(packageFile.getName(), packageFile.toURI(), bt.getId(), rs.findOpenDb(
                    Constants.SPD_ISSUE_ID, bt.getId()), Constants.SPD_ISSUE_ID, Constants.SPD_ISSUE_NUMBER,
                        ProcessType.find(bt.getProductType().getProcessTypeOnUpload()).get());

            } else if (!spd && (bt.isCDSR() || bt.isEditorial() || bt.isCCA())) {
                startUploadAriesPackage(packageFile.getName(), packageFile.toURI(), bt.getId(), findOpenDbId(issueId,
                    bt.getId(), openDbIds), issueId, fullIssueNumber,
                        ProcessType.find(bt.getProductType().getProcessTypeOnUpload()).get());
                return;
            } else {
                LOG.warn(String.format("%s - article-type '%s' is not supported",
                        packageFile.getName(), importFile.getArticleType()));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    private Integer findOpenDbId(int issueId, String dbName, Map<String, Integer> openDbIds) throws CmsException {
        Integer dbId  = openDbIds.get(dbName);
        if (dbId == null) {
            dbId = rs.findOpenDb(issueId, dbName);
            openDbIds.put(dbName, dbId);
        }
        return dbId;
    }

    public void uploadMl3gPackage(String packageName, URI packageUri, String dbName, ProcessType pt) {
        LocalDate ld = CmsUtils.getCochraneDownloaderDate();
        try {
            IssueEntity ie = rs.findOpenIssueEntity(ld.getYear(), ld.getMonthValue());
            int dbId = rs.findOpenDb(ie.getId(), dbName);
            startUploadMl3gPackage(packageName, packageUri, dbName, dbId, ie.getId(), ie.getFullNumber(), pt);

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    private void startUploadMl3gPackage(String packageName, URI packageUri, String dbName, int dbId, int issueId,
                                        int fullIssueNumber, ProcessType pt) {
        int dfId = rs.createDeliveryFile(dbId, packageName, DeliveryFileEntity.TYPE_WML3G,
                IDeliveryFileStatus.STATUS_BEGIN, IDeliveryFileStatus.STATUS_PACKAGE_IDENTIFIED, "");
        DbHandler mainHandler = new PackageUploadHandler(packageName, packageUri.toString(),
                fullIssueNumber, dbName, dbId, dfId, issueId);
        CochraneCMSBeans.getCMSProcessManager().startProcess(mainHandler, pt, systemUser);
    }

    private void startUploadAriesPackage(String packageName, URI packageUri, String dbName, int dbId, int issueId,
                                         int fullIssueNumber, ProcessType pt) {
        int dfId = rs.createDeliveryFile(dbId, packageName, DeliveryFileEntity.TYPE_ARIES,
               IDeliveryFileStatus.STATUS_BEGIN, IDeliveryFileStatus.STATUS_PACKAGE_IDENTIFIED, "");
        DbHandler mainHandler = new PackageUploadHandler(packageName, packageUri.toString(),
                fullIssueNumber, dbName, dbId, dfId, issueId);
        CochraneCMSBeans.getCMSProcessManager().startProcess(mainHandler, pt, systemUser);
    }

    public String validateWml3gCDSR(String cdnumberListStr, boolean withRules) {
        return validateWMl3G(BaseType.getCDSR().get().getId(), cdnumberListStr, withRules, true);
    }

    public String validateWml3gCDSR(boolean withRules) throws Exception {

        String dbName = BaseType.getCDSR().get().getId();
        List<String> list = startValidation(dbName);
        String format = withRules ? IConverterAdapter.WILEY_ML3GV2_HW : IConverterAdapter.WILEY_ML3GV2_GRAMMAR;
        StringBuilder err = new StringBuilder("\n");
        int count = 0;
        int i = 0;
        for (String cdNumber: list) {
            count += validateWMl3G(dbName, cdNumber, true, format, err);
            i++;
            if (i % DbConstants.DB_PACK_SIZE == 0) {
                LOG.info(String.format("%d (%d) unique wml3g records were validated", i, count));
            }
        }
        return endValidation("tmp/validation_ml3g_report.txt", count, err);
    }

    public String getRecordsState(int issueNumber, String cdNumberIn) {
        String cdNumber = cdNumberIn.trim();
        int issueId = rs.findIssue(CmsUtils.getYearByIssueNumber(issueNumber),
                CmsUtils.getIssueByIssueNumber(issueNumber));
        if (DbUtils.exists(issueId)) {
            String dbName = RecordHelper.getDbNameByRecordNumber(RecordHelper.buildRecordNumber(cdNumber));
            int clDbId = rs.findDb(issueId, dbName);
            if (DbUtils.exists(clDbId)) {
                return getRecordsState(clDbId, Collections.singletonList(cdNumber), "runtime");
            }
        }
        return "";
    }

    public String getRecordsStateByPackage(Integer dfId, String operation) {
        DeliveryFileEntity entity = rs.getDeliveryFileEntity(dfId);
        if (entity == null) {
            return "cannot find a delivery file entity by " + dfId;
        }
        List<RecordEntity> records = CochraneCMSPropertyNames.getCentralDbName().equals(
                entity.getDb().getTitle()) ? Collections.emptyList() : recs.getRecordsByDFile(entity, false);
        List<String> cdNumbers = new ArrayList<>();
        records.forEach(r -> cdNumbers.add(r.getName()));
        return getRecordsState(entity.getDb(), cdNumbers, operation, records);
    }

    public String getRecordsState(Integer clDbId, Collection<String> cdNumbers, String operation) {
        ClDbEntity clDb = rs.getDb(clDbId);
        if (clDb == null) {
            return "cannot find a db entity by " + clDbId;
        }
        return getRecordsState(clDb, cdNumbers, operation, rs.getRecordsByDb(clDbId, cdNumbers));
    }

    private String getRecordsState(ClDbEntity clDb, Collection<String> cdNumbers, String operation,
                                  List<RecordEntity> records) {
        if (cdNumbers.isEmpty()) {
            return "";
        }

        String dbName = clDb.getTitle();
        StringBuilder sb = new StringBuilder(Constants.KB * cdNumbers.size()).append(operation).append(" >>>\n");

        if (CochraneCMSPropertyNames.getCDSRDbName().equals(dbName)) {
            getCDSRRecordsState(dbName, clDb, cdNumbers, records, sb);
        } else if (CochraneCMSPropertyNames.getEditorialDbName().equals(dbName)) {
            getEditorialRecordsState(dbName, clDb, cdNumbers, records, sb);
        } else {
            sb.append("printing record state is not supported for ").append(dbName);
        }

        sb.append("<<< ").append(operation).append("\n");
        return sb.toString();
    }

    private void getCDSRRecordsState(String dbName, ClDbEntity clDb, Collection<String> cdNumbers,
                                     List<RecordEntity> records, StringBuilder sb) {
        Map<String, ICDSRMeta> metadataMap = cdNumbers.isEmpty() ? Collections.emptyMap()
                : rs.findLatestMetadata(cdNumbers, true);
        cdNumbers.forEach(r -> getEntireCDSRRecordState(dbName, r, metadataMap, sb));
        records.forEach(r -> getIssueRecordState(clDb, r, sb));
    }

    private void getEditorialRecordsState(String dbName, ClDbEntity clDb, Collection<String> cdNumbers,
                                          List<RecordEntity> records, StringBuilder sb) {
        cdNumbers.forEach(r -> getEntireEditorialRecordState(dbName, r, sb));
        records.forEach(r -> getIssueRecordState(clDb, r, sb));
    }

    private void getIssueRecordState(ClDbEntity clDb, RecordEntity re, StringBuilder sb) {
        String cdNumber = re.getName();
        Integer issueId = clDb.getIssue().getId();
        sb.append(cdNumber).append(String.format(" issue %d [%d] =>\n", clDb.getIssue().getFullNumber(), issueId));

        ICDSRMeta meta = re.getMetadata();
        sb.append("issue metadata: ").append(meta).append(meta != null && meta.isJats() ? " J" : "").append("\n");

        appendContentPath(issueId, clDb.getTitle(), null, cdNumber, ContentLocation.ISSUE_COPY, sb);
    }

    private EntireRecordVO appendEntireRecord(String dbName, String cdNumber, StringBuilder sb) {
        BaseType bt = BaseType.find(dbName).get();
        sb.append(cdNumber).append(" entire =>\n");
        EntireRecordVO entireEntity = edbs.findRecordByName(bt.getDbId(), cdNumber);
        sb.append("entire: ").append(entireEntity).append("\n");
        return entireEntity;
    }

    private void getEntireEditorialRecordState(String dbName, String cdNumber, StringBuilder sb) {
        EntireRecordVO entireEntity = appendEntireRecord(dbName, cdNumber, sb);
        if (entireEntity != null) {
            appendContentPath(null, dbName, null, cdNumber, ContentLocation.ENTIRE, sb);
        }
    }

    private void getEntireCDSRRecordState(String dbName, String cdNumber, Map<String, ICDSRMeta> metadataMap,
                                          StringBuilder sb) {
        EntireRecordVO entireEntity = appendEntireRecord(dbName, cdNumber, sb);
        ICDSRMeta meta = metadataMap.get(cdNumber);
        sb.append("metadata: ").append(meta).append(meta != null && meta.isJats() ? " J" : "").append("\n");
        if (entireEntity == null || meta == null) {
            return;
        }
        int recordNumber = RecordHelper.buildRecordNumberCdsr(cdNumber);
        ICDSRMeta history = meta.getHistory();
        if (history != null) {
            sb.append("metadata history: ").append(history).append(history.isJats() ? " J" : "").append("\n");
        }

        PrevVO lastPrev, historyPrev;
        List<PrevVO> list = vm.getVersions(cdNumber);
        if (!list.isEmpty()) {
            lastPrev = list.remove(0);
            sb.append("latest: ").append(lastPrev).append("\n");
            if (!list.isEmpty()) {
                historyPrev = list.remove(0);
                sb.append("previous: ").append(historyPrev).append("\n");
                appendContentPath(null, dbName, historyPrev.version, cdNumber, ContentLocation.PREVIOUS, sb);
                appendTaState(historyPrev.version, cdNumber, rm.getTranslationHistory(recordNumber,
                        historyPrev.version), ContentLocation.PREVIOUS, "previous", sb);
            }
        }
        appendContentPath(null, dbName, null, cdNumber, ContentLocation.ENTIRE, sb);
        appendTaState(null, cdNumber, rm.getLastTranslations(recordNumber), ContentLocation.ENTIRE, "entire", sb);
    }

    private void appendTaState(Integer version, String cdNumber, List<DbRecordVO> taList, ContentLocation cl,
                               String title, StringBuilder sb) {
        sb.append(title).append(String.format(" translations: %d\n", taList.size()));
        taList.forEach(ta -> appendTaState(version, cdNumber, ta, cl, sb));
    }

    private void appendTaState(Integer v, String cdNumber, DbRecordVO taVO, ContentLocation cl, StringBuilder sb) {
        sb.append(taVO).append("\n");
        String language = taVO.getLanguage();
        appendContentPath(cl.getPathToMl3gTA(null, null, v, language, cdNumber), taVO.isJats(), sb);
        appendContentPath(cl.getPathToJatsTA(null, null, v, language, cdNumber)
                + FilePathBuilder.buildTAFileName(language, cdNumber), taVO.isJats(), sb);
        appendContentPath(cl.getPathToTA(null, v, language, cdNumber), !taVO.isJats(), sb);
        appendContentPath(cl.getPathToMl21TA(null, null, v, language, cdNumber), !taVO.isJats(), sb);
    }

    private void appendContentPath(Integer issueId, String dbName, Integer version, String cdNumber, ContentLocation cl,
                                   StringBuilder sb) {
        if (ContentLocation.ENTIRE == cl) {
            sb.append("entire content:\n");
        }
        appendContentPath(cl.getPathToMl3g(issueId, dbName, version, cdNumber, false), true, sb);
        appendContentPath(cl.getPathToMl3gAssets(issueId, dbName, version, cdNumber), true, sb);
        appendContentPath(cl.getPathToJatsSrcDir(issueId, dbName, null, version, cdNumber), true, sb);
        appendContentPath(cl.getPathToPdf(issueId, dbName, version, cdNumber), true, sb);
        appendContentPath(cl.getPathToHtml(issueId, dbName, version, cdNumber, false), true, sb);
        String srcDir = cl.getPathToMl21SrcDir(issueId, dbName, null, version, cdNumber);
        appendContentPath(srcDir + Extensions.XML, true , sb);
        appendContentPath(srcDir, false , sb);
    }

    private void appendContentPath(String path, boolean mandatory, StringBuilder sb) {
        if (rp.isFileExistsQuiet(path)) {
            sb.append("  ").append(path).append("\n");
        } else if (mandatory)  {
            sb.append("  ").append(path).append(" - does not exist\n");
        }
    }

    private List<String> startValidation(String dbName) {
        List<String> list = edbs.findRecordNames(dbName, 0, 0);
        LOG.info(String.format("%d unique records found to validate ...", list.size()));
        return list;
    }

    public String validateRevman() throws Exception {
        String dbName = BaseType.getCDSR().get().getId();
        List<String> list = startValidation(dbName);

        StringBuilder err = new StringBuilder("\n");
        int count = 0;
        int i = 0;
        for (String cdNumber: list) {
            count += validateRevman(cdNumber, true, err);
            i++;
            if (i % DbConstants.DB_PACK_SIZE == 0) {
                LOG.info(String.format("%d (%d) unique revman records were validated", i, count));
            }
        }
        return endValidation("tmp/validation_revman_report.txt", count, err);
    }

    private String endValidation(String reportPath, int count, StringBuilder err) throws Exception{
        String ret = String.format("successful count %d\nreport: %s", count, reportPath);
        err.append("\n\n").append(ret);
        RepositoryFactory.getRepository().putFile(reportPath,
            new ByteArrayInputStream(err.toString().getBytes(StandardCharsets.UTF_8)));
        return ret;
    }

    private String validateWMl3G(String dbName, String cdnumberListStr, boolean withRules, boolean withHistory) {

        String format = withRules ? IConverterAdapter.WILEY_ML3GV2_HW : IConverterAdapter.WILEY_ML3GV2_GRAMMAR;
        StringBuilder err = new StringBuilder("\n");
        String[] cdnumberList = splitNames(cdnumberListStr);
        int count = 0;
        for (String cdnumber: cdnumberList) {
            String cdNumber = splitCDNumber(cdnumber);
            count += validateWMl3G(dbName, cdNumber, withHistory, format, err);
        }
        return String.format("successful %d, %s", count, err);
    }

    private int validateWMl3G(String dbName, String cdNumber, boolean withHistory, String format, StringBuilder ret) {
        int count = 0;
        try {
            List<PrevVO> list = vm.getVersions(cdNumber);
            if (list.isEmpty()) {
                throw new Exception("cannot find db data for " + cdNumber);
            }

            PrevVO latest = list.remove(0);
            if (validateWMl3G(dbName, latest.name, FilePathBuilder.ML3G.getPathToEntireMl3gRecord(
                    dbName, cdNumber, false), format, ret)) {
                count++;
            }

            if (withHistory) {
                for (PrevVO pvo : list) {

                    if (validateWMl3G(dbName, pvo.name, FilePathBuilder.ML3G.getPathToPreviousMl3gRecord(
                            pvo.version, cdNumber), format, ret)) {
                        count++;
                    }
                }
            }

        } catch (Exception e) {
            addError(cdNumber, "", e.getMessage(), ret);
        }
        return count;
    }

    private int validateRevman(String cdNumber, boolean withHistory, StringBuilder ret) {
        int count = 0;
        try {
            List<PrevVO> list = vm.getVersions(cdNumber);
            if (list.isEmpty()) {
                throw new Exception("cannot find db data for  " + cdNumber);
            }

            PrevVO latest = list.remove(0);
            if (validateRevman(cdNumber, latest.pub,
                    FilePathBuilder.getPathToEntireRevmanRecord(latest.group, cdNumber), ret)) {
                count++;
            }

            if (withHistory) {
                for (PrevVO pvo : list) {

                    if (validateRevman(cdNumber, pvo.pub, FilePathBuilder.getPathToPreviousRevmanRecord(
                            pvo.version, pvo.group, cdNumber), ret)) {
                        count++;
                    }
                }
            }

        } catch (Exception e) {
            addError(cdNumber, "", e.getMessage(), ret);
        }
        return count;
    }

    private String[] splitNames(String cdnumberListStr) {
        return cdnumberListStr.split(CochraneCMSPropertyNames.DELIMITER);
    }

    private String splitCDNumber(String cdnumber) {
        return cdnumber.trim().split("\\.")[0].toUpperCase();
    }

    private boolean validateWMl3G(String dbName, String doi, String path, String format, StringBuilder ret) {
        try {
            String result = converter.validate(path, format, format, dbName);
            if (result == null || result.trim().isEmpty()) {
                return true;
            }
            addError(path, doi, result, ret);

        } catch (Exception e) {
            addError(path, doi, e.getMessage(), ret);
        }

        return false;
    }

    private boolean validateRevman(String cdNumber, int pub, String path, StringBuilder ret) {
        String pubName = RevmanMetadataHelper.buildPubName(cdNumber, pub);
        try {
            ICDSRMeta meta = rs.getCDSRMetadata(cdNumber, pub);

            if (meta == null) {
                throw new Exception(String.format("cannot find metadata for %s", pubName));
            }
            String source = InputUtils.readStreamToString(rp.getFile(path));
            String result = converter.validate(source, RevmanMetadataHelper.getMappedType(meta.getType(), false));
            if (result == null || result.trim().isEmpty()) {
                return true;
            }
            addError(path, pubName, result, ret);

        } catch (Exception e) {
            addError(path, pubName, e.getMessage(), ret);
        }
        return false;
    }

    private void addError(String name, String doi, String message, StringBuilder ret) {
        LOG.debug(String.format("%s - %s", doi, message));
        ret.append(name).append(" - ").append(message).append("\n");
    }
}
