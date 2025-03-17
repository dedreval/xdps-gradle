package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 17.10.2018
 */
public class LiteratumCCAGeneratorEntire extends LiteratumGeneratorEntire {
    public LiteratumCCAGeneratorEntire(EntireDbWrapper db) {
        super(db);
    }

    @Override
    protected String getPathToMl3gRecord(String dbName, String recordName) {
        return FilePathBuilder.getPathToEntireSrcRecord(dbName, recordName);
    }

    @Override
    protected List<String> getAssetsUris(String dbName, String recordName) {
        String baseAssetsDirPath = FilePathBuilder.getPathToEntireSrcRecordDir(dbName, recordName);
        File baseAssetsDir = new File(rps.getRealFilePath(baseAssetsDirPath));
        if (baseAssetsDir.exists()) {
            return FileUtils.listFiles(baseAssetsDir, null, true).stream()
                    .map(it -> it.getPath())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected IPublishChecker createPublishChecker(List<? extends RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
                false, false, isTrackByRecord() ? null : false, ps);
    }
}
