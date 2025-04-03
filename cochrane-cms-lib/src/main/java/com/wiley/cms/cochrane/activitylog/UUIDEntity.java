package com.wiley.cms.cochrane.activitylog;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 09/14/2022
 */

@Entity
@Table(name = "COCHRANE_SID")
@NamedQueries({
        @NamedQuery(
                name = UUIDEntity.QUERY_SELECT_BY_EVENT,
                query = "SELECT e FROM UUIDEntity e WHERE e.event=:ev ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = UUIDEntity.QUERY_SELECT_BY_ENTITY_AND_EVENT,
                query = "SELECT e FROM UUIDEntity e WHERE e.entityId=:en AND e.event=:ev ORDER BY e.id DESC"
        )
    })
public class UUIDEntity extends DbEntity {
    public static final String UUID = "UUID";
         
    static final String QUERY_SELECT_BY_EVENT = "uuidByEvent";
    static final String QUERY_SELECT_BY_ENTITY_AND_EVENT = "uuidByEntityIdAndEvent";

    private static final long serialVersionUID = 1L;

    private static final int SID_LENGTH = 36;

    private String sid;
    private int event;
    private Integer entityId;

    public UUIDEntity() {
    }

    public static Query queryUUIDByEvent(int event, int max, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_SELECT_BY_EVENT).setParameter("ev", event), 0, max);
    }

    public static Query queryUUIDByEntityIdAndEvent(Integer entityId, int event, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_SELECT_BY_ENTITY_AND_EVENT).setParameter(
                "en", entityId).setParameter("ev", event), 0, 1);
    }

    @Column(name = "sid", nullable = false, length = SID_LENGTH, updatable = false)
    public String getSid() {
        return sid;
    }

    public void setSid(String title) {
        this.sid = title;
    }

    @Column(name = "event")
    public int getEvent() {
        return event;
    }

    public void setEvent(int event) {
        this.event = event;
    }

    @Column(name = "entity_id")
    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public static String extractUUID(String packageName) {
        if (packageName != null){
            int index = packageName.indexOf('#');
            return (index != -1) ? FileUtils.cutExtension(packageName.substring(index + 1), Extensions.TAR_GZ) : null;
        }
        return null;
    }
}
