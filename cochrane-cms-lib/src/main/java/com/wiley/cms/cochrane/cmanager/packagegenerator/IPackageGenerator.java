package com.wiley.cms.cochrane.cmanager.packagegenerator;

import java.util.Collection;
import java.util.List;

import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 29.01.2010
 */
public interface IPackageGenerator {

    List<String> generateAndUpload(List<String> recordNames, String dbName, int fullIssueNumber, String suffix)
            throws Exception;

    List<String> generateAndUpload(List<String> recordNames, int dbId, String suffix) throws Exception;

    List<String> generateAndUpload(List<String> recordNames, int issueId, String dbName, String suffix)
            throws Exception;

    String generatePackage(Collection<String> recordNames, int issueId, String dbName, String suffix) throws Exception;

    void deliverPackage(String fileName, int issueId, String dbName) throws Exception;

    default int getMaxRecordsInPackage() {
        return Constants.UNDEF;
    }
}
