package com.wiley.cms.cochrane.cmanager.central;

import java.util.Set;

import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.08.11
 */
public interface IPackageDownloader  {
    void downloadCentral(IssueWrapper issue) throws Exception;

    String downloadCentralSFTP(int year, int month, Integer issueId) throws Exception;

    String downloadFromLocal(String path);

    String downloadCentralToLocal(int issueNumber, String path);

    String exportCDSRWhenReady(int issueNumber, Set<Integer> recordIds, boolean upload, boolean jats) throws Exception;

    String uploadCDSRWhenReady(int dbId, Set<Integer> recordIds) throws Exception;
}
