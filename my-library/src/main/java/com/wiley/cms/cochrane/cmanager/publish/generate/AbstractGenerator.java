package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.ErrorInfo;

import org.apache.commons.io.filefilter.TrueFileFilter;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 31.10.11
 *
 * @param <H> A holder for an arhive related to the given feed
 **/
public abstract class AbstractGenerator<H extends ArchiveHolder> extends ArchiveGenerator<H> {
    protected static final int THREE = 3;

    protected IRecordStorage recs;

    private final ClDbVO db;
    private int deliveryFileId;
    private boolean outdatedIssue;

    protected AbstractGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db.getTitle(), generateName, exportTypeName);

        this.db = db;
    }

    protected abstract List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception;

    protected abstract List<RecordWrapper> getRecordList(int startIndex, int count) throws Exception;

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        recs = RecordStorageFactory.getFactory().getInstance();

        generateStartDate = publish.getStartDate();
        deliveryFileId = publish.getDeliveryFileId();

        String packagePath = FilePathCreator.getDirPathForPublish(db.getTitle(), db.getIssue().getId(),
                        getExportTypeName(), !byIssue());
        PublishEntity pe = checkExportEntity(publish, db.getId(), packagePath, true, rs, ps, flowLogger);
        outdatedIssue = PublishHelper.isIssueOutdated(db.getIssue().getId(), db.getTitle(),
                flowLogger.getRecordCache().getLastDatabases());

        archive.init(publish, pe, packagePath);

        archive.clearArchive(rps);
        archive.initArchive(rps);
    }

    protected boolean isRecordOutdated(RecordWrapper record) {
        if (outdatedIssue && FilePathBuilder.isEntirePath(record.getRecordPath())) {
            ICDSRMeta meta = rs.findLatestMetadata(record.getName(), false);
            return meta != null && meta.getIssue() > db.getIssue().getFullNumber();
        }
        return false;
    }

    private void sendErrors() {
        errorCollector.sendErrors();
    }

    public void start(PublishWrapper publish, PublishOperation operation) {
        logStartGenerate(LOG);
        try {
            init(publish);
            createArchive();
            archive.closeArchive();
            publish.setPublishEntity(archive.onGeneration(generateStartDate, rps, rs));

        } catch (CmsException ce) {
            handleError(publish, ce, true);
            return;

        } catch (Exception e) {
            handleError(publish, e, true, true, false, false);
            return;

        } finally {
            sendErrors();
            if (publish.byRecords()) {
                RecordWrapper.getProcessStorage().deleteProcess(publish.getRecordsProcessId());
            }
        }
        logEndGenerate(LOG);
    }

    protected void handleError(PublishWrapper publish, CmsException e, boolean critical) {

        boolean empty = e.isNoPublishingRecords();
        boolean emptyNotFound = e.isNoGeneratingRecordsFound();
        publish.setScopeSkipped(emptyNotFound);
        boolean notify = !emptyNotFound || !isWhenReady();
        if ((critical && empty) || emptyNotFound) {

            publish.setSend(false);
            publish.setUnpack(false);
            handleError(publish, e, false, notify, empty, emptyNotFound);
            archive.closeArchive();
            archive.clearArchive(rps);

        } else {
            handleError(publish, e, critical, notify, empty, emptyNotFound);
        }
    }

    protected void handleError(PublishWrapper publish, Exception e, boolean critical, boolean notify, boolean empty,
                               boolean emptyNotFound) {
        if (emptyNotFound) {
            logEndGenerate(LOG);
        } else {
            logEndGenerate(LOG, e);
        }
        if (archive.export != null) {
            publish.setPublishEntity(archive.onGeneration(null, rps, rs));
        }

        if (critical) {
            LOG.error(e);
            if (byDeliveryPacket()
                    && PublishProfile.PUB_PROFILE.get().getDestination().isMandatoryPubType(publish.getType())) {
                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PUBLISHING_FAILED, true);

            } else if (isWhenReady()) {
                archive.onWhenReadyFail(tag, getExportTypeName(), e);
            }
        }

        if (notify) {
            if (emptyNotFound) {
                collectMessage(e);

            } else {
                collectError4MainGeneration(e);
            }
        }
        archive.closeArchive();
    }

    protected void createArchive() throws Exception {
        int beginIndex = 0;
        int maxRecordsNumber = getBatchSize();
        List<RecordWrapper> recordList = getRecords(beginIndex, maxRecordsNumber);
        boolean added = false;
        if (maxRecordsNumber > 0) {
            while (recordList != null && !recordList.isEmpty()) {
                List<ArchiveEntry> archiveEntryList = processRecordList(recordList);
                added = added || (archiveEntryList != null && !archiveEntryList.isEmpty());
                addToArchive(archiveEntryList);
                onRecordsBatchArchived(recordList);

                beginIndex += maxRecordsNumber;
                recordList = getRecords(beginIndex, maxRecordsNumber);
            }
        }
        if (!added && checkEmpty()) {
            throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_GENERATING_RECORD_FOUND));
        }
        addToArchive(createParticularFiles());
    }

    protected void createMeshArchive() throws Exception {
        int issue = getDb().getIssue().getFullNumber();
        String path = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES)
                + "medline/downloaded/" + issue;

        File fl = new File(new URI(path));
        if (!fl.exists()) {
            throw new Exception(String.format("Root directory %s doesn't exist", path));
        } else {
            File[] lists = fl.listFiles();
            if (lists == null || lists.length == 0) {
                throw new Exception(String.format("Root directory %s is empty", path));
            }
        }

        List<ArchiveEntry> archiveEntryList = new ArrayList<>();
        add(archiveEntryList, "", fl);
        addToArchive(archiveEntryList);
    }

    protected void add(List<ArchiveEntry> list, String prefix, File file) {
        add(list, prefix, file, TrueFileFilter.INSTANCE);
    }

    protected void add(List<ArchiveEntry> list, String prefix, File file, FilenameFilter filter) {
        if (file.isFile()) {
            if (filter.accept(file.getParentFile(), file.getName())) {
                list.add(new ArchiveEntry(prefix + "/" + file.getName(), file.getAbsolutePath()));
            }
            return;
        }
        File[] aList = file.listFiles();
        if (aList != null) {
            for (File nestedFile : aList) {
                add(list, prefix + "/" + file.getName(), nestedFile, filter);
            }
        } else {
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN,
                    "File or directory " + String.format(NOT_EXIST_MSG, file));
        }
    }

    protected ClDbVO getDb() {
        return db;
    }

    @Override
    public String getDbName() {
        return getDb().getTitle();
    }

    @Override
    public Integer getIssueId() {
        return getDb().getIssue().getId();
    }

    protected boolean isWhenReady() {
        return deliveryFileId == BY_WHEN_READY;
    }

    protected boolean byDeliveryPacket() {
        return deliveryFileId > BY_ISSUE;
    }

    protected boolean byIssue() {
        return deliveryFileId == BY_ISSUE;
    }

    public int getDeliveryFileId() {
        return deliveryFileId;
    }

    protected List<RecordWrapper> getRecords(int startIndex, int count) throws Exception {
        return !byRecords() ? getRecordList(startIndex, count)
                : RecordWrapper.getProcessRecordWrapperList(getRecordsProcessId(), startIndex, count);
    }

    protected final List<RecordWrapper> getRecordListFromIncludedNames(int count, boolean prevVersion) {
        String[] items = getItemsFromIncludedNames(count);
        if (items == null) {
            return Collections.emptyList();
        }
        return RecordWrapper.getDbRecordWrapperList(getDb().getId(), 0, 0, items, SearchRecordStatus.QA_PASSED, null,
            SearchRecordOrder.NAME, false, prevVersion);
    }
}