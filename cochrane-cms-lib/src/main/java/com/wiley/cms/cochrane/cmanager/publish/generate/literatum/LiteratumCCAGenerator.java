package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 17.10.2018
 */
public class LiteratumCCAGenerator extends LiteratumWRGenerator  {
    private String processedRecordPath;
    private boolean useCommonWrSupport = true;

    public LiteratumCCAGenerator(ClDbVO db) {
        super(db);
    }

    public void setUseCommonWrSupport(boolean value) {
        useCommonWrSupport = value;
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        if (byDeliveryPacket()) {
            return RecordWrapper.getRecordWrapperList(recs.getDbRecordList(getDb().getId(), getDeliveryFileId(),
                    startIndex, count, null, SearchRecordStatus.QA_PASSED, null, SearchRecordOrder.NONE, false, null));
        } else {
            return super.getRecordList(startIndex, count);
        }
    }

    @Override
    protected IPublishChecker createPublishChecker(List<RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
            byRecords(), false, isTrackByRecord() ? null : false, ps);
    }

    @Override
    protected void processRecord(int issueId,
                                 String dbName,
                                 RecordWrapper record,
                                 IPublishChecker checker) throws Exception {
        processedRecordPath = record.getRecordPath();
        super.processRecord(issueId, dbName, record, checker);
    }

    @Override
    protected String getPathToMl3gRecord(int issueId, String dbName, String recordName) {
        return processedRecordPath;
    }

    @Override
    protected boolean useCommonWRSupport() {
        return useCommonWrSupport;
    }

    @Override
    protected List<String> getAssetsUris(int issueId, String dbName, String recordName, boolean outdated) {
        String baseAssetsDirPath = FilenameUtils.removeExtension(processedRecordPath);
        File baseAssetsDir = new File(rps.getRealFilePath(baseAssetsDirPath));
        if (baseAssetsDir.exists()) {
            return FileUtils.listFiles(baseAssetsDir, null, true).stream()
                    .map(it -> it.getPath())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
