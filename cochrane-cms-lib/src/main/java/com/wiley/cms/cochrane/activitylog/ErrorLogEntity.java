package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;
import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2017
 */
@Entity
@Table(name = "COCHRANE_ERROR_LOG")
@NamedQueries({
        @NamedQuery(
            name = ErrorLogEntity.QUERY_SELECT_BY_DB_AND_PID_AND_ISSUE_AND_EVENTS,
            query = "SELECT e FROM ErrorLogEntity e WHERE e.dbType =:db AND e.entityId =:id AND e.issue =:i"
                        + " AND e.event IN (:ev) ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = ErrorLogEntity.QUERY_SELECT_BY_ENTITY_IDS_AND_EVENTS_AND_ISSUE,
                query = "SELECT e FROM ErrorLogEntity e"
                        + " WHERE e.entityId IN (:id) AND e.event IN (:ev) AND e.issue =:i ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = ErrorLogEntity.QUERY_SELECT_BY_DB_AND_ISSUE,
                query = "SELECT e FROM ErrorLogEntity e WHERE e.dbType =:db AND e.issue =:i ORDER BY e.id DESC"
        ),
        @NamedQuery(
            name = ErrorLogEntity.QUERY_DELETE_BY_DB_AND_ISSUE,
            query = "DELETE FROM ErrorLogEntity e WHERE e.dbType =:db AND e.issue =:i"
        )
    })
public class ErrorLogEntity extends LogEntity implements Serializable {
    static final String QUERY_SELECT_BY_DB_AND_ISSUE = "selectErrorsByDbAndIssue";
    static final String QUERY_SELECT_BY_DB_AND_PID_AND_ISSUE_AND_EVENTS = "selectErrorsByDbAndPidAndIssueAndEvents";
    static final String QUERY_SELECT_BY_ENTITY_IDS_AND_EVENTS_AND_ISSUE = "selectErrorsByEntityIdsAndEventsAndIssue";
    static final String QUERY_DELETE_BY_DB_AND_ISSUE = "deleteErrorsByDbAndIssue";

    private static final long serialVersionUID = 1L;

    private int issue = 0;
    private int dbType = 0;
    private int event = 0;

    public static Query queryErrorLog(int dbType, int issue, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_ISSUE).setParameter(
                "db", dbType).setParameter("i", issue);
    }

    public static Query queryErrorLog(Collection<Integer> entityIds, Collection<Integer> events, int fullIssueNumb,
                                      int skip, int max, EntityManager manager) {
        Query q = manager.createNamedQuery(QUERY_SELECT_BY_ENTITY_IDS_AND_EVENTS_AND_ISSUE)
                .setParameter("id", entityIds)
                .setParameter("ev", events)
                .setParameter("i", fullIssueNumb);
        return DbEntity.appendBatchResults(q, skip, max);
    }

    public static Query queryErrorLog(int dbType, int entityId, int issue, Collection<Integer> events,
                                      int skip, int max, EntityManager manager) {
        return DbEntity.appendBatchResults(manager.createNamedQuery(
            QUERY_SELECT_BY_DB_AND_PID_AND_ISSUE_AND_EVENTS).setParameter("ev", events).setParameter(
                "db", dbType).setParameter("id", entityId).setParameter("i", issue), skip, max);
    }

    public static Query deleteErrorLog(int dbType, int issue, EntityManager manager) {
        return manager.createNamedQuery(
            QUERY_DELETE_BY_DB_AND_ISSUE).setParameter("db", dbType).setParameter("i", issue);
    }

    @Column(updatable = false)
    public int getIssue() {
        return issue;
    }

    public void setIssue(int issue) {
        this.issue = issue;
    }

    @Column(name = "db_type", updatable = false)
    public int getDbType() {
        return dbType;
    }

    public void setDbType(int dbType) {
        this.dbType = dbType;
    }

    @Column(updatable = false)
    public int getEvent() {
        return event;
    }

    public void setEvent(int event) {
        this.event = event;
    }
}
