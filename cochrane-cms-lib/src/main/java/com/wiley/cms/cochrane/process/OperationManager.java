package com.wiley.cms.cochrane.process;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.ImportJatsPackage;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.meshtermmanager.IMeshtermCodesUpdaterManager;
import com.wiley.cms.cochrane.process.handler.OpHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.term2num.Term2NumHelper;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;

import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.cochrane.test.LogPatterns;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/10/2015
 */
@Stateless
@Local(IOperationManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class OperationManager implements IOperationManager {
    private static final Logger LOG = Logger.getLogger(OperationManager.class);

    @EJB(beanName = "DbStorage")
    private IDbStorage dbs;

    @EJB(beanName = "CMSProcessManager")
    private ICMSProcessManager manager;

    @EJB(beanName = "MeshtermCodesUpdaterManager")
    private IMeshtermCodesUpdaterManager mum;

    @EJB(beanName = "VersionManager")
    private IVersionManager vm;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    @EJB(beanName = "ActivityLogService")
    private IActivityLogService logService;

    public void importRecords(int dbId, Integer[] recordIds, String user) {
        DbVO dbVO = dbs.getDbVO(dbId);
        ImportContext context = new ImportContext(dbVO.getTitle(), new ImportJatsPackage.CDSRHistory(vm, rs),
                CmsUtils::replaceFile, CmsUtils::replaceDir);
        for (Integer recordId: recordIds) {
            RecordEntity re = null;
            try {
                re = findImportedRecord(recordId);
                importRecord(context.init(re));

            } catch (CmsException | CmsJTException ce) {
                if (re != null && context.meta != null) {
                    logService.logRecordError(ILogEvent.IMPORT_FAILED, recordId, context.pubName,
                            DatabaseEntity.CDSR_KEY, Constants.IMPORT_JATS_ISSUE_NUMBER, ce.getMessage());
                }
                LOG.error(ce.getMessage());

            } catch (Throwable tr) {
                LOG.error(tr.getMessage());
            }
        }
    }

    public void restoreRecords(int dbId, Integer[] recordIds, String user) {
        DbVO dbVO = dbs.getDbVO(dbId);
        ImportContext context = new ImportContext(dbVO.getTitle(), null, CmsUtils::restoreFile, CmsUtils::restoreDir);
        for (Integer recordId: recordIds) {
            try {
                restoreRecord(context.init(findImportedRecord(recordId)));
                
            } catch (Throwable tr) {
                LOG.error(tr.getMessage());
            }
        }
    }

    private void restoreRecord(ImportContext context) throws Exception {

        context.checkOnRestore(context.meta, rs);

        rm.setMetadataImported(context.meta.getId(), null, Constants.IMPORT_JATS_ISSUE_NUMBER, 0);
        LOG.debug(String.format("%s is being restored ...", context.pubName));
        if (context.isPrevious()) {
            restoreTranslations(context, ContentLocation.ISSUE_PREVIOUS, ContentLocation.PREVIOUS,
                                context.getPathToBackPrev());
            context.moveContent(ContentLocation.ISSUE_PREVIOUS, ContentLocation.PREVIOUS, context.getPathToBackPrev());

        } else {
            restoreTranslations(context, ContentLocation.ISSUE, ContentLocation.ENTIRE, null);
            context.moveContent(ContentLocation.ISSUE, ContentLocation.ENTIRE, null);
        }
        ICDSRMeta lastMeta = rs.getCDSRMetadata(context.cdNumber, context.pub);
        rm.setMetadataImported(context.meta.getId(),
            (lastMeta != null && lastMeta.getIssue() != Constants.IMPORT_JATS_ISSUE_NUMBER) ? lastMeta.getId() : null,
                Constants.IMPORT_JATS_ISSUE_NUMBER, -1);
        LOG.debug(String.format("%s was restored from backup", context.pubName));
    }

    private void importRecord(ImportContext context) throws Exception {

        ICDSRMeta existed = context.checkOnImport(rm);
        int issueImported = existed == null ? context.meta.getCitationIssue() : existed.getIssue();
        if (!context.ta) {
            rm.setMetadataImported(context.meta.getId(), null, issueImported, 1);
        }

        LOG.debug(String.format("%s is being imported ...", context.pubName));
        if (context.isPrevious()) {
            context.moveContent(ContentLocation.ISSUE_PREVIOUS, ContentLocation.PREVIOUS, context.getPathToBackPrev());
            context.fixAssets(ContentLocation.ISSUE_PREVIOUS, ContentLocation.PREVIOUS);
            importTranslations(context, ContentLocation.ISSUE_PREVIOUS, ContentLocation.PREVIOUS,
                    context.getPathToBackPrev());
        } else {
            context.moveContent(ContentLocation.ISSUE, ContentLocation.ENTIRE, null);
            context.fixAssets(ContentLocation.ISSUE, ContentLocation.ENTIRE);
            importTranslations(context, ContentLocation.ISSUE, ContentLocation.ENTIRE, null);
        }
        if (!context.ta) {
            rm.setMetadataImported(context.meta.getId(), existed != null ? existed.getId() : null, issueImported, 2);
        }
        LOG.debug(String.format("%s imported", context.pubName));
    }

    private void importTranslations(ImportContext context, ContentLocation from, ContentLocation to,
                                    String pathToBackupPrev) {
        if (context.imported.isEmpty() && context.existed.isEmpty()) {
            return;
        }
        List<DbRecordVO> results = rm.importTranslations(context.number, context.version, !context.ta, context.existed,
                context.imported);
        int[] res = {0, 0};
        results.forEach(tvo -> res[0] += context.moveContent(tvo, from, to, pathToBackupPrev, false));
        results.forEach(tvo -> res[1] += context.moveContent(tvo, from, to, pathToBackupPrev, true));
        LOG.debug(String.format("translations of %s: imported %d, removed %d", context.pubName, res[1], res[0]));
    }

    private void restoreTranslations(ImportContext context, ContentLocation from, ContentLocation to,
                                     String pathToBackupPrev) throws Exception {
        List<DbRecordVO> results = rm.restoreTranslations(context.number, context.dfId, context.version);
        if (results.isEmpty()) {
            return;
        }
        int[] res = {0, 0};
        results.forEach(tvo -> res[0] += context.moveContent(tvo, from, to, pathToBackupPrev, true));
        results.forEach(tvo -> res[1] += context.moveContent(tvo, from, to, pathToBackupPrev, false));
        LOG.debug(String.format("translations of %s: restored %d, removed %d", context.pubName, res[1], res[0]));
    }

    private RecordEntity findImportedRecord(Integer recordId) throws CmsException {
        RecordEntity re = dbs.find(RecordEntity.class, recordId);
        if (re == null) {
            throw new CmsException(String.format("no record found by [%d]", recordId));
        }
        if (!CmsUtils.isImportIssue(re.getDb().getIssue().getId())) {
            throw new CmsException(String.format("a record [%d] does not belong to imported Issue", recordId));
        }
        RecordMetadataEntity importedMeta = re.getMetadata();
        String cdNumber = re.getName();
        if (!re.getUnitStatus().isTranslationUpdated() && (importedMeta == null || !importedMeta.isJats())) {
            throw new CmsException(String.format("no imported JATS metadata found by %s [%d]", cdNumber, recordId));
        }
        return re;
    }

    public void updateMeshtermCodes(String user) {
        if (checkExist(ICMSProcessManager.LABEL_TERM2NUM) || checkExist(ICMSProcessManager.LABEL_WML3G_MESH_UPDATE)) {
            return;
        }

        try {
            mum.prepareMeshtermCodes(user);
        } catch (Exception e) {
            LOG.warn(String.format("%s is stopped.", ICMSProcessManager.LABEL_TERM2NUM), e);
        }
    }

    public void clearDB(int dbId, String dbName, String user) {

        dbs.setClearing(dbId, true);

        ProcessHandler ph = new OpHandler(Operation.CLEARING, dbId, dbName);
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_CLEAR_DB).get();

        manager.startProcess(ph, pt, user);
    }

    public void performTerm2NumCreation(boolean download, String user) {
        if (checkExist(ICMSProcessManager.LABEL_TERM2NUM) || checkExist(ICMSProcessManager.LABEL_WML3G_MESH_UPDATE)) {
            return;
        }

        ProcessHandler ph = new OpHandler(ICMSProcessManager.LABEL_TERM2NUM,
            DbEntity.NOT_EXIST_ID, download ? Operation.TERM2NUM_MAKE_PERM.name() : Operation.TERM2NUM_CREATION.name());
        manager.startProcess(ph, ProcessType.find(ICMSProcessManager.PROC_TYPE_TERM2NUM1).get(), user);
    }

    private boolean checkExist(String label) {
        if (manager.existProcess(label)) {
            LOG.info(String.format("%s is already being executed now ... ", label));
            return true;
        }
        return false;
    }

    /**
     * enum for common operations
     */
    public enum Operation {
        CLEARING {

            @Override
            public void performOperation(OpHandler handler, ProcessVO pvo) {
                DbStorageFactory.getFactory().getInstance().clearDb(handler.getSystemId());
            }

            @Override
            public void logOnStart(OpHandler handler, ProcessVO pvo, ProcessManager manager) {
                logInf(LogPatterns.OP_OBJECT_START, handler);
                logOnStart(ILogEvent.CLEARING_STARTED, handler, pvo, manager);
            }

            @Override
            public void logOnEnd(OpHandler handler, ProcessVO pvo, ProcessManager manager) {
                logInf(LogPatterns.OP_OBJECT_END, handler);
                logOnEnd(ILogEvent.CLEARING_COMPLETED, handler, pvo, manager);
            }

            public void logOnFail(OpHandler handler, ProcessVO pvo, String msg, ProcessManager manager) {
                logErr(LogPatterns.OP_OBJECT_FAIL, handler, msg);
                logOnFail(ILogEvent.CLEARING_FAILED, msg, handler, pvo, manager);
            }

        }, TERM2NUM_CREATION {
            @Override
            public void performOperation(OpHandler handler, ProcessVO pvo) throws ProcessException {
                Term2NumHelper.makeTerm2Num(false);
            }

        }, TERM2NUM_MAKE_PERM {
            @Override
            public void performOperation(OpHandler handler, ProcessVO pvo) throws ProcessException {
                Term2NumHelper.makeTerm2Num(true);
            }

        },  MONTHLY_MESHTERM,

        TERM2NUM_CHECK_CHANGES_MESH_CODES;

        public void performOperation(OpHandler handler, ProcessVO pvo) throws ProcessException {
        }

        public void performOperation(OpHandler handler, ProcessPartVO pvo) throws ProcessException {
        }

        public void logOnStart(OpHandler handler, ProcessVO pvo, ProcessManager manager) {
            manager.logProcessStart(pvo, 0, pvo.getType().getName(), pvo.getType().getId());
        }

        public void logOnEnd(OpHandler handler, ProcessVO pvo, ProcessManager manager) {
            manager.logProcessEnd(pvo, 0, pvo.getType().getName(), pvo.getType().getId());
        }

        public void logOnFail(OpHandler handler, ProcessVO pvo, String msg, ProcessManager manager) {
            manager.logProcessFail(pvo, 0, pvo.getType().getName(), pvo.getType().getId(), msg);
        }

        protected void logOnStart(int event, OpHandler handler, ProcessVO pvo, ProcessManager manager) {
            manager.logProcessStart(pvo, event, handler.getSystemName(), handler.getSystemId());
        }

        protected void logOnEnd(int event, OpHandler handler, ProcessVO pvo, ProcessManager manager) {
            manager.logProcessEnd(pvo, event, handler.getSystemName(), handler.getSystemId());
        }

        protected void logOnFail(int event, String msg, OpHandler handler, ProcessVO pvo, ProcessManager manager) {
            manager.logProcessFail(pvo, event, handler.getSystemName(), handler.getSystemId(), msg);
        }

        protected void logInf(String pattern, OpHandler handler) {
            LOG.info(String.format(pattern, name(), handler.getSystemName(), handler.getSystemId()));
        }

        protected void logErr(String pattern, OpHandler handler, String msg) {
            LOG.error(String.format(pattern, name(), handler.getSystemName(), handler.getSystemId(), msg));
        }
    }

    /**
     * Just factory
     */
    public static class Factory extends AbstractBeanFactory<IOperationManager> {
        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super(CochraneCMSPropertyNames.buildLookupName("OperationManager", IOperationManager.class));
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }

    private static class ImportContext {
        //private boolean validationTa = Property.get(
        //        "cms.cochrane.jats.import.validation-ta", "true").get().asBoolean();

        private final String dbName;
        private final ImportJatsPackage.CDSRHistory history;
        private final String pathToBackup;

        private final BiFunction<String[], IRepository, Exception> flMover;
        private final BiFunction<String[], IRepository, Exception> dirMover;

        private final Integer issueId = Constants.IMPORT_JATS_ISSUE_ID;
        private final IRepository rp = RepositoryFactory.getRepository();

        private Integer dfId;
        private String cdNumber;
        private int pub;
        private int number;
        private String pubName;
        private Integer version;
        private RecordMetadataEntity meta;
        private boolean ta = false;
        private Integer recordId;

        private final Map<Integer, String> prevPaths = new HashMap<>();
        private final String[] paths = new String[]{null, null, null};
        /** an existed  id -> imported VO  */
        private final Map<Integer, DbRecordVO> existed = new HashMap<>();
        private Map<String, Pair<File, File>> existedTaFiles = null;
        private Map<String, Pair<File, File>> existedTaJatsFiles = null;

        private final Map<Integer, DbRecordVO> imported =  new HashMap<>();
        private final Map<String, Pair<String, String>> importedFiles = new HashMap<>();

        private final StringBuilder ml3gAssetManagerErrs = new StringBuilder();

        private ImportContext(String dbName, ImportJatsPackage.CDSRHistory history,
                              BiFunction<String[], IRepository, Exception> flMover,
                              BiFunction<String[], IRepository, Exception> dirMover) {
            this.dbName = dbName;
            this.history = history;
            this.flMover = flMover;
            this.dirMover = dirMover;
            pathToBackup = FilePathBuilder.getPathToBackup(Constants.IMPORT_JATS_ISSUE_ID, dbName);
        }

        private String getPathToBackPrev() {
            return prevPaths.computeIfAbsent(version, f -> FilePathBuilder.getPathToBackupPrevious(
                    Constants.IMPORT_JATS_ISSUE_ID, dbName, version));
        }

        private ImportContext init(RecordEntity re) throws CmsException {
            existed.clear();
            existedTaFiles = null;
            existedTaJatsFiles = null;

            imported.clear();
            importedFiles.clear();
            paths[0] = null;
            paths[1] = null;
            paths[2] = null;

            recordId = re.getId();
            dfId = re.getDeliveryFile().getId();
            cdNumber = re.getName();
            meta = re.getMetadata();
            pub = meta.getPubNumber();
            number = RecordHelper.buildRecordNumberCdsr(cdNumber);
            pubName = RevmanMetadataHelper.buildPubName(cdNumber, pub);
            version = meta.getHistoryNumber();
            ta = re.getUnitStatus().isTranslationUpdated();

            if (!re.isQasSuccessful() || !re.isRenderingSuccessful()) {
                throw new CmsException(String.format("%s wasn't fully processed", pubName));
            }
            return this;
        }

        private boolean isPrevious() {
            return version > RecordEntity.VERSION_LAST;
        }

        private ICDSRMeta checkOnImport(IRecordManager rm) throws Exception {
            ICDSRMeta existedMeta = history.findExistedMeta(cdNumber, pub);
            if (existedMeta == null || CmsUtils.isSpecialIssueNumber(existedMeta.getIssue())) {
                return null;
            }
                        
            if (existedMeta.getHistoryNumber() != null && !version.equals(existedMeta.getHistoryNumber())) {
                throw new CmsException(String.format(
                    "%s: an imported history number is %d, but the legacy one is %d",
                        pubName, version, existedMeta.getHistoryNumber()));
            }
            if (existedMeta.isJats() && (existedMeta.getCochraneVersion().equals(meta.getCochraneVersion())
                    || CmsUtils.isFirstMoreThanSecond(existedMeta.getCochraneVersion(), meta.getCochraneVersion())))  {
                throw new CmsException(String.format(
                    "%s: an imported version %s is not newer than the existing JATS %s",
                        pubName, meta.getCochraneVersion(), existedMeta.getCochraneVersion()));
            }

            history.check(existedMeta, meta);
            checkImportedTranslations(existedMeta, rm);
            return existedMeta;
        }

        private void checkOnRestore(ICDSRMeta importedMeta, IResultsStorage rs) throws Exception {
            ICDSRMeta lastMeta = rs.getCDSRMetadata(meta.getCdNumber(), meta.getPubNumber());
            if (lastMeta != null && !lastMeta.getId().equals(importedMeta.getId())) {
                throw new CmsException(String.format("%s: an imported meta [%d] is not latest to replace [%d]",
                        pubName, importedMeta.getId(), lastMeta.getId()));
            }
        }

        private void checkImportedTranslations(ICDSRMeta existedMeta, IRecordManager rm) throws Exception {
            boolean prev = isPrevious();
            List<DbRecordVO> existedList = prev ? rm.getTranslationHistory(number, version)
                    : rm.getLastTranslations(number);
            List<DbRecordVO> importedList = rm.getTranslations(number, dfId);

            ContentLocation cl = prev ? ContentLocation.ISSUE_PREVIOUS : ContentLocation.ISSUE;
            for (DbRecordVO tvo: importedList) {
                String label = tvo.getLabel();
                if (!label.equals(pubName)) {
                    continue;
                }
                String language = tvo.getLanguage();
                String jatsTaFolderPath = cl.getPathToJatsTA(issueId, dfId, version, language, pubName);
                String jatsPath = RecordHelper.findPathToJatsTAOriginalRecord(jatsTaFolderPath, cdNumber, rp);
                String wml3gPath = cl.getPathToMl3gTA(issueId, dfId, version, language, pubName);
                if (rp.isFileExists(wml3gPath)) {
                    importedFiles.put(language, new Pair<>(jatsPath, wml3gPath));
                    imported.put(tvo.getId(), tvo);
                    LOG.debug(String.format("%s.%s is preparing to import...", label, language));
                }
            }
            existedTaFiles = prev
                    ? TranslatedAbstractsHelper.getAbstractsFromPrevious(version, cdNumber, false)
                    : TranslatedAbstractsHelper.getAbstractsFromEntire(cdNumber, false);
            existedTaJatsFiles = prev
                    ? TranslatedAbstractsHelper.getAbstractsFromPrevious(version, cdNumber, true)
                    : TranslatedAbstractsHelper.getAbstractsFromEntire(cdNumber, true);

            for (DbRecordVO tvo: existedList) {
                if (tvo.isDeleted()) {
                    continue;
                }
                String language = tvo.getLanguage();
                String label = tvo.getLabel();
                if (!label.equals(pubName)) {
                    LOG.warn(String.format("%s.%s: should have %s", label, language, pubName));
                    continue;
                }
                if (!existedTaFiles.containsKey(language) && !existedTaJatsFiles.containsKey(language)) {
                    String msg = String.format("%s.%s: existing %s translation's content not found",
                            pubName, language, tvo.isJats() ? "jats" : "legacy");
                    //if (validationTa) {
                    //    throw new CmsException(msg);
                    //}
                    LOG.warn(msg);
                    continue;
                }
                if (!importedFiles.containsKey(language)) {
                    String msg = String.format("%s.%s: imported content for existing translation not found",
                            pubName, language);
                    //if (validationTa) {
                    //    throw new CmsException(msg);
                    //}
                    LOG.warn(msg);
                }
                existed.put(tvo.getId(), tvo);
            }
        }

        private void moveContent(String[] paths, String pathToBackup, String pathToBackupPrev,
                                 BiFunction<String[], IRepository, Exception> mover) {
            String backUpPath = paths[2];
            if (pathToBackupPrev != null && backUpPath != null) {
                paths[2] = backUpPath.replace(pathToBackup, pathToBackupPrev);
            }
            Exception e = mover.apply(paths, rp);
            paths[2] = backUpPath;
            if (e != null) {
                LOG.error(e.getMessage());
            }
        }

        private void moveContent(ContentLocation from, ContentLocation to, String pathToBackupPrev) {
            String group = meta.getGroupSid();
            ContentLocation back = ContentLocation.ISSUE_COPY;

            // rendering FOP
            paths[0] = from.getPathToPdf(issueId, dbName, version, cdNumber);
            paths[1] = to.getPathToPdf(issueId, dbName, version, cdNumber);
            paths[2] = back.getPathToPdf(issueId, dbName, version, cdNumber);
            moveContent(paths, pathToBackup, pathToBackupPrev, dirMover);

            // ml3g
            paths[0] = from.getPathToMl3g(issueId, dbName, version, cdNumber, false);
            paths[1] = to.getPathToMl3g(issueId, dbName, version, cdNumber, false);
            paths[2] = back.getPathToMl3g(issueId, dbName, version, cdNumber, false);
            moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            paths[0] = paths[0].replace(Extensions.XML, Extensions.ASSETS);
            paths[1] = paths[1].replace(Extensions.XML, Extensions.ASSETS);
            paths[2] = paths[2].replace(Extensions.XML, Extensions.ASSETS);
            moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            if (ta) {
                // there is no source to move
                return;
            }

            // revman
            paths[0] = from.getPathToRevmanSrc(issueId, version, group, cdNumber);
            paths[1] = to.getPathToRevmanSrc(issueId, version, group, cdNumber);
            paths[2] = back.getPathToRevmanSrc(issueId, version, group, cdNumber);
            moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            String metadataName = cdNumber + Constants.METADATA_SOURCE_SUFFIX;
            paths[0] = paths[0].replace(cdNumber, metadataName);
            paths[1] = paths[1].replace(cdNumber, metadataName);
            paths[2] = paths[2].replace(cdNumber, metadataName);
            moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            // legacy rendering
            paths[0] = from.getPathToPdfTex(issueId, dbName, version, cdNumber);
            paths[1] = to.getPathToPdfTex(issueId, dbName, version, cdNumber);
            paths[2] = back.getPathToPdfTex(issueId, dbName, version, cdNumber);
            moveContent(paths, pathToBackup, pathToBackupPrev, dirMover);

            paths[0] = from.getPathToHtml(issueId, dbName, version, cdNumber, false);
            paths[1] = to.getPathToHtml(issueId, dbName, version, cdNumber, false);
            paths[2] = back.getPathToHtml(issueId, dbName, version, cdNumber, false);
            moveContent(paths, pathToBackup, pathToBackupPrev, dirMover);

            // wml21
            paths[0] = from.getPathToMl21SrcDir(issueId, dbName, dfId, version, cdNumber);
            paths[1] = to.getPathToMl21SrcDir(issueId, dbName, dfId, version, cdNumber);
            paths[2] = back.getPathToMl21SrcDir(issueId, dbName, dfId, version, cdNumber);
            moveContent(paths, pathToBackup, pathToBackupPrev, dirMover);

            paths[0] = paths[0] != null ? paths[0] + Extensions.XML : null;
            paths[1] = paths[1] != null ? paths[1] + Extensions.XML : null;
            paths[2] = paths[2] != null ? paths[2] + Extensions.XML : null;
            moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            // jats
            //boolean fromIssue = ContentLocation.ISSUE == from;
            paths[0] = from.getPathToJatsSrcDir(issueId, dbName, dfId, version, pubName);
            paths[1] = to.getPathToJatsSrcDir(issueId, dbName, dfId, version, cdNumber);
            paths[2] = back.getPathToJatsSrcDir(issueId, dbName, dfId, version, cdNumber);
            moveContent(paths, pathToBackup, pathToBackupPrev, dirMover);
        }

        private int moveContent(DbRecordVO tvo, ContentLocation from, ContentLocation to, String pathToBackupPrev,
                                boolean imported) {
            ContentLocation back = ContentLocation.ISSUE_COPY;
            String language = tvo.getLanguage();
            Pair<String, String> importedPair = importedFiles.get(language);
            Pair<File, File> existedPair = existedTaFiles == null ? null : existedTaFiles.get(language);
            if (existedPair == null) {
                existedPair = existedTaJatsFiles == null ? null : existedTaJatsFiles.get(language);
            }
            boolean jats = tvo.isJats();
            boolean old = tvo.getIssue() != Constants.IMPORT_JATS_ISSUE_NUMBER;
            if (imported == old) {
                return 0;
            }
            if (!jats) {
                paths[0] = null;
                paths[1] = existedPair != null ? RepositoryUtils.getRepositoryPath(existedPair.first)
                        : to.getPathToTA(issueId, version, language, cdNumber);
                paths[2] = back.getPathToTA(issueId, version, language, cdNumber);
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

                paths[1] = existedPair != null ? RepositoryUtils.getRepositoryPath(existedPair.second)
                        : to.getPathToMl21TA(issueId, null, version, language, cdNumber);
                paths[2] = back.getPathToMl21TA(issueId, null, version, language, cdNumber);
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            } else if (old) {
                paths[0] = null;
                paths[1] = existedPair != null ? RepositoryUtils.getRepositoryPath(existedPair.first)
                        : to.getPathToJatsTA(issueId, dfId, version, language, cdNumber)
                            + FilePathBuilder.buildTAFileName(language, cdNumber);
                paths[2] = FilePathBuilder.TR.getPathToBackupJatsTARecord(issueId, language, cdNumber);
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

                paths[1] = existedPair != null ? RepositoryUtils.getRepositoryPath(existedPair.second)
                        : to.getPathToMl3gTA(issueId, dfId, version, language, cdNumber);
                paths[2] = FilePathBuilder.TR.getPathToBackupWML3GTARecord(issueId, language, cdNumber);
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);

            } else {
                paths[0] = importedPair != null ? importedPair.first : null;
                String jatsTaFolderPath = to.getPathToJatsTA(issueId, dfId, version, language, cdNumber);
                String taName = FilePathBuilder.buildTAFileName(language, cdNumber);
                paths[1] = jatsTaFolderPath + taName;
                paths[2] = null;
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);
                //   paths[2] = FilePathBuilder.TR.getPathToBackupJatsTARecord(issueId, language, cdNumber);

                paths[0] = importedPair != null ? importedPair.second : null;
                paths[1] = to.getPathToMl3gTA(issueId, dfId, version, language, cdNumber);
                //   paths[2] = FilePathBuilder.TR.getPathToBackupWML3GTARecord(issueId, language, cdNumber);
                moveContent(paths, pathToBackup, pathToBackupPrev, flMover);
            }
            return 1;
        }

        private void fixAssets(ContentLocation src, ContentLocation dest) throws Exception {
            Ml3gAssetsManager.copyAssetsFromOneLocation2Another(dbName, issueId, cdNumber, version, src, dest,
                    ml3gAssetManagerErrs);
            if (!pubName.equals(cdNumber)) {
                // modifying resulted assets as Ml3gAssetsManager doesn't work with .pub modifiers
                String assetPath = FilePathBuilder.ML3G.getPathToEntireMl3gRecordAssets(dbName, cdNumber);
                String assets = InputUtils.readStreamToString(rp.getFile(assetPath)).replace(pubName, cdNumber);
                RecordHelper.putFile(assets, assetPath, rp);
            }
        }

    }
}
