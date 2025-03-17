package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumWRGenerator extends LiteratumGenerator {

    private WhenReadyDependency wrDependency;

    public LiteratumWRGenerator(ClDbVO db) {
        super(db);
    }

    @Override
    public boolean isWhenReady() {
        return wrDependency != null;
    }

    @Override
    protected boolean shouldStopHWPublishAwait() {
        return isWhenReady();
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        if (archive.getExport().getPublishType().equals(
                PublishProfile.PUB_PROFILE.get().getDestination().getWhenReadyTypeId(PubType.TYPE_LITERATUM))) {

            archive.setPriorityStrategy(BaseType.find(getDbName()).get().getWhenReadyLiteratumPriority());
            if (useCommonWRSupport()) {
                wrDependency = new WhenReadyDependency();
            }
        }
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {

        List<ArchiveEntry> ret = super.processRecordList(recordList);

        if (isWhenReady()) {
            wrDependency.trackRecordsForWhenReadyPublish(taInserter, getDb().getId(), getDeliveryFileId(),
                    getExportTypeName());
        }
        return ret;
    }

    protected boolean useCommonWRSupport() {
        return true;
    }

    @Override
    protected void processRecord(int issueId, String dbName, RecordWrapper record, IPublishChecker checker)
        throws Exception {

        LiteratumArchiveHolder current = processRecord(issueId, dbName, record, record.getPubNumber(),
                RecordEntity.VERSION_LAST, record.getId(), checker);
        if (current == null) {
            return;
        }
        if (isWhenReady()) {
            wrDependency.addRecord(record.getName(), record.getDeliveryFile().getId(), current.getExport());

        } else if (byRecords()) {
            List<PrevVO> prevList = record.getVersions().getPreviousVersionsVO();
            for (PrevVO prev : prevList) {
                processRecord(issueId, dbName, record, prev.pub, prev.version, null, checker);
            }
        }
    }

    @Override
    protected void onRecordSkipped(String cdNumber, int version, Integer recordId) {
        super.onRecordSkipped(cdNumber, version, recordId);

        if (isWhenReady() && version == RecordEntity.VERSION_LAST) {
            wrDependency.addSkippedRecord(cdNumber);
        }
    }
}
