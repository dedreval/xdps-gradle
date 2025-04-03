package com.wiley.cms.cochrane.cmanager.entity;


import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;

/**
 * Base cochrane record entity.
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 */

@Entity
@Table(name = "COCHRANE_ABSTRACT_RECORD")
@NamedQueries({
        @NamedQuery(
                name = DbRecordEntity.QUERY_BY_IDS,
                query = "SELECT e FROM DbRecordEntity e WHERE e.id IN(:id)"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_BY_DBTYPE_AND_NUMBER_AND_VERSION,
                query = "SELECT e FROM DbRecordEntity e WHERE e.db.database.id=:td AND e.number=:nu AND e.version=:v"
                        + " ORDER BY e.id DESC"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_BY_NUMBER_AND_DB,
            query = "SELECT e FROM DbRecordEntity e WHERE e.db.id=:db AND e.number=:nu"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_BY_NUMBER_AND_DF,
                query = "SELECT e FROM DbRecordEntity e WHERE e.deliveryId=:df AND e.number=:nu"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_TR_BEFORE_ISSUE,
            query = "SELECT e FROM DbRecordEntity e WHERE e.db.issue.id <:i AND e.number=:nu AND e.version > "
                + RecordEntity.VERSION_SHADOW + " AND e.language=:la ORDER BY e.db.issue.id DESC, e.date DESC"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_VO_HISTORY,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                        + " WHERE e.db.database.id=:td AND e.version > " + RecordEntity.VERSION_LAST
                        + " ORDER BY e.number, e.version"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_TR_VO_BY_DB_AND_NUMBER_AND_LANG,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                + " WHERE e.db.id=:db AND e.number=:nu AND e.language=:la ORDER BY e.date DESC"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_VO_HISTORY_BY_NUMBER,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                + " WHERE e.db.database.id=:td AND e.number=:nu AND e.version > " + RecordEntity.VERSION_LAST
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_VO_HISTORY_BY_NUMBER_AND_VERSION,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                        + " WHERE e.db.database.id=:td AND e.number=:nu AND e.version=:v"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_VO_BY_DBTYPE_AND_NUMBERS_AND_STATUS_AND_VERSION,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                    + " WHERE e.db.database.id=:td AND e.number IN (:nu) AND e.status=:st AND e.version=:v"
                    + " ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_VO_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                        + " WHERE e.db.database.id=:td AND e.deliveryId=:df AND e.status=:st AND e.version=:v"
                        + " ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = DbRecordEntity.QUERY_COUNT_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION,
                query = "SELECT COUNT (e.number) FROM DbRecordEntity e WHERE e.db.database.id=:td"
                    + " AND e.deliveryId=:df AND e.status=:st AND e.version=:v"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_TR_VO_HISTORY_BY_LANG,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM DbRecordEntity e"
                + " WHERE e.db.database.id=:td AND e.language=:la AND e.version > " + RecordEntity.VERSION_LAST
                    + " ORDER BY e.number, e.version"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_LANG_BY_NUMBER_AND_VERSION_AND_TYPE,
            query = "SELECT e.language FROM DbRecordEntity e WHERE e.db.database.id=:td AND e.number=:nu"
                + " AND e.version=:v AND e.type >=:lt AND e.type <=:ht AND e.status < " + DbRecordEntity.STATUS_DELETED
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_DELETE_BY_DB_AND_NUMBER,
            query = "DELETE FROM DbRecordEntity e WHERE e.db.id=:db AND e.number=:nu"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_DELETE_BY_DB,
            query = "DELETE FROM DbRecordEntity e WHERE e.db.id=:db"
        ),
        @NamedQuery(
            name = DbRecordEntity.QUERY_DELETE_BY_NUMBER,
            query = "DELETE FROM DbRecordEntity e WHERE e.number=:nu"
        )
    })
public class DbRecordEntity extends AbstractRecord {
    static final String QUERY_BY_IDS = "selectDbRecordByIds";
    static final String QUERY_BY_DBTYPE_AND_NUMBER_AND_VERSION = "selectDbRecordByDbTypeAndNumberAndVersion";
    static final String QUERY_BY_NUMBER_AND_DB = "selectDbRecordByNumberAndDb";
    static final String QUERY_BY_NUMBER_AND_DF = "selectDbRecordByNumberAndDf";
    static final String QUERY_TR_BEFORE_ISSUE = "selectTranslationBeforeIssue";
    static final String QUERY_TR_VO_BY_DB_AND_NUMBER_AND_LANG = "selectTranslationVOByDbAndNumberAndLang";
    static final String QUERY_VO_HISTORY_BY_NUMBER = "selectHistoryDbRecordVOByNumber";
    static final String QUERY_VO_HISTORY = "selectHistoryDbRecordVO";
    static final String QUERY_VO_HISTORY_BY_NUMBER_AND_VERSION = "selectHistoryDbRecordVOByNumberAndVersion";
    static final String QUERY_VO_BY_DBTYPE_AND_NUMBERS_AND_STATUS_AND_VERSION =
            "selectDbRecordVOByDbTypeAndNumbersAndStatusAndVersion";
    static final String QUERY_VO_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION =
            "selectDbRecordVOByDbTypeAndDfAndStatusAndVersion";
    static final String QUERY_COUNT_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION = "countByDbTypeAndDfAndStatusAndVersion";
    static final String QUERY_LANG_BY_NUMBER_AND_VERSION_AND_TYPE = "selectLanguagesByNumberAndVersionAndType";
    static final String QUERY_TR_VO_HISTORY_BY_LANG = "selectHistoryTranslationVOByLang";
    static final String QUERY_DELETE_BY_DB_AND_NUMBER = "deleteDbRecordsByDbAndNumber";
    static final String QUERY_DELETE_BY_DB = "deleteDbRecordsByDb";
    static final String QUERY_DELETE_BY_NUMBER = "deleteDbRecordsByNumber";

