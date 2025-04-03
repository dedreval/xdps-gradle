package com.wiley.cms.cochrane.cmanager.data.db;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.process.IModelController;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IDbStorage extends IModelController {
    DbVO getDbVO(int clDbId);

    void updateDatabaseAllCounter(int clDbId, int count);

    Integer getDatabaseAllCounter(int clDbId);

    DbVO getNextDbVOByNameAndIssue(String dbName, int issueId);

    DbVO getDbVOByNameAndIssue(String dbName, int issueId);

    void setClearing(int dbId, boolean clearing);

    void clearIssueDb(int id, boolean onlyForArchive);

    void clearDb(Integer id);

    void updateRecordCount(int id);

    void updateAllRecordCount(int id);

    void updateRenderedRecordCount(int id);

    void updateAllAndRenderedRecordCount(int id);

    void setDbArchiving(ClDbEntity db, boolean value);

    void setDbArchived(ClDbEntity db, boolean value);

    boolean unArchiveIssueDb(int dbId);

    List<ClDbEntity> getDbByIssue(IssueEntity issue);

    void deleteDbByIssue(IssueEntity issue);

    void setInitialPackageDeliveredByIssueIdAndTitle(int issueId, String title);

    int findEditorialDbIdByCDSRDbId(int sysrevDbId);

    boolean isClearingInProgress(int dbId);

    ClDbEntity getDbAfterDbForClearing(int dbType, int issueYear, int issueNumber);
}
