package com.wiley.cms.cochrane.cmanager.data.db;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;

/**
 * Type comments here.
 *
 * @author <a href='mailto:osoletskay@wiley.com'>Olga Soletskay</a>
 * @version 1.0
 */
public class ClDbVO implements java.io.Serializable {

    private int id;
    private String title;
    private IssueVO issue;

    public ClDbVO(int id, String title, IssueVO issue) {

        setId(id);
        setTitle(title);
        setIssue(issue);
    }

    public ClDbVO(ClDbEntity dbEntity) {

        setId(dbEntity.getId());
        setTitle(dbEntity.getTitle());

        if (dbEntity.hasIssue()) {
            setIssue(new IssueVO(dbEntity.getIssue()));
        }
    }

    public IssueVO getIssue() {
        return issue;
    }

    public void setIssue(IssueVO issue) {
        this.issue = issue;
    }

    public int getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }
}
