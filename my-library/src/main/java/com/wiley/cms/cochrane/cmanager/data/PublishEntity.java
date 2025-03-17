package com.wiley.cms.cochrane.cmanager.data;

import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.process.entity.DbEntity;

import java.beans.Transient;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Entity
@Table(name = "COCHRANE_PUBLISH")
@NamedQueries({
        @NamedQuery(
                name = "deletePublishByDb",
                query = "delete from PublishEntity e where e.db=:db"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_BY_DBNAME,
                query = "SELECT e FROM PublishEntity e WHERE e.db.database.name = :dn ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_BY_DBTYPE_AND_FILE,
                query = "SELECT e FROM PublishEntity e WHERE e.db.database.id=:td AND e.fileName = :fn"
                        + " ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_BY_DB_AND_TYPE,
                query = "SELECT e FROM PublishEntity e WHERE e.db.id=:db AND e.publishType=:tp ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_BY_PARENT,
                query = "SELECT e FROM PublishEntity e WHERE e.parentId=:pa AND e.id<>:id"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_SENT_AFTER_BY_DB_AND_TYPE_AND_FILE,
                query = "SELECT e.id FROM PublishEntity e WHERE e.db.database.id=:db AND e.publishType IN (:tp)"
                        + " AND e.sendingDate >:dt AND e.sent=true AND e.fileName =:fn"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_SELECT_ENTIRE,
                query = "SELECT e FROM PublishEntity e WHERE e.publishType=:tp AND e.db.database=:db"
                        + " AND e.db.issue is null ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_DELETE_BY_IDS,
                query = "DELETE FROM PublishEntity pe WHERE pe.id IN (:id) OR pe.parentId IN (:id)"
        ),
        @NamedQuery(
                name = PublishEntity.QUERY_LATEST_SENT_BY_DB_AND_PUB_TYPES,
                query = "SELECT e FROM PublishEntity e"
                        + " WHERE e.sent = TRUE AND e.db.database.name = :db AND e.publishType IN (:id)"
                        + " ORDER BY e.sendingDate DESC"
        )
    })

public class PublishEntity extends DbEntity {

    static final String QUERY_SELECT_BY_DBNAME = "findPublishByDbName";
    static final String QUERY_SELECT_BY_DBTYPE_AND_FILE = "findPublishByDbTypeAndFile";
    static final String QUERY_SELECT_ENTIRE = "findEntirePublishByDbAndType";
    static final String QUERY_SELECT_BY_DB_AND_TYPE = "findPublishByDbAndType";
    static final String QUERY_SELECT_BY_PARENT = "findPublishByParent";
    static final String QUERY_SELECT_SENT_AFTER_BY_DB_AND_TYPE_AND_FILE = "findPublishSentAfterDateByDbAndTypeAndFile";
    static final String QUERY_LATEST_SENT_BY_DB_AND_PUB_TYPES = "latestPublishSentByDbAndPubTypes";
    static final String QUERY_DELETE_BY_IDS = "deletePublishByIds";

    private static final long serialVersionUID = 1L;

    private ClDbEntity db;
    private Integer publishTypeId;
    private String fileName;
    private long fileSize;
    private boolean generating;
    private boolean isGenerated;
    private Date generationDate;
    private boolean sending;
    private Boolean isSent;
    private Date startSendingDate;
    private Date sendingDate;
    private boolean unpacking;
    private Date startUnpackingDate;
    private Date unpackingDate;
    private boolean deleting;
    private boolean waiting;
    private int hwFreqOrdinal;
    private Integer parentId = DbEntity.NOT_EXIST_ID;

    public PublishEntity() {
    }

    public PublishEntity(ClDbEntity clDb, Integer type) {
        setId(DbEntity.NOT_EXIST_ID);

        setFileName("");
        setPublishType(type);
        setDb(clDb);
        setGenerating(false);
        setGenerated(false);
        setSending(false);
        setSent(null);
        setGenerationDate(null);
        setSendingDate(null);

        setWaiting(false);
    }

