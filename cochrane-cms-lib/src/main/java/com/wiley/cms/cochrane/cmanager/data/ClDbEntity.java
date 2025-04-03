package com.wiley.cms.cochrane.cmanager.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

/**
 * Cochrane database entity bean.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_DB")
@NamedQueries({
        @NamedQuery(
            name = "dbByTitleAndIssue",
            query = "select db from ClDbEntity db where db.database.name=:db and db.issue=:issue"
        ),
        @NamedQuery(
            name = ClDbEntity.QUERY_SELECT_BY_ISSUE_ID_AND_DB_ID,
            query = "SELECT db FROM ClDbEntity db WHERE db.database.id=:db AND db.issue.id=:i"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_BY_DB_AND_EMPTY_ISSUE,
                query = "select db from ClDbEntity db where db.database=:db and db.issue is null"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_VO_LAST_BY_DB,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.db.ClDbVO(db) FROM ClDbEntity db"
                        + " WHERE db.database.name=:db ORDER BY db.issue.year DESC, db.issue.number DESC, db.id DESC"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_BY_ISSUE_NUMBER_AND_DB,
                query = "select db from ClDbEntity db where db.issue.year=:year AND"
                    + " db.issue.number=:num AND db.database.name=:name"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_BY_DB_AND_APPROVED,
                query = "select db from ClDbEntity db where db.database.name=:db AND db.approved=true"
                    + " ORDER BY db.issue.year DESC, db.issue.number DESC"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_EXISTS_AFTER_ISSUE,
                query = "SELECT db FROM ClDbEntity db WHERE db.database.id=:db AND (db.issue.year >:y"
                    + " OR (db.issue.year =:y AND db.issue.number >:m )) AND (db.allCount > 0 OR db.clearing=true)"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_BY_ISSUE,
                query = "SELECT db FROM ClDbEntity db WHERE db.issue.id=:i ORDER BY db.priority"
        ),
        @NamedQuery(
                name = ClDbEntity.QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE,
                query = "SELECT db FROM ClDbEntity db WHERE db.issue.id=:i OR db.issue.id=:spd ORDER BY db.priority"
        ),
        @NamedQuery(
                name = "dbSetArchived",
                query = "update ClDbEntity db set db.archived=:value where db=:db"
        ),
        @NamedQuery(
                name = "dbSetArchiving",
                query = "update ClDbEntity db set db.archiving=:value where db=:db"
        ),
        @NamedQuery(
                name = "deleteDbsByIssue",
                query = "delete from ClDbEntity db where db.issue=:issue"
        )
    })
public class ClDbEntity implements java.io.Serializable {

    static final String QUERY_SELECT_BY_ISSUE = "dbByIssue";
    static final String QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE = "dbByIssueAndSpd";
    static final String QUERY_SELECT_BY_DB_AND_EMPTY_ISSUE = "dbByDbAndEmptyIssue";
    static final String QUERY_SELECT_BY_ISSUE_NUMBER_AND_DB = "dbByIssueNumberAndDb";
    static final String QUERY_SELECT_BY_ISSUE_ID_AND_DB_ID = "dbByIssueIdAndDbId";
    static final String QUERY_SELECT_VO_LAST_BY_DB = "dbVOLastByDb";
    static final String QUERY_SELECT_BY_DB_AND_APPROVED = "dbByDbAndApproved";
    static final String QUERY_SELECT_EXISTS_AFTER_ISSUE = "dbExistsAfterIssue";

    private static final long serialVersionUID = 1L;

    private Integer id;

    private DatabaseEntity database;
    private Date date;
    private IssueEntity issue;
    private Date statusDate;
    /**
     * Database statuses
     */
    private boolean approved;
    private boolean archived;
    private boolean clearing;
    private boolean archiving;
    private boolean initialPackageDelivered;

    private Integer priority = 0;
    private Integer allCount = 0;
    private Integer renderedCount = 0;

    public static Query queryClDb(Integer issueId, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_BY_ISSUE).setParameter("i", issueId);
    }

    static Query queryClDb(Integer issueId, Integer spdIssueId, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE).setParameter(
                "i", issueId).setParameter("spd", spdIssueId);
    }

    public static Query queryClDb(DatabaseEntity database, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_BY_DB_AND_EMPTY_ISSUE).setParameter(
            "db", database);
    }

    public static Query queryLastClDb(String dbName, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_VO_LAST_BY_DB).setParameter(
            "db", dbName).setMaxResults(CochraneCMSPropertyNames.getAmountOfLastActualMonths());
    }

    public static Query queryLastApprovedClDb(String dbName, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_BY_DB_AND_APPROVED).setParameter("db", dbName);
    }

    public static Query queryClDb(int dbType, int issueId, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_BY_ISSUE_ID_AND_DB_ID).setParameter(
            "db", dbType).setParameter("i", issueId);
    }

    public static Query queryClDb(int dbType, int issueYear, int issueNumber, EntityManager manager) {
        return manager.createNamedQuery(ClDbEntity.QUERY_SELECT_EXISTS_AFTER_ISSUE).setParameter(
             "db", dbType).setParameter("y", issueYear).setParameter("m", issueNumber).setMaxResults(1);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    @Transient
    public String getTitle() {
        return database.getName();
    }

    public Date getDate() {
        return date;
    }


    public void setDate(final Date date) {
        this.date = date;
    }

    public boolean isClearing() {
        return clearing;
    }

    public void setClearing(boolean clearing) {
        this.clearing = clearing;
    }

    @ManyToOne
    @JoinColumn(name = "issue_id")
    public IssueEntity getIssue() {
        return issue;
    }

    public void setIssue(final IssueEntity issue) {
        this.issue = issue;
    }

    @Transient
    public boolean hasIssue() {
        return issue != null;
    }

    public boolean getApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public Date getStatusDate() {
        return statusDate;
    }

    public void setStatusDate(Date statusDate) {
        this.statusDate = statusDate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean isArchiving() {
        return archiving;
    }

    public void setArchiving(boolean archiving) {
        this.archiving = archiving;
    }

    @Column(nullable = false)
    public boolean isInitialPackageDelivered() {
        return initialPackageDelivered;
    }

    public void setInitialPackageDelivered(boolean initialPackageDelivered) {
        this.initialPackageDelivered = initialPackageDelivered;
    }

    @ManyToOne
    @JoinColumn(name = "database_id")
    public DatabaseEntity getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseEntity database) {
        this.database = database;
    }

    @Transient
    public boolean isEntire() {
        return getId().equals(database.getId());
    }

    @Transient
    @Override
    public String toString() {
        return String.format("%s [%d], %s", getTitle(), getId(),
                isEntire() ? "entire" : "" + getIssue().getFullNumber());
    }
}