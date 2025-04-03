package com.wiley.cms.cochrane.cmanager.publish.generate;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.publish.AbstractPublisher;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.zip.GZipOutput;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 12.12.11
 */
public abstract class AbstractGeneratorWhenReady extends ArchiveGenerator<ArchiveHolder> {

    //private static final Set<String> RESERVED_PACKAGE_NAMES = Collections.synchronizedSet(new HashSet<>());
    private static final Logger LOG = Logger.getLogger(AbstractGeneratorWhenReady.class);
    //private static final int PACKAGE_NAME_GEN_DELAY = 1000;

    //private static final DateFormat LONG_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    protected String archiveName;
    protected final List<String> archivedRecords;

    private final IRecordStorage recStor;
    private final boolean fromEntire;
    private final DbWrapper db;
    private PublishProfile.PubLocationPath path;
    private PublishWrapper publish;
    private IZipOutput archive;

    protected AbstractGeneratorWhenReady(ClDbVO dbVO, String exportTypeName) {
        this(false, dbVO.getId(), dbVO.getTitle(), exportTypeName);
    }

    protected AbstractGeneratorWhenReady(EntireDbWrapper db, String exportTypeName) {
        this(true, ResultStorageFactory.getFactory().getInstance().getDatabaseEntity(db.getDbName()).getId(),
                db.getDbName(), exportTypeName);
    }

    protected AbstractGeneratorWhenReady(boolean fromEntire,
                                         int dbId, String dbName,
                                         String exportTypeName) {

        super(dbName, exportTypeName.toUpperCase() + ":WHENREADY:" + dbName, exportTypeName);

        this.rps = RepositoryFactory.getRepository();
        this.rs = ResultStorageFactory.getFactory().getInstance();
        this.recStor = RecordStorageFactory.getFactory().getInstance();
        this.ps = CochraneCMSBeans.getPublishStorage();
        this.fromEntire = fromEntire;
        this.db = new DbWrapper(dbId);
        this.archivedRecords = new ArrayList<>();
    }

    @Override
    protected void init(PublishWrapper publish) {
        this.publish = publish;

        AbstractPublisher.checkExportEntity(publish, db.getId(), getArchiveDir(), true, rs,
                       ps, CochraneCMSBeans.getFlowLogger());

        this.path = PublishProfile.getProfile().get().getPubLocation(getExportTypeName(), getDbName(), false, false);
        includedNames = publish.takeCdNumbers();
    }

    @Override
    public void start(PublishWrapper publish, PublishOperation operation) {
        try {
            logStartGenerate(LOG);

            tryExecuteGeneration(publish);
            String archiveDir = getArchiveDir();
            long size = 0;
            if (archiveDir != null) {
                size = FileUtils.getFileSize(rps.getRealFilePath(archiveDir)
                        + FilePathCreator.SEPARATOR + archiveName);
            }
            publish.setPublishEntity(
                    rs.updatePublish(publish.getId(), archiveName, false, true, generateStartDate, true, size));

            logEndGenerate(LOG);

        } catch (CmsException ce) {
            handleError(publish, ce.isNoPublishingRecords(), ce.isNoGeneratingRecordsFound(), ce);
        } catch (Exception e) {
            handleError(publish, false, false, e);
        }
        cleanup();
        publish.setGenerate(publish.getPublishEntity().isGenerated());
        errorCollector.sendErrors();
    }

    public boolean isArchiveEmpty() {
        return archivedRecords.isEmpty();
    }

    protected void tryExecuteGeneration(PublishWrapper publish) throws Exception {
        init(publish);
        generateArchive();
        past();
    }

    protected void generateArchive() throws Exception {
        defineArchiveName();
        createArchive();
        closeArchive();
    }

    protected void defineArchiveName() {
        Date timeStamp = new Date();
        if (archiveName == null) {
            archiveName = buildArchiveName(timeStamp);
            //String archivePath = getArchivePath();
            //String archiveDir = getArchiveDir();
            //File fl = new File(rps.getRealFilePath(archiveDir), archiveName);
            //while (rps.isFileExistsQuiet(archivePath) || !RESERVED_PACKAGE_NAMES.add(archivePath)) {
            //while (fl.exists() || !RESERVED_PACKAGE_NAMES.add(archivePath)) {
            //    try {
            //        Thread.sleep(PACKAGE_NAME_GEN_DELAY);
            //    } catch (InterruptedException e) {
            //    }

            //    timeStamp = new Date();
            //    archiveName = buildArchiveName(timeStamp);
            //    archivePath = getArchivePath();
            //}
        }
        generateStartDate = timeStamp;
    }

    protected String buildArchiveName(Date timeStamp) {
        IssueVO issueVO = getIssue();
        Map<String, String> replaceList = new HashMap<>();
        replaceList.put("dbname", getDbName());
        replaceList.put("year", String.valueOf(issueVO.getYear()));
        replaceList.put("issue", String.format("%02d", issueVO.getNumber()));
        replaceList.put("now", PublishHelper.buildNowParam(getDbName(), timeStamp, false));

        return PublishProfile.buildExportFileName(path.getArchive(), replaceList);
    }

    private IssueVO getIssue() {
        return fromEntire
                ? new IssueVO(rs.getLastApprovedDatabaseIssue(getDbName()))
                : new IssueVO(db.getIssue());
    }

    protected final boolean fromEntire() {
        return fromEntire;
    }

    protected void createArchive() throws Exception {
        int beginIndex = 0;
        int maxRecordsNumber = PublishProfile.PUB_PROFILE.get().getBatch();
        List<RecordWrapper> recordList = getRecords(beginIndex, maxRecordsNumber);

        while (recordList != null && recordList.size() > 0) {
            List<ArchiveEntry> archiveEntryList = processRecordList(recordList);
            addToArchiveLegacyImpl(archiveEntryList);
            beginIndex += maxRecordsNumber;
            recordList = getRecords(beginIndex, maxRecordsNumber);
        }
        List<ArchiveEntry> particularFilesList = createParticularFiles();
        addToArchiveLegacyImpl(particularFilesList);
    }

    protected List<RecordWrapper> getRecords(int beginIndex, int limit) {
        List<RecordWrapper> ret;
        if (byDeliveryPacket()) {
            int dfId = publish.getDeliveryFileId();
            ret = RecordWrapper.getRecordWrapperList(recStor.getDbRecordList(getDbId(), dfId, beginIndex, limit, null,
                    SearchRecordStatus.QA_PASSED, null, SearchRecordOrder.NONE, false, null));
        } else {
            ret = fromEntire
                    ? new ArrayList<>(getEntireRecords(beginIndex, limit))
                    : getIssueRecords(beginIndex, limit);
        }
        return ret;
    }

    protected void handleError(PublishWrapper publish, boolean empty, boolean emptyNotFound, Exception e) {
        publish.setPublishEntity(rs.setGenerating(publish.getId(), false, true, 0));
        publish.setScopeSkipped(emptyNotFound);
        
        if (emptyNotFound) {
            collectWarn(e);
            logEndGenerate(LOG);
            publish.setSend(false);
            clearArchive();

        } else {
            collectError4MainGeneration(e);
            logEndGenerate(LOG, e);
            if (empty) {
                clearArchive();
            }
        }
    }

    private List<EntireRecordWrapper> getRecordListFromIncludedNamesEntire(int limit) {
        String[] items = getItemsFromIncludedNames(limit);
        if (items == null) {
            return Collections.emptyList();
        }
        return EntireRecordWrapper.getRecordWrapperList(db.getTitle(), 0, 0, items, null,
                SearchRecordOrder.NONE, false);
    }

    private List<RecordWrapper> getRecordListFromIncludedNames(int limit) {
        String[] items = getItemsFromIncludedNames(limit);
        if (items == null) {
            return Collections.emptyList();
        }
        return RecordWrapper.getDbRecordWrapperList(db.getId(), 0, 0, items, SearchRecordStatus.QA_PASSED, null,
                SearchRecordOrder.NONE, false, false);
    }

    private List<EntireRecordWrapper> getEntireRecords(int beginIndex, int limit) {
        List<EntireRecordWrapper> ret;
        if (hasIncludedNames()) {
            ret = getRecordListFromIncludedNamesEntire(limit);
        } else {
            ret = byRecords()
                    ? EntireRecordWrapper.getProcessEntireRecordWrapperList(getDbName(), beginIndex, limit,
                            SearchRecordOrder.NONE, false, publish.getRecordsProcessId())
                    : EntireRecordWrapper.getRecordWrapperList(getDbName(), beginIndex, limit);
        }
        if (checkRecordWithNoOnlineDate()) {
            excludeDisabledRecords(ret, true);
        }
        return ret;
    }

    @Override
    protected boolean isRecordNotIncluded(RecordWrapper record, Supplier<String> pathSupplier) {
        return pathSupplier != null && (fromEntire
                ? super.isRecordNotIncluded(record, ((RecordWrapper) record)::getRecordPath)
                : isRecordWithNoFirstOnlineDate(record.getPubName(), pathSupplier));
    }

    private List<RecordWrapper> getIssueRecords(int beginIndex, int limit) {
        List<RecordWrapper> ret;
        if (hasIncludedNames()) {
            ret = getRecordListFromIncludedNames(limit);
        } else {
            ret = byRecords()
                          ? RecordWrapper.getProcessRecordWrapperList(publish.getRecordsProcessId(), beginIndex, limit)
                          : RecordWrapper.getDbRecordWrapperList(getDbId(), beginIndex, limit);
        }
        excludeDisabledRecords(ret, false);
        return ret;
    }

    @Override
    protected boolean byRecords() {
        return publish.getRecordsProcessId() > 0;
    }

    private boolean byDeliveryPacket() {
        return publish.getDeliveryFileId() > BY_ISSUE;
    }

