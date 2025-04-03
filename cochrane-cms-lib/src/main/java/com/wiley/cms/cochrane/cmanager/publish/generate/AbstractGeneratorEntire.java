package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;

import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 10.11.11
 *
 * @param <H> A holder for an arhive related to the given feed
 */
public abstract class AbstractGeneratorEntire<H extends ArchiveHolder> extends ArchiveGenerator<H> {
    private EntireDbWrapper db;

    protected AbstractGeneratorEntire(EntireDbWrapper db, String generateName, String exportTypeName) {

        super(db.getDbName(), generateName, exportTypeName);
        this.db = db;
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {

        super.init(publish);

        Date startDate = publish.getStartDate();
        generateStartDate = (startDate == null) ? new Date() : startDate;

        String type = PublishProfile.buildExportDbName(publish.getPath(), false, byRecords());
        String packagePath = FilePathCreator.getDirPathForPublish(db.getDbName(), DbEntity.NOT_EXIST_ID,
                getExportTypeName(), false);

        PublishEntity pe = checkExportEntityEntire(publish, db.getDbName(), type, packagePath, true);

        archive.init(publish, pe, packagePath);
        
        archive.clearArchive(rps);
        archive.initArchive(rps);
    }

    private void sendErrors() {
        errorCollector.sendErrors();
    }

    public void start(PublishWrapper publish, PublishOperation op) {
        logStartGenerate(LOG);
        try {
            init(publish);
            generate();
            archive.closeArchive();

        } catch (CmsException ce) {
            if (ce.hasErrorInfo()) {
                handleError(publish, ce.getErrorInfo(), ce);
            } else {
                handleError(ce, true);
            }
            return;

        } catch (Exception e) {
            handleError(e, true);
            return;

        } finally {
            sendErrors();
            if (publish.byRecords()) {
                CochraneCMSBeans.getCMSProcessManager().deletePublishProcess(publish.getRecordsProcessId());
            }
        }
        logEndGenerate(LOG);
        if (archive.export != null) {   
            publish.setPublishEntity(archive.onGeneration(generateStartDate, rps, rs));
        }
    }

    private void handleError(PublishWrapper pw, ErrorInfo eInfo, CmsException e) {
        if (ErrorInfo.Type.NO_GENERATING_RECORD_FOUND == eInfo.getErrorType()) {
            pw.setScopeSkipped(true);
            handleError(e, false);
        } else {
            handleError(e, true);
        }
    }

    private void handleError(Exception e, boolean error) {
        if (error) {
            logEndGenerate(LOG, e);
            collectError4MainGeneration(e);

        } else {
            logEndGenerate(LOG);
            collectMessage(e);
        }
        if (archive.export != null) {
            archive.onGeneration(null, rps, rs);
        }
        archive.closeArchive();
        archive.clearArchive(rps);
    }

    private void generate() throws Exception {
        int beginIndex = 0;
        int maxRecordsNumber = getBatchSize();
        boolean added = false;
        List<EntireRecordWrapper> recordList = getRecords(beginIndex, maxRecordsNumber);
        if (maxRecordsNumber > 0) {
            while (recordList != null && recordList.size() > 0) {
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

    protected void addHtmlContent(List<ArchiveEntry> ret, EntireRecordWrapper record) {
        String recName = record.getName();
        String htmlContentDirPath =
            FilePathCreator.getRenderedDirPathEntire(getDb().getDbName(), recName, RenderingPlan.HTML);
        File[] htmlContent = rps.getFilesFromDir(htmlContentDirPath);
        if (htmlContent != null) {
            for (File file : htmlContent) {
                add(ret, getArchivePrefix(recName), file);
            }
        } else {
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN,
                    "HTML directory " + String.format(NOT_EXIST_MSG, htmlContentDirPath));
        }
    }

    protected void addSourceContent(List<ArchiveEntry> ret, EntireRecordWrapper record) {
        String recName = record.getName();
        String sourceContentDirPath =
            FilePathCreator.getFilePathForEnclosureEntire(getDb().getDbName(), recName, "");
        File[] sourceContent = rps.getFilesFromDir(sourceContentDirPath);
        if (sourceContent != null) {
            for (File file : sourceContent) {
                add(ret, getArchivePrefix(recName), file);
            }
        }
    }

    protected void add(List<ArchiveEntry> list, String prefix, File file) {
        if (file.isFile()) {
            list.add(new ArchiveEntry(prefix + "/" + file.getName(), file.getAbsolutePath()));
            return;
        }
        File[] aList = file.listFiles();
        if (aList != null) {
            for (File nestedFile : aList) {
                add(list, prefix + "/" + file.getName(), nestedFile);
            }
        } else {
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN,
                    "File or directory " + String.format(NOT_EXIST_MSG, file));
        }
    }

    protected abstract String getArchivePrefix(String recName);

    protected abstract List<EntireRecordWrapper> getRecordList(int startIndex, int count);

    protected abstract List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception;

    public EntireDbWrapper getDb() {
        return db;
    }

    @Override
    public String getDbName() {
        return getDb().getDbName();
    }

    @Override
    public Integer getIssueId() {
        return Constants.UNDEF;
    }

    protected String getConvertedSource(String recordName) {
        return FilePathCreator.getFilePathForEntireMl3gXml(getDb().getDbName(), recordName);
    }

    protected List<EntireRecordWrapper> getRecords(int startIndex, int count) {
        if (!byRecords())  {
            return getRecordList(startIndex, count);
        }
        return EntireRecordWrapper.getProcessEntireRecordWrapperList(getDb().getDbName(),
                startIndex, count, SearchRecordOrder.NONE, false, getRecordsProcessId());
    }

    protected List<EntireRecordWrapper> getRecordListFromIncludedNames(int count, int search, boolean orderBy) {
        String[] items = getItemsFromIncludedNames(count);
        if (items == null) {
            return Collections.emptyList();
        }
        return EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), 0, 0, items, null, search, orderBy);
    }

    @Override
    protected boolean isRecordNotIncluded(RecordWrapper record, Supplier<String> pathSupplier) {
        return pathSupplier != null && isRecordWithNoFirstOnlineDate(record.getPubName(), pathSupplier);
    }
}