    public static Query queryPublishEntireEntity(DatabaseEntity db, Integer type, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_ENTIRE).setParameter(
                "db", db).setParameter("tp", type).setMaxResults(2);
    }

    public static Query queryPublishEntity(String dbName, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DBNAME).setParameter("dn", dbName);
    }

    public static Query queryPublishEntity(int dbType, String fileName, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_SELECT_BY_DBTYPE_AND_FILE).setParameter(
                "td", dbType).setParameter("fn", fileName), 0, 1);
    }

    public static Query queryPublishEntityByDb(Integer dbId, Integer type, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_TYPE).setParameter("db", dbId).setParameter("tp", type);
    }

    public static Query queryPublishEntity(Integer parentId, Integer publishId,  EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PARENT).setParameter(
                "pa", parentId).setParameter("id", publishId);
    }

    public static Query queryLatestSentPublishEntityByDbAndPubTypes(String dbName,
                                                                    List<Integer> pubTypeIds,
                                                                    EntityManager manager) {
        return manager.createNamedQuery(QUERY_LATEST_SENT_BY_DB_AND_PUB_TYPES)
                .setParameter("db", dbName)
                .setParameter(PARAM_ID, pubTypeIds);
    }

    public static Query querySentPublishIdsByDbAndPubTypes(Collection<Integer> pubTypesIds, int baseType,
        String fileName, Date afterDate, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_SENT_AFTER_BY_DB_AND_TYPE_AND_FILE).setParameter(
            "tp", pubTypesIds).setParameter("db", baseType).setParameter("fn", fileName).setParameter("dt", afterDate);
    }

    public static Query queryDeletePublish(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_IDS).setParameter("id", ids);
    }

    @Column(name = "parent_id")
    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    @Transient
    public boolean hasRelation() {
        return parentId != DbEntity.NOT_EXIST_ID;
    }

    @Transient
    public boolean hasParent() {
        return hasRelation() && parentId != getId();
    }

    @Transient
    public Integer relationId() {
        return hasRelation() ? parentId : getId();
    }

    @Transient
    public void copyByTemplate(PublishEntity template, boolean onGenerating) {
        if (template != null) {
            setFileName(template.getFileName());
            if (onGenerating) {
                setGenerating(true);
            } else {
                setGenerating(template.isGenerating());
                setGenerated(template.isGenerated());
                setGenerationDate(template.getGenerationDate());
            }
        }
    }

    @ManyToOne
    @JoinColumn(name = "db_id")
    public ClDbEntity getDb() {
        return db;
    }

    public void setDb(ClDbEntity db) {
        this.db = db;
    }

    @Column(name = "type_id")
    public Integer getPublishType() {
        return publishTypeId;
    }

    public void setPublishType(Integer publishTypeId) {
        this.publishTypeId = publishTypeId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Column(name = "gen")
    public boolean isGenerated() {
        return isGenerated;
    }

    public void setGenerated(boolean generated) {
        isGenerated = generated;
    }

    public Date getGenerationDate() {
        return generationDate;
    }

    public void setGenerationDate(Date generationDate) {
        this.generationDate = generationDate;
    }

    public boolean sent() {
        return isSent != null && isSent;
    }

    public Boolean getSent() {
        return this.isSent;
    }

    public void setSent(Boolean isSent) {
        this.isSent = isSent;
    }

    @Column(name = "startSendingDate")
    public Date getStartSendingDate() {
        return startSendingDate;
    }

    public void setStartSendingDate(Date startSendingDate) {
        this.startSendingDate = startSendingDate;
    }

    public Date getSendingDate() {
        return sendingDate;
    }

    public void setSendingDate(Date sendingDate) {
        this.sendingDate = sendingDate;
    }

    public boolean isGenerating() {
        return generating;
    }

    public void setGenerating(boolean generating) {
        this.generating = generating;
    }

    public boolean isSending() {
        return sending;
    }

    public void setSending(boolean sending) {
        this.sending = sending;
    }

    public void setDeleting(boolean deleting) {
        this.deleting = deleting;
    }

    public boolean isDeleting() {
        return deleting;
    }

    @Column(name = "startUnpackingDate")
    public Date getStartUnpackingDate() {
        return startUnpackingDate;
    }

    public void setStartUnpackingDate(Date startUnpackingDate) {
        this.startUnpackingDate = startUnpackingDate;
    }

    public Date getUnpackingDate() {
        return unpackingDate;
    }

    public void setUnpackingDate(Date unpackingDate) {
        this.unpackingDate = unpackingDate;
    }

    public boolean isUnpacking() {
        return unpacking;
    }

    public void setUnpacking(boolean unpacking) {
        this.unpacking = unpacking;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    @Column(name = "size")
    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long size) {
        fileSize = size;
    }

    @Column(name = "high")
    public int getHwFrequency() {
        return hwFreqOrdinal;
    }

    public void setHwFrequency(int value) {
        hwFreqOrdinal = value;
    }

    @Transient
    public boolean highPriority() {
        return hwFreqOrdinal == HWFreq.HIGH.ordinal();
    }

    @Transient
    public boolean noPrioritySet() {
        return hwFreqOrdinal == HWFreq.NONE.ordinal();
    }

    @Transient
    public void updateOnSent() {
        setSending(false);
        setSent(true);
        setSendingDate(new Date());
        setWaiting(false);
    }
}
