package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.WhenReadyDependency;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.tes.util.FileUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/23/2016
 */
public class SemanticoCDSRGenerator extends ML3GCDSRGenerator{

    private boolean addPreviousVersion = CochraneCMSPropertyNames.addPreviousVersionForSemantico();

    private WhenReadyDependency wrDependency;

    public SemanticoCDSRGenerator(ClDbVO db) {
        super(db, "SEMANTICO:ML3G:" + db.getTitle(), PubType.TYPE_SEMANTICO);
    }

    protected SemanticoCDSRGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        if (archive.getExport().getPublishType().equals(
                PublishProfile.PUB_PROFILE.get().getDestination().getWhenReadyTypeId(getExportTypeName()))) {
            wrDependency = new WhenReadyDependency();
        }
    }

    protected boolean isOnlyTopic() {
        return false;
    }

    @Override
    protected List<RecordWrapper> getRecords(int startIndex, int count) throws Exception {
        return !byRecords() ? getRecordList(startIndex, count)
            : RecordWrapper.getProcessRecordWrapperList(getRecordsProcessId(), startIndex, count, addPreviousVersion());
    }

    @Override
    protected boolean isWhenReady() {
        return wrDependency != null;
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {

        if (isWhenReady() && hasIncludedNames() && byDeliveryPacket()) {
            return getWRRecordListFromIncludedNames(count);
        }
        return isOnlyTopic() ? Collections.emptyList() : (byDeliveryPacket()
            ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(),
                    startIndex, count, addPreviousVersion())
            : (hasIncludedNames() ? getRecordListFromIncludedNames(count, addPreviousVersion())
                : RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count, addPreviousVersion())));
    }

    private List<RecordWrapper> getWRRecordListFromIncludedNames(int limit) {
        String[] items = getItemsFromIncludedNames(limit);
        return items == null ? Collections.emptyList() : RecordWrapper.getDbRecordWrapperList(
                getDb().getId(), 0, 0, items, SearchRecordStatus.HW_PUBLISHING, null, 0, false, addPreviousVersion());
    }

    @Override
    protected void addArchivedRecordsToTrack(List<? extends RecordWrapper> records) {
        if (isWhenReady()) {
            wrDependency.trackRecordsForWhenReadyPublish(taInserter, getDb().getId(), getDeliveryFileId(),
                    getExportTypeName());
        }
        super.addArchivedRecordsToTrack(records);
    }

    @Override
    protected List<String> getAssetsUris(int issueId, RecordWrapper record, boolean outdated) throws Exception {

        StringBuilder errs = new StringBuilder();
        List<String> assetsUris = null;
        if (!record.isPublishingCanceled()) {
            try {
                assetsUris = PublishHelper.getPublishContentUris(this, Constants.UNDEF, record.getName(), outdated,
                        true, ContentLocation.ISSUE, errs);
                if (assetsUris == null) {
                    throw new Exception(errs.toString());
                }
            } catch (Exception e) {
                if (isWhenReady()) {
                    List<String> list = new ArrayList<>();
                    list.add(record.getName());
                    CochraneCMSBeans.getRecordManager().setRecordState(
                            RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION,
                            RecordEntity.STATE_WR_ERROR, getDb().getId(), list);
                }
                throw e;
            }
        }
        if (isWhenReady()) {
            wrDependency.addRecord(record.getName(), record.getDeliveryFile().getId(), archive.getExport());
        }
        addToSPDManifest(record);

        return assetsUris;
    }

    @Override
    protected boolean addPreviousVersion() {
        return addPreviousVersion;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = addSPDManifest(new ArrayList<>());
        if (!CmsUtils.isScheduledIssue(getDb().getIssue().getId())) {
            addEditorialTopicXml(ret);
        }
        return ret;
    }

    static void addEditorialTopicXml(List<ArchiveEntry> ret, String archiveRootDir,
        IRepository rps) throws IOException  {

        String topicPath = rps.getRealFilePath(
            FilePathBuilder.getPathToTopics(GroupVO.SID_EDITORIAL));
        String content = FileUtils.readStream(new File(topicPath).toURI());
        ret.add(new ArchiveEntry(archiveRootDir + "/" + Constants.TOPICS_SOURCE, null, content));
    }

    private void addEditorialTopicXml(List<ArchiveEntry> ret) throws Exception {

        boolean hasPrevious = RevmanPackage.hasPreviousEditorial();
        boolean fewRecords = hasPrevious && (isWhenReady() || byRecords() || byDeliveryPacket());
        if (isOnlyTopic() && isWhenReady() && !hasPrevious) {
            throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_GENERATING_RECORD_FOUND));
        }

        if (fewRecords || byIssue() || isOnlyTopic()) {
            addEditorialTopicXml(ret, archiveRootDir, rps);
            RevmanPackage.removePreviousEditorial();
        }
    }
}
