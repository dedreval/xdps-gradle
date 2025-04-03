package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumGeneratorEntire extends AbstractGeneratorEntire<LiteratumMultiPackArchiveHolder> {

    private boolean onlyNewContent = false;
    private HWDependency hwDependency;

    public LiteratumGeneratorEntire(EntireDbWrapper db) {
        super(db, "LIT:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_LITERATUM);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        onlyNewContent = publish.isOnlyNewContent();
        if (publish.hasPublishToAwait()) {
            hwDependency = new HWDependency(publish);
        }
        archive.setPriorityStrategy(BaseType.find(getDb().getDbName()).get().getEntireLiteratumPriority());
        if (byRecords()) {
            archive.setPriorityStrategy(BaseType.find(getDb().getDbName()).get().getSelectiveLiteratumPriority());
        }
    }

    private boolean hasHWPublish() {
        return hwDependency != null;
    }

    protected boolean isOnlyNewContent() {
        return onlyNewContent;
    }

    @Override
    protected LiteratumMultiPackArchiveHolder createArchiveHolder() {
        return new LiteratumMultiPackArchiveHolder(errorCollector);
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        if (hasHWPublish() && archive.getAllRecordCount() == 0) {
            // there is nothing to await
            hwDependency.stopHWPublishAwait();
        }
        return archive.createParticularlyFiles(sbn, rps, rs);
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return null;
    }

    protected boolean addSbnToPath() {
        return true;
    }

    @Override
    protected List<EntireRecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames() ? getRecordListFromIncludedNames(count, SearchRecordOrder.NONE, false)
            : EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), startIndex, count, null, null,
                SearchRecordOrder.NAME, false);
    }

    @Override
    protected List<EntireRecordWrapper> getRecords(int startIndex, int count) {
        return byRecords() ? EntireRecordWrapper.getProcessEntireRecordWrapperList(getDb().getDbName(),
            startIndex, count, SearchRecordOrder.NAME, false, getRecordsProcessId()) : getRecordList(startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception {

        ML3GCDSRGeneratorEntire.excludeUnconvertedRecords(recordList, errorCollector);

        if (recordList.isEmpty()) {
            return new ArrayList<>();
        }

        IPublishChecker checker = createPublishChecker(recordList);

        archive.resetEntries();
        for (RecordWrapper record: recordList) {
            processRecord(getDb().getDbName(), record, checker);
        }

        if (hasHWPublish()) {
            hwDependency.saveRecordToHWPublish(errorCollector, LOG);
        }
        return archive.getEntries();
    }

    protected IPublishChecker createPublishChecker(List<? extends RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
                true, false, isTrackByRecord() ? null : false, ps);
    }

    protected void processRecord(String dbName, RecordWrapper record, IPublishChecker checker) throws Exception {
        processRecord(dbName, record, record.getPubNumber(), RecordEntity.VERSION_LAST, record.getId(), checker);
    }

    final LiteratumArchiveHolder processRecord(String dbName, RecordWrapper record, int pubNumber, int version,
                                               Integer recordId, IPublishChecker checker) throws Exception {
        String cdNumber = record.getName();
        Ml3gXmlAssets assets = getMl3gAssets(dbName, cdNumber, pubNumber, version);
        if (assets == null) {
            return null;
        }
        String pubName = getPubName(cdNumber, pubNumber);
        LiteratumArchiveHolder current = archive.checkRecord(checker.getPublishCheckerItem(record.getNumber(), pubName),
                sbn, checker, isOnlyNewContent(), rps, rs);
        if (current != null) {
            LiteratumGenerator.addArchiveEntries(cdNumber, pubName, assets, sbn, current.getEntries(),
                    addSbnToPath(), rps);
            if (isTrackByRecord()) {
                ps.createPublishRecord(RecordHelper.buildRecordNumber(cdNumber), pubNumber,
                        null, current.getExport().getId(), recordId);
            }
            onRecordArchive(cdNumber, version, pubName);
            
        } else {
            // skip this record
            current = archive;
            if (version == RecordEntity.VERSION_LAST && hasHWPublish()) {
                hwDependency.addRecordToHWPublish(recordId, cdNumber, false);
            }
        }
        return current;
    }

    protected Ml3gXmlAssets createAssets(String dbName, String cdNumber, int version) throws Exception {
        Ml3gXmlAssets  assets = new Ml3gXmlAssets();
        setBaseAssetsUri(getPathToMl3gRecord(dbName, cdNumber), assets, rps);
        assets.setAssetsUris(getAssetsUris(dbName, cdNumber));
        return assets;
    }

    protected List<String> getAssetsUris(String dbName, String recordName) throws Exception {
        StringBuilder errs = new StringBuilder();
        List<String> assetsUris = PublishHelper.getPublishContentUris(this, Constants.UNDEF, recordName, false,
                false, ContentLocation.ENTIRE, errs);
        if (assetsUris == null) {
            throw new Exception(errs.toString());
        }
        return assetsUris;
    }

    protected String getPathToMl3gRecord(String dbName, String recordName) {
        return FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, recordName, false);
    }

    private Ml3gXmlAssets getMl3gAssets(String dbName, String cdNumber, int pubNumber, int version) {
        Ml3gXmlAssets assets;
        try {
            assets = createAssets(dbName, cdNumber, version);

        } catch (Exception e) {
            assets = null;
            LiteratumGenerator.handleAssetsError(cdNumber, getPubName(cdNumber, pubNumber), getExportFileName(), e,
                    errorCollector, null);
        }
        return assets;
    }

    @Override
    protected void addArchivedRecordsToTrack(List<? extends RecordWrapper> records) {
    }
}
