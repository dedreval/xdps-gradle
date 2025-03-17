package com.wiley.cms.cochrane.activitylog;

import javax.persistence.Cacheable;
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

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Cacheable
@Entity
@Table(name = "COCHRANE_ACTIVITY_LOG_EVENT")
@NamedQueries({
        @NamedQuery(
            name = ActivityLogEventEntity.Q_VOS,
            query = "SELECT new com.wiley.cms.cochrane.activitylog.ActivityLogEventVO(e.id, e.name)"
                    + "FROM ActivityLogEventEntity e"
        )
    })
public class ActivityLogEventEntity implements java.io.Serializable {

    static final String Q_VOS = "activityLogEventVOs";
    private Integer id;
    private String name;
    private String description;

    public ActivityLogEventEntity() {
    }

    public ActivityLogEventEntity(String name) {
        setName(name);
    }

    public static Query qVOs(EntityManager manager) {
        return manager.createNamedQuery(Q_VOS);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(updatable = false)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(updatable = false, nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /*@Enumerated(EnumType.ORDINAL)
    @Column(updatable = false, nullable = true)
    public LogEntity.EntityLevel getEntityLevel() {
        return entityLevel;
    }

    public void setEntityLevel(LogEntity.EntityLevel level) {
        this.entityLevel = level;
    }*/
}
