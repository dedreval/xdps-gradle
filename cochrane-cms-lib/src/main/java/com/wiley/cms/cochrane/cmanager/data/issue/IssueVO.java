package com.wiley.cms.cochrane.cmanager.data.issue;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;
import com.wiley.cms.process.entity.DbEntity;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class IssueVO implements java.io.Serializable {
    private int id;
    private int number;
    private int year;
    private Date publishDate;

    public IssueVO() {
    }

    public IssueVO(int id) {
        setId(id);
    }

    public IssueVO(int year, int number, Date publishDate) {
        setYear(year);
        setNumber(number);
        setPublishDate(publishDate);
    }

    public IssueVO(int id, int year, int number, Date publishDate) {
        this(year, number, publishDate);
        setId(id);
    }

    public IssueVO(int number, Date publishDate) {
        this(CmsUtils.getYearByIssueNumber(number), CmsUtils.getIssueByIssueNumber(number), publishDate);
    }

    public IssueVO(IssueEntity entity) {
        this(entity.getId(), entity.getYear(), entity.getNumber(), entity.getPublishDate());
    }

    public IssueVO(IssueWrapper issueWrapper) {
        this(issueWrapper.getId(), issueWrapper.getYear(), issueWrapper.getNumber(), issueWrapper.getPublishDate());
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Date getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Date publishDate) {
        this.publishDate = publishDate;
    }

    public int getFullNumber() {
        return CmsUtils.getIssueNumber(getYear(), getNumber());
    }

    public String toString() {
        return number + ", " + year;
    }

    public boolean exist() {
        return id > DbEntity.NOT_EXIST_ID;
    }
}
