package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCDSRGenerator extends ML3GCDSRGenerator {

    private boolean addPreviousVersion = CochraneCMSPropertyNames.addPreviousVersionForDs();
    private StringBuilder meshNames;

    public DSCDSRGenerator(ClDbVO db) {
        this(db, "DS:ML3G:" + db.getTitle(), PubType.TYPE_DS);
    }

    protected DSCDSRGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS());
    }

    @Override
    protected List<RecordWrapper> getRecords(int startIndex, int count) {
        return !byRecords() ? getRecordList(startIndex, count)
            : RecordWrapper.getProcessRecordWrapperList(getRecordsProcessId(), startIndex, count, addPreviousVersion());
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames() ? getRecordListFromIncludedNames(count, addPreviousVersion())
            : (byDeliveryPacket() ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(),
                startIndex, count, addPreviousVersion())
            : RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count, addPreviousVersion()));
    }

    @Override
    protected boolean addPreviousVersion() {
        return addPreviousVersion;
    }

    //@Override
    //protected boolean checkRecordWithNoOnlineDate() {
    //    return true;
    //}

    @Override
    protected boolean checkEmpty() {
        return true;
    }

    @Override
    protected void onRecordArchive(RecordWrapper record) {
        super.onRecordArchive(record);

        if (record.isMeshtermUpdated()) {
            if (meshNames == null) {
                meshNames = new StringBuilder();
            } else {
                meshNames.append(", ");
            }
            meshNames.append(record.getName());
        }
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<>();
        addDoiXml(ret, archiveRootDir, getMeshtermUpdated(), RecordHelper::buildDoiCDSRAndEditorial);
        return ret;
    }

    static void addEditorialTopicXml(List<ArchiveEntry> ret, String archiveRootDir, IRepository rps)
            throws IOException {
        String topicPath = rps.getRealFilePath(
                FilePathBuilder.getPathToTopics(GroupVO.SID_EDITORIAL));
        String content = FileUtils.readStream(new File(topicPath).toURI());
        ret.add(new ArchiveEntry(archiveRootDir + "/" + Constants.TOPICS_SOURCE, null, content));
    }


    private String getMeshtermUpdated() {
        return meshNames != null ? meshNames.toString() : null;
    }

    private void addEditorialTopicXml(List<ArchiveEntry> ret) throws Exception {

        boolean hasPrevious = RevmanPackage.hasPreviousEditorial();
        boolean fewRecords = hasPrevious && (byRecords() || byDeliveryPacket());
        if (fewRecords || byIssue()) {
            addEditorialTopicXml(ret, archiveRootDir, rps);
            RevmanPackage.removePreviousEditorial();
        }
    }
}
