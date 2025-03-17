package com.wiley.cms.cochrane.cmanager.data.db;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DbVO implements java.io.Serializable {
    private Integer id;
    private String title;
    private Date date;
    private Integer issueId;
    private IssueVO issue;

    private Date statusDate;

    private boolean approved;
    private boolean clearing;

    private Integer allCount;
    private Integer renderedCount;

    public DbVO(ClDbEntity dbEntity) {
        setId(dbEntity.getId());
        setTitle(dbEntity.getTitle());
        setDate(dbEntity.getDate());
        if (dbEntity.getIssue() != null) {
            setIssueId(dbEntity.getIssue().getId());
            setIssue(new IssueVO(dbEntity.getIssue()));
        }
        setStatusDate(dbEntity.getStatusDate());
        setApproved(dbEntity.getApproved());
        setClearing(dbEntity.isClearing());
        setAllCount(dbEntity.getAllCount());
        setRenderedCount(dbEntity.getRenderedCount());
    }

    public IssueVO getIssue() {
        return issue;
    }

    public void setIssue(IssueVO issue) {
        this.issue = issue;
    }

    public Integer getId() {
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

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    public Integer getIssueId() {
        return issueId;
    }

    public void setIssueId(final Integer issueId) {
        this.issueId = issueId;
    }

    public boolean getApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean isClearing() {
        return clearing;
    }

    public void setClearing(boolean clearing) {
        this.clearing = clearing;
    }

    public Date getStatusDate() {
        return statusDate;
    }

    public void setStatusDate(Date statusDate) {
        this.statusDate = statusDate;
    }


    public Integer getAllCount() {
        return allCount;
    }

    public void setAllCount(Integer allCount) {
        this.allCount = allCount;
    }

    public Integer getRenderedCount() {
        return renderedCount;
    }

    public void setRenderedCount(Integer renderedCount) {
        this.renderedCount = renderedCount;
    }
}
