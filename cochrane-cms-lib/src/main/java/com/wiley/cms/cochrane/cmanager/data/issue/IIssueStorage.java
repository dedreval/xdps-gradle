package com.wiley.cms.cochrane.cmanager.data.issue;

import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.process.IModelController;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IIssueStorage extends IModelController {
    IssueVO getIssueVO(int id);

    IssueEntity setIssueArchiving(int issueId, boolean value, boolean unarchive);

    void setIssueArchived(IssueEntity issue, boolean value);

    void deleteIssue(IssueEntity issue);

    void setIssueMeshtermsDownloaded(int id, boolean value);

    void setIssueMeshtermsDownloading(int id, boolean value);

    void setIssueMeshtermsDownloading(IssueEntity issue, boolean value);

    int getIssueNumber(int issueId);
}