    private static final long serialVersionUID = 1L;

    private int version = RecordEntity.VERSION_SHADOW;
    private String language; // null - not translated, English

    // temporary data
    private Integer dfId;

    private Date date;
    private int status = STATUS_NORMAL;
    private int type;
    private TitleEntity titleEntity;

    public DbRecordEntity() {
    }

    public DbRecordEntity(String label, int number, String lang, int status, int v, ClDbEntity db, Integer dfId) {
        setLabel(label);
        setDb(db);
        setLanguage(lang);
        setNumber(number);
        setDeliveryId(dfId);
        setDate(new Date());
        setStatus(status);
        setVersion(v);
    }

    public static Query queryRecords(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_BY_IDS).setParameter("id", ids);
    }

    public static Query queryRecordsByDb(int number, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_BY_NUMBER_AND_DB).setParameter("nu", number).setParameter("db", dbId);
    }

    public static Query queryRecordsByDf(int number, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_BY_NUMBER_AND_DF).setParameter("nu", number).setParameter("df", dfId);
    }

    public static Query queryRecords(int number, int dbType, Integer version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_BY_DBTYPE_AND_NUMBER_AND_VERSION).setParameter(
                "nu", number).setParameter("td", dbType).setParameter("v", version);
    }

    public static Query queryRecords(int dbType, Collection<Integer> numbers, int status, Integer version,
                                     EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_BY_DBTYPE_AND_NUMBERS_AND_STATUS_AND_VERSION).setParameter(
                "nu", numbers).setParameter("td", dbType).setParameter("st", status).setParameter("v", version);
    }

    public static Query queryRecords(int dbType, int dfId, int status, Integer version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION).setParameter(
                "df", dfId).setParameter("td", dbType).setParameter("st", status).setParameter("v", version);
    }

    public static Query queryRecordsCount(int dbType, int dfId, int status, Integer version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_BY_DBTYPE_AND_DF_AND_STATUS_AND_VERSION).setParameter(
                "df", dfId).setParameter("td", dbType).setParameter("st", status).setParameter("v", version);
    }

    public static Query queryTranslationBefore(int number, String lang, int issueId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_TR_BEFORE_ISSUE).setParameter("nu", number).setParameter(
                "i", issueId).setParameter("la", lang).setMaxResults(1);
    }

    public static Query queryTranslationVOsByDb(int number, String lang, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_TR_VO_BY_DB_AND_NUMBER_AND_LANG).setParameter("nu", number).setParameter(
                "db", dbId).setParameter("la", lang).setMaxResults(1);
    }

    public static Query queryHistoryTranslationVOs(int skip, int batchSize, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_VO_HISTORY).setParameter(
                "td", DatabaseEntity.CDSR_TA_KEY), skip, batchSize);
    }

    public static Query queryHistoryTranslationVOs(int number, EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_HISTORY_BY_NUMBER).setParameter("nu", number).setParameter(
                "td", DatabaseEntity.CDSR_TA_KEY);
    }

    public static Query queryHistoryTranslationVOs(int number, Integer version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_HISTORY_BY_NUMBER_AND_VERSION).setParameter(
                "nu", number).setParameter("td", DatabaseEntity.CDSR_TA_KEY).setParameter("v", version);
    }

    public static Query queryHistoryTranslationVOs(String language, int skip, int batchSize, EntityManager manager) {
        return appendBatchResults(
            manager.createNamedQuery(QUERY_TR_VO_HISTORY_BY_LANG).setParameter("la", language).setParameter(
                "td", DatabaseEntity.CDSR_TA_KEY), skip, batchSize);
    }

    public static Query queryLanguages(int number, int dbType, int version, int formatLimiterFrom, int formatLimiterTo,
                                       EntityManager manager) {
        return manager.createNamedQuery(QUERY_LANG_BY_NUMBER_AND_VERSION_AND_TYPE).setParameter(
            "nu", number).setParameter("td", dbType).setParameter("v", version).setParameter(
                    "lt", formatLimiterFrom).setParameter("ht", formatLimiterTo);
    }

    public static Query deleteRecordByDb(int number, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DB_AND_NUMBER).setParameter("nu", number).setParameter(
                "db", dbId);
    }

    public static Query deleteRecordsByDb(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DB).setParameter("db", dbId);
    }

    public static Query deleteRecord(int number, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_NUMBER).setParameter("nu", number);
    }

    @Column(name = "version", nullable = false)
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Column(name = "df_id", updatable = false)
    public Integer getDeliveryId() {
        return dfId;
    }

    public void setDeliveryId(Integer dfId) {
        this.dfId = dfId;
    }

    /*@Column(name = "prev_id")
    public Integer getPreviousId() {
        return prevId;
    }

    public void setPreviousId(Integer prevId) {
        this.prevId = prevId;
    }*/

    @Column(name = "lang", length = STRING_VARCHAR_LENGTH_SMALL, updatable = false)
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Transient
    public boolean hasLanguage() {
        return language != null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id")
    public TitleEntity getTitleEntity() {
        return titleEntity;
    }

    public void setTitleEntity(TitleEntity title) {
        titleEntity = title;
    }

    @Column(name = "updated", nullable = false)
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Column(name = "status", nullable = false)
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @Column(name = "type", nullable = false)
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Transient
    public boolean isDeleted() {
        return status >= STATUS_DELETED;
    }

    @Transient
    public boolean isHistorical() {
        return DbRecordVO.isHistorical(version);
    }

    @Transient
    public String toString() {
        return getLabel() + (hasLanguage() ? ("." + getLanguage()) : "") + "[" + getId() + "]";
    }
}
