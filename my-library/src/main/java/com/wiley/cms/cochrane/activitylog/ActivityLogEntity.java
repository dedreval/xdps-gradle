package com.wiley.cms.cochrane.activitylog;

import java.beans.Transient;
import java.io.Serializable;
import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

/**
 * @author <a href='mailto:oarinosov@wiley.ru'>Oleg Arinosov</a>
 * @version 06.12.2006
 */
@Entity
@Table(name = "COCHRANE_ACTIVITY_LOG")
@NamedQueries({

        @NamedQuery(
            name = ActivityLogEntity.QUERY_SELECT_BY_EVENT_AND_OBJS,
            query = "SELECT al FROM ActivityLogEntity al WHERE al.event.id in (:ev) AND al.entityId IN (:ei)"
                + " ORDER BY al.id DESC"
        ),
        @NamedQuery(
            name = ActivityLogEntity.QUERY_LAST_COMMENTS_BY_EVENT,
            query = "SELECT al.comments FROM ActivityLogEntity al WHERE al.event.id =:ev ORDER BY al.id DESC"
        ),
        @NamedQuery(
            name = ActivityLogEntity.QUERY_ALL_COUNT,
            query = "SELECT COUNT(al.id) FROM ActivityLogEntity al WHERE al.id > 0"
        ),
        @NamedQuery(
            name = ActivityLogEntity.Q_ENTITY_NAME,
            query = "SELECT DISTINCT a.entityName FROM ActivityLogEntity a"
                    + " WHERE a.entityId = :entity AND a.entityLevel = :level"
        )
    })

public class ActivityLogEntity extends BaseLogEntity implements Serializable {
    /**
     *
     */
    public enum Field {
        LOG_LEVEL("logLevel"),
        DATE("date"),
        AUTHOR("who"),
        ENTITY_LEVEL("entityLevel"),
        ENTITY_ID("entityId"),
        ENTITY_NAME("entityName"),
        EVENT("event"),
        COMMENTS("comments"),
        FLOW_ID("packageId");

        private final String colName;

        Field(String colName) {
            this.colName = colName;
        }

        public String getColumnName() {
            return colName;
        }
    }

    static final String QUERY_SELECT_BY_EVENT_AND_OBJS = "findActivityByEventAndObjs";
    static final String QUERY_LAST_COMMENTS_BY_EVENT = "lastCommentsByEventAndLevel";
    static final String QUERY_ALL_COUNT = "activityLogCount";
    static final String Q_ENTITY_NAME = "activityLogEntityName";

    private static final String LEVEL_PRM = "level";
    private static final String ENTITY_PRM = "entity";
    private static final long serialVersionUID = 1L;

    static Query queryLastCommentsByEvent(int eventId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_LAST_COMMENTS_BY_EVENT).setParameter("ev", eventId).setMaxResults(1);
    }

    public static Query queryByEventAndEntity(Collection<Integer> eventIds, Collection<Integer> entityIds,
        int start, int size, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_EVENT_AND_OBJS).setParameter("ev", eventIds).setParameter(
                "ei", entityIds).setFirstResult(start).setMaxResults(size);
    }

    static Query queryEntityName(int entityId, ActivityLogEntity.EntityLevel entityLvl, EntityManager manager) {
        return manager.createNamedQuery(Q_ENTITY_NAME)
                .setParameter(ENTITY_PRM, entityId)
                .setParameter(LEVEL_PRM, entityLvl);
    }

    static Query queryCount(EntityManager manager) {
        return manager.createNamedQuery(QUERY_ALL_COUNT);
    }

    @Transient
    public String buildCommentsUI() {
        return getEntityLevel().getComments(this);
    }

    @Transient
    public String buildFlowUI() {
        return "";
    }
}
