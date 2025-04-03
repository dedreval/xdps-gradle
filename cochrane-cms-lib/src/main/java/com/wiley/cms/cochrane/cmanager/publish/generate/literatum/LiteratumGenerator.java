package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GGenerator;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryUtils;

import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumGenerator extends AbstractGenerator<LiteratumMultiPackArchiveHolder> {

    private boolean onlyNewContent;
    private HWDependency hwDependency;

    public LiteratumGenerator(ClDbVO db) {
        super(db, "LIT:ML3G:" + db.getTitle(), PubType.TYPE_LITERATUM);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        onlyNewContent = publish.isOnlyNewContent();
        if (publish.hasPublishToAwait()) {
            hwDependency = new HWDependency(publish);
        }

        if (byRecords()) {
            archive.setPriorityStrategy(BaseType.find(getDb().getTitle()).get().getSelectiveLiteratumPriority());

        } else if (byDeliveryPacket()) {
            archive.setPriorityStrategy(BaseType.find(getDb().getTitle()).get().getPackageLiteratumPriority());

        } else {
            archive.setPriorityStrategy(BaseType.find(getDb().getTitle()).get().getIssueLiteratumPriority());
        }
    }

    private boolean hasHWPublish() {
        return hwDependency != null;
    }

    private boolean isOnlyNewContent() {
        return onlyNewContent;
    }

    @Override
    protected LiteratumMultiPackArchiveHolder createArchiveHolder() {
        return new LiteratumMultiPackArchiveHolder(errorCollector);
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return byDeliveryPacket() ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(),
             startIndex, count) : RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count, true);
    }

    @Override
    protected List<RecordWrapper> getRecords(int startIndex, int count) {
        return byRecords() ? getSortableByRecordNumber(RecordWrapper.getProcessRecordWrapperList(getRecordsProcessId(),
                startIndex, count, true)) : getRecordList(startIndex, count);
    }

    private static List<RecordWrapper> getSortableByRecordNumber(List<RecordWrapper> list) {
        list.sort(Comparator.comparing(RecordWrapper::getNumber));
        return list;
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {
        excludeDisabledRecords(recordList, false);

        ML3GGenerator.excludeNotConvertedToWml3gRecords(recordList, getExportFileName(),
                errorCollector, recs, flowLogger);
        if (recordList.isEmpty()) {
            return new ArrayList<>();
        }

        IPublishChecker checker = createPublishChecker(recordList);

        archive.resetEntries();
        for (RecordWrapper record: recordList) {
            processRecord(getDb().getIssue().getId(), getDb().getTitle(), record, checker);
        }
        if (hasHWPublish()) {
            hwDependency.saveRecordToHWPublish(errorCollector, LOG);
        }
        return archive.getEntries();
    }

    protected IPublishChecker createPublishChecker(List<RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
            true, false, isTrackByRecord() ? null : false, ps);
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        if (hasHWPublish() && (archive.getAllRecordCount() == 0 || shouldStopHWPublishAwait())) {
            // there is nothing to await
            hwDependency.stopHWPublishAwait();
        }
        return archive.createParticularlyFiles(sbn, rps, rs);
    }

    protected boolean shouldStopHWPublishAwait() {
        return false;
    }

    protected void processRecord(int issueId, String dbName, RecordWrapper record, IPublishChecker checker)
            throws Exception {
        processRecord(issueId, dbName, record, record.getPubNumber(), RecordEntity.VERSION_LAST,
                record.getId(), checker);
    }

    final LiteratumArchiveHolder processRecord(int issueId, String dbName, RecordWrapper record, int pubNumber,
        int version, Integer recordId, IPublishChecker checker) throws Exception {

        String cdNumber = record.getName();
        Ml3gXmlAssets assets = getMl3gAssets(issueId, dbName, cdNumber, pubNumber, version,
                RecordEntity.VERSION_LAST == version && isRecordOutdated(record));
        if (assets == null) {
            return null;
        }

        String pubName = getPubName(cdNumber, pubNumber);
        LiteratumArchiveHolder current = archive.checkRecord(checker.getPublishCheckerItem(record.getNumber(), pubName),
                sbn, checker, isOnlyNewContent(), rps, rs);
        if (current != null) {

            addArchiveEntries(cdNumber, pubName, assets, sbn, current.getEntries(), addSbnToPath(), rps);
            if (isTrackByRecord()) {
                ps.createPublishRecord(record.getNumber(), pubNumber, record.getDeliveryFileId(),
                        current.getExport().getId(), recordId);
            }
            onRecordArchive(cdNumber, version, pubName);

        } else {
            // skip this record
            current = archive;
            onRecordSkipped(cdNumber, version, recordId);
        }
        return current;
    }

    protected void onRecordSkipped(String cdNumber, int version, Integer recordId) {
        if (version == RecordEntity.VERSION_LAST && hasHWPublish()) {
            hwDependency.addRecordToHWPublish(recordId, cdNumber, isWhenReady());
        }
    }

    static void addArchiveEntries(String recName, String pubName, Ml3gXmlAssets assets, String sbn,
        List<ArchiveEntry> entries, boolean addSbn, IRepository rp) {

        String sbnName = addSbn ? sbn + "." + pubName : pubName;
        String basePath = FilePathCreator.SEPARATOR + sbn + FilePathCreator.SEPARATOR + sbnName
                + FilePathCreator.SEPARATOR;

        String ml3gPathInArch = basePath + sbnName + Extensions.XML;
        entries.add(new ArchiveEntry(ml3gPathInArch, rp.getRealFilePath(assets.getXmlUri())));

        for (String assetUri : assets.getAssetsUris()) {

            String filePath;
            String fileName;
            if (assetUri.contains(JatsPackage.JATS_FOLDER)) {
                filePath = rp.getRealFilePath(JatsMl3gAssetsManager.getRealAssetUri(assetUri));
                fileName = RepositoryUtils.getLastNameByPath(JatsMl3gAssetsManager.getMappedAsset(assetUri));
            } else {
                filePath = rp.getRealFilePath(assetUri);
                fileName = RepositoryUtils.getLastNameByPath(filePath);
            }

            String ext = "." + FilenameUtils.getExtension(fileName);

            String assetPath = null;
            if (Extensions.PDF.equals(ext)) {
                fileName = fileName.replace(recName, sbnName);
                assetPath = basePath + fileName;

            } else if (Extensions.RM5.equals(ext) || FilePathBuilder.containsRawData(fileName)
                    || Extensions.ZIP.equals(ext) || Extensions.HTML.equals(ext) || Extensions.DOCX.equals(ext)) {
                assetPath = basePath + "suppl/" + fileName;

            } else if (FileUtils.isGraphicExtension(ext)) {
                assetPath = basePath + "graphic/" + fileName;
            }

            if (assetPath != null) {
                entries.add(new ArchiveEntry(assetPath, filePath));
            } else {
                LOG.warn(String.format("cannot build an archive path for: %s", assetUri));
            }
        }
    }

    protected Ml3gXmlAssets createAssets(int issueId, String dbName, String cdNumber, int version,
                                         boolean outdated) throws Exception {

        Ml3gXmlAssets assets = new Ml3gXmlAssets();
        setBaseAssetsUri(getPathToMl3gRecord(issueId, dbName, cdNumber), assets, rps);
        assets.setAssetsUris(getAssetsUris(issueId, dbName, cdNumber, outdated));
        return assets;
    }

    private Ml3gXmlAssets getMl3gAssets(int issueId, String dbName, String cdNumber, int pubNumber, int version,
                                        boolean outdated) {
        Ml3gXmlAssets assets;
        try {
            assets = createAssets(issueId, dbName, cdNumber, version, outdated);

        } catch (Exception e) {
            assets = null;
            handleAssetsError(cdNumber, getPubName(cdNumber, pubNumber), getExportFileName(), e,
                    errorCollector, flowLogger);
        }
        return assets;
    }

    static void handleAssetsError(String cdNumber, String pubName, String exportFileName, Exception e,
                                  GenerationErrorCollector errCollector, IFlowLogger flowLogger) {
        String reason = e.getMessage();
        if (StringUtils.isEmpty(reason)) {
            reason = "undefined reason";
        }
        String message = String.format("Failed to obtain WML3G content for %s: %s", pubName, reason);
        LOG.error(message, e);
        errCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, message);
        if (flowLogger != null) {
            flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, exportFileName, cdNumber, message,
                    true, true, false);
        }
    }

    protected List<String> getAssetsUris(int issueId, String dbName, String recordName, boolean outdatedIssue)
            throws Exception {
       
        StringBuilder errs = new StringBuilder();
        List<String> assetsUris = PublishHelper.getPublishContentUris(this, Constants.UNDEF, recordName, outdatedIssue,
                false, ContentLocation.ISSUE, errs);
        if (assetsUris == null) {
            throw new Exception(errs.toString());
        }
        return assetsUris;
    }

    protected String getPathToMl3gRecord(int issueId, String dbName, String recordName) {
        return FilePathBuilder.ML3G.getPathToMl3gRecord(issueId, dbName, recordName);
    }

    protected boolean addSbnToPath() {
        return true;
    }

    @Override
    protected void addArchivedRecordsToTrack(List<? extends RecordWrapper> records) {
    }
}
