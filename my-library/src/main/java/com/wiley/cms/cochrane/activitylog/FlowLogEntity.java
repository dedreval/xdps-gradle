package com.wiley.cms.cochrane.activitylog;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.activitylog.snowflake.SFType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2017
 */
@Entity
@Table(name = "COCHRANE_FLOW_LOG")
@NamedQueries({
        @NamedQuery(
            name = FlowLogEntity.QUERY_SELECT_BY_EVENTS_AND_PACKAGE_ID,
            query = "SELECT e FROM FlowLogEntity e WHERE e.event.id IN(:ev) AND e.packageId =:pi ORDER BY e.id DESC"
        ),
        @NamedQuery(
            name = FlowLogEntity.QUERY_SELECT_BY_PACKAGE_ID,
            query = "SELECT e FROM FlowLogEntity e WHERE e.packageId =:pi ORDER BY e.id DESC"
        ),
        @NamedQuery(
            name = FlowLogEntity.QUERY_SELECT_BY_ENTITY_NAME,
            query = "SELECT e FROM FlowLogEntity e WHERE e.date > :dt AND e.dbType=:db AND e.entityName =:na"
                    + " ORDER BY e.id DESC"
        ),
        @NamedQuery(
            name = FlowLogEntity.QUERY_SELECT_UNCOMPLETED,
            query = "SELECT e FROM FlowLogEntity e WHERE e.sendTo > 0 AND e.sendTo <:cm ORDER BY e.id"
        )
    })

public class FlowLogEntity extends ActivityLogEntity {
    static final String QUERY_SELECT_BY_EVENTS_AND_PACKAGE_ID = "findFlowLogsByEventsAndPackageId";
    static final String QUERY_SELECT_BY_PACKAGE_ID = "findFlowLogsByPackageId";
    static final String QUERY_SELECT_BY_ENTITY_NAME = "findFlowLogsByEntityName";
    static final String QUERY_SELECT_UNCOMPLETED = "findFlowLogsUncompleted";

    private static final long serialVersionUID = 1L;

    private Integer packageId;
    private int dbType;
    private int sendTo;

    public static Query queryByEventsAndPackageId(Collection<Integer> eventIds, Integer packageId,
                                                  EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_EVENTS_AND_PACKAGE_ID).setParameter(
                "ev", eventIds).setParameter("pi", packageId);
    }

    public static Query queryByPackageId(Integer packageId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PACKAGE_ID).setParameter("pi", packageId);
    }

    public static Query queryByEntityName(String entityName, int dbType, Date date, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ENTITY_NAME).setParameter("na", entityName).setParameter(
                "db", dbType).setParameter("dt", date).setMaxResults(1);
    }

    static Query queryUncompleted(int maxSize, EntityManager manager) {
        Query query = manager.createNamedQuery(QUERY_SELECT_UNCOMPLETED).setParameter("cm", SFType.COMPLETED);
        if (maxSize > 0) {
            query.setMaxResults(maxSize);
        }
        return query;
    }

    @Column(name = "df_id", updatable = false)
    public Integer getPackageId() {
        return packageId;
    }

    public void setPackageId(Integer dfId) {
        this.packageId = dfId;
    }

    @Column(name = "db_type", updatable = false, nullable = false)
    public int getDbType() {
        return dbType;
    }

    public void setDbType(int dbType) {
        this.dbType = dbType;
    }

    @Column(name = "send", nullable = false)
    public int getSendTo() {
        return sendTo;
    }

    public void setSendTo(int flags) {
        this.sendTo = flags;
    }

    @Transient
    @Override
    public String buildFlowUI() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPackageId()).append(" [").append(getId()).append("]");
        if (sendTo > 0) {
            sb.append("<br>SF: ");
            for (SFType type: SFType.values()) {
                appendSF(type, sb);
            }
        }
        return sb.toString();
    }

    private void appendSF(SFType type, StringBuilder sb) {
        if (type.has(sendTo)) {
            if (type.completed(sendTo))  {
                sb.append(type.shortName());

            } else {
                sb.append("<b>");
                sb.append(type.shortName());
                sb.append("</>");
            }
            sb.append(" ");
        }
    }

    @Transient
    public boolean sendToFlow() {
        return SFType.FLOW.has(sendTo);
    }

    @Transient
    public boolean sendToProduct() {
        return SFType.PRODUCT.has(sendTo);
    }

    @Transient
    public boolean sendToTranslation() {
        return SFType.TRANSLATION.has(sendTo);
    }

    @Transient
    public boolean sendToPackageProduct() {
        return SFType.PACKAGE_PRODUCT.has(sendTo);
    }

    @Transient
    @Override
    public String toString() {
        return String.format("[%d] eventId=%d %s dfId=%d sendTo=%d", getId(), getEvent().getId(), getEntityName(),
                getPackageId(), getSendTo());
    }
}