    protected List<ArchiveEntry> processRecordList(List<? extends RecordWrapper> recordList) {
        List<ArchiveEntry> ret = new ArrayList<>();

        for (RecordWrapper record : recordList) {
            String xmlPath = rps.getRealFilePath(record.getRecordPath());
            if (!rps.isFileExistsQuiet(xmlPath)) {
                String err = String.format("%s failed to add to %s, %s doesn't exist",
                        record.getName(), archiveName, xmlPath);
                errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, err);
                continue;
            }

            add(ret, getPrefix() + record.getName(), new File(xmlPath));
            add(ret, getPrefix(), new File(StringUtils.substringBeforeLast(xmlPath, ".")));
            updateArchivedRecordList(record);
        }

        return ret;
    }

    protected abstract String getPrefix();

    private void add(List<ArchiveEntry> list, String prefix, File file) {
        String baseDir = !prefix.equals("") && !prefix.endsWith("/") ? prefix + "/" : prefix;
        if (file.isFile()) {
            list.add(new ArchiveEntry(baseDir + file.getName(), file.getAbsolutePath()));
            return;
        }
        File[] nestedFiles = file.listFiles();
        if (nestedFiles != null && nestedFiles.length > 0) {
            for (File nestedFile : file.listFiles()) {
                add(list, baseDir + file.getName(), nestedFile);
            }
        }
    }

    protected void updateArchivedRecordList(RecordWrapper record) {
        if (isTrackByRecord()) {
            ps.createPublishRecord(record.getNumber(), record.getPubNumber(), record.getDeliveryFileId(),
                    publish.getId(), record.getId());
        }
        archivedRecords.add(record.getName());
    }

    protected abstract List<ArchiveEntry> createParticularFiles() throws Exception;

    protected void addToArchiveLegacyImpl(List<ArchiveEntry> archiveEntryList) throws Exception {
        if (!archiveEntryList.isEmpty() && archive == null) {
            openArchive();
        }
        for (ArchiveEntry archiveEntry : archiveEntryList) {
            InputStream is = null;
            try {
                if (archiveEntry.getContent() != null) {
                    is = new ByteArrayInputStream(archiveEntry.getContent().getBytes(StandardCharsets.UTF_8));
                } else {
                    is = new FileInputStream(new File(archiveEntry.getPathToContent()));
                }
                archive.put(archiveEntry.getPathInArchive(), is);
            } catch (IOException e) {
                throw new Exception("Failed to add file to archive: [" + archiveEntry.getPathInArchive() + "]", e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    private void openArchive() throws Exception {
        if (getArchivePath().endsWith(".tar.gz")) {
            File parent = new File(rps.getRealFilePath(getArchiveDir()));
            if (!parent.exists()) {
                parent.mkdirs();
            }
            archive = new GZipOutput(new File(parent, archiveName), true);
        } else if (getArchivePath().endsWith(".zip")) {
            archive = new ZipOutput(getArchivePath());
        } else {
            archive = new GZipOutput(getArchivePath(), false);
        }
    }

    private void closeArchive() {
        try {
            if (archive != null) {
                archive.close();
                LOG.debug("GEN > " + archiveName);
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        archive = null;
    }

    private void clearArchive() {
        String archivePath = getArchivePath();
        String archiveDir = getArchiveDir();
        File fl = new File(rps.getRealFilePath(archiveDir), archiveName);
        try {
            if (fl.exists()) {
                fl.delete();
            }
        } catch (Exception e) {
            LOG.error("Failed to delete: [" + archivePath + "]", e);
        }
    }

    private void cleanup() {
        closeArchive();
        //RESERVED_PACKAGE_NAMES.remove(getArchivePath());
        if (publish.byRecords()) {
            RecordWrapper.getProcessStorage().deleteProcess(publish.getRecordsProcessId());
        }
    }

    protected void past() throws Exception {
        if (isArchiveEmpty()) {
            throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_PUBLISHING_RECORD));
        }
        saveArchivedRecordsList();
    }

    private void saveArchivedRecordsList() {
        String records = StringUtils.join(archivedRecords, ",");
        try (OutputStream os = new FileOutputStream(new File(rps.getRealFilePath(getArchiveDir()),
                getRecordListFileName(archiveName)))) {
            IOUtils.write(records, os, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to save records list from archive " + archiveName);
        }
    }

    @Override
    public String getDbName() {
        return db.getTitle();
    }

    private int getDbId() {
        return db.getId();
    }

    private String getArchivePath() {
        return FilenameUtils.separatorsToUnix(FilenameUtils.concat(getArchiveDir(), archiveName));
    }

    protected Integer getExportId() {
        return publish.getId();
    }

    protected String getArchiveDir() {
        if (fromEntire) {
            return String.format("%s/%s/publish/%s",
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY),
                    getDbName(),
                    getExportTypeName());
        } else {
            return String.format("%s/%s/%s/publish/%s",
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY),
                    db.getIssue().getId(),
                    getDbName(),
                    getExportTypeName());
        }
    }
        
    public static String getRecordListFileName(String archiveName) {
        return archiveName + ".list";
    }
}
