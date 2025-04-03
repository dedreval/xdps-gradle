package com.wiley.cms.cochrane.cmanager.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.process.entity.DbEntity;


/**
 * Cochrane issue entity bean.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Entity
@Table(name = "COCHRANE_ISSUE")
@NamedQueries({
        @NamedQuery(
                name = IssueEntity.QUERY_SELECT_ALL,
                query = "SELECT e FROM IssueEntity e ORDER BY e.year DESC, e.number DESC, e.id DESC"
        ),
        @NamedQuery(
                name = IssueEntity.QUERY_SELECT_PREVIOUS,
                query = "SELECT e FROM IssueEntity e where e.year <:ye OR (e.year =:ye AND e.number <:nu)"
                        + " ORDER BY e.year DESC, e.number DESC"
        ),
        @NamedQuery(
                name = "selectNextIssues",
                query = "SELECT id FROM IssueEntity where year>:year "
                        + "or (year=:year and number>:number) order by year ,number "
        ),
        @NamedQuery(
                name = "selectIssueByDb",
                query = "SELECT i FROM IssueEntity i where i.id=(select issue.id from ClDbEntity where id=:dbId)"
        ),
        @NamedQuery(
                name = "issueSetArchiving",
                query = "update IssueEntity i set i.archiving=:value where i=:issue"
        ),
        @NamedQuery(
                name = "issueSetArchived",
                query = "update IssueEntity i set i.archived=:value where i=:issue"
        ),
        @NamedQuery(
                name = "deleteIssue",
                query = "delete from IssueEntity i where i=:issue"
        ),
        @NamedQuery(
                name = "selectLastNonPublishedIssue",
                query = "SELECT i FROM IssueEntity i order by i.year desc, i.number desc"
        ),
        @NamedQuery(
                name = "selectIssuesWithEditorials",
                query = "select i from IssueEntity i, ClDbEntity d where i.id=d.issue.id "
                                + "and d.database.id=9 and d.renderedCount>0 order by i.date desc"
        ),
        @NamedQuery(
                name = IssueEntity.QUERY_SELECT_BY_YEAR_AND_NUMBER,
                query = "SELECT i FROM IssueEntity i WHERE i.year=:ye AND i.number=:nu ORDER BY i.id DESC"
        )
    })

public class IssueEntity implements java.io.Serializable {
    public static final String ISSUE_PARAM_NAME = "issue";

    static final String QUERY_SELECT_ALL = "findIssues";
    static final String QUERY_SELECT_PREVIOUS = "findPreviousIssue";
    static final String QUERY_SELECT_BY_YEAR_AND_NUMBER = "findIssueByYearAndNumber";

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String title;
    private Date date;

    private int number;
    private int year;

    private Date publishDate;

    /**
     * Issue statuses
     */

    private boolean archived;
    private boolean archiving;
    private boolean meshtermsDownloaded;
    private boolean meshtermsDownloading;

    public static Query queryAll(int beginIndex, int limit, EntityManager manager) {
        return DbEntity.appendBatchResults(manager.createNamedQuery(QUERY_SELECT_ALL), beginIndex, limit);
    }

    public static Query getIssuesWithEditorials(EntityManager manager) {
        return manager.createNamedQuery("selectIssuesWithEditorials");
    }

    public static Query getPreviousIssue(int year, int number, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PREVIOUS).setParameter("ye", year).setParameter(
                "nu", number).setMaxResults(1);
    }

    public static Query getIssue(int year, int number, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_YEAR_AND_NUMBER).setParameter("ye", year).setParameter(
                "nu", number).setMaxResults(1);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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

    public int getNumber() {
        return number;
    }

    public void setNumber(final int number) {
        this.number = number;
    }

    public int getYear() {
        return year;
    }

    public void setYear(final int year) {
        this.year = year;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    public boolean isArchiving() {
        return archiving;
    }

    public void setArchiving(boolean archiving) {
        this.archiving = archiving;
    }

    public Date getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Date publishDate) {
        this.publishDate = publishDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    @Column(nullable = false)
    public boolean isMeshtermsDownloaded() {
        return meshtermsDownloaded;
    }

    public void setMeshtermsDownloaded(boolean meshtermsDownloaded) {
        this.meshtermsDownloaded = meshtermsDownloaded;
    }

    @Column(nullable = false)
    public boolean isMeshtermsDownloading() {
        return meshtermsDownloading;
    }

    public void setMeshtermsDownloading(boolean meshtermsDownloading) {
        this.meshtermsDownloading = meshtermsDownloading;
    }

    @Transient
    public int getFullNumber() {
        return CmsUtils.getIssueNumber(getYear(), getNumber());
    }

    @Transient
    public boolean isClosed() {
        return isArchived() || isArchiving();
    }

    @Override
    public String toString() {
        return String.format("%d %d [%d]", getYear(), getNumber(), getId());
    }
}
