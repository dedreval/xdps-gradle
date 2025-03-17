package com.wiley.cms.cochrane.cmanager.data.issue;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.process.ModelController;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IIssueStorage.class)
public class IssueStorage extends ModelController implements IIssueStorage {

    private static final String VALUE_PARAM_NAME = "value";

    public IssueVO getIssueVO(int id) {
        IssueEntity entity = getManager().find(IssueEntity.class, id);
        return new IssueVO(entity);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public IssueEntity setIssueArchiving(int issueId, boolean value, boolean unarchive) {
        //getManager().createNamedQuery("issueSetArchiving")
        //        .setParameter(IssueEntity.ISSUE_PARAM_NAME, issue)
        //        .setParameter(VALUE_PARAM_NAME, value)
        //        .executeUpdate();

        IssueEntity issue = getManager().find(IssueEntity.class, issueId);
        if (issue != null) {
            if (!value) {                  // unset an archiving flag
                issue.setArchiving(false);

            } else if (unarchive && !issue.isArchiving() && issue.isArchived()) {
                issue.setArchiving(true);  // an unarchiving is to be start

            } else if (!unarchive && !issue.isClosed()) {
                issue.setArchiving(true);  // an archiving is to be start

            } else {
                issue = null;              // an archiving/unarchiving cannot be started
            }
        }
        if (issue != null) {
            getManager().merge(issue);
        }
        return issue;
    }

    public void setIssueArchived(IssueEntity issue, boolean value) {
        getManager().createNamedQuery("issueSetArchived")
                .setParameter(IssueEntity.ISSUE_PARAM_NAME, issue)
                .setParameter(VALUE_PARAM_NAME, value)
                .executeUpdate();
    }

    public void deleteIssue(IssueEntity issue) {
        getManager().createNamedQuery("deleteIssue")
                .setParameter(IssueEntity.ISSUE_PARAM_NAME, issue)
                .executeUpdate();
    }

    public void setIssueMeshtermsDownloaded(int id, boolean value) {
        IssueEntity entity = find(IssueEntity.class, id);
        entity.setMeshtermsDownloaded(value);
        getManager().merge(entity);
        getManager().flush();
    }

    public void setIssueMeshtermsDownloading(int id, boolean value) {
        IssueEntity entity = find(IssueEntity.class, id);
        setIssueMeshtermsDownloading(entity, value);
    }

    public void setIssueMeshtermsDownloading(IssueEntity issue, boolean value) {
        issue.setMeshtermsDownloading(value);
        getManager().merge(issue);
        getManager().flush();
    }

    /**
     * Returns issue.year*100 + issue.number;
     *
     * @param issueId issueId
     * @return year*100 + number, for example if year=2009 and issue=4 than result=200904
     */
    public int getIssueNumber(int issueId) {
        IssueEntity ie = find(IssueEntity.class, issueId);
        return Integer.parseInt(String.format("%d%02d", ie.getYear(), ie.getNumber()));
    }
}
