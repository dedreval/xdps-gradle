package com.wiley.cms.cochrane.cmanager.entity;

import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;

/**
 * Base cochrane record entity.
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 */

@Entity
@Table(name = "COCHRANE_LAST_RECORD")
@NamedQueries({
        @NamedQuery(
            name = LastRecordEntity.QUERY_VO_ALL,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM LastRecordEntity e"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_BY_NUMBER_AND_TYPE,
            query = "SELECT e FROM LastRecordEntity e WHERE e.db.id=:db AND e.record.number=:nu"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_VO_BY_NUMBER_AND_TYPE,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM LastRecordEntity e"
                    + " WHERE e.db.id=:db AND e.record.number=:nu"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_VO_BY_NUMBERS_AND_TYPE,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM LastRecordEntity e"
                    + " WHERE e.db.id=:db AND e.record.number IN :nu"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_LANG_BY_NUMBER_AND_TYPE,
            query = "SELECT e.record.language FROM LastRecordEntity e WHERE e.db.id=:db AND e.record.number=:nu"
                + " AND e.record.type >=:lt AND e.record.type <=:ht"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_COUNT_TR_BY_LANG,
            query = "SELECT COUNT(e) FROM LastRecordEntity e WHERE e.db.id=:db AND e.record.language = :la"
                    + " ORDER BY e.label"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_COUNT_TR_UNIQUE,
            query = "SELECT COUNT (DISTINCT e.number) FROM LastRecordEntity e WHERE e.db.id=:db"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_TR_VO_BY_LANG,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM LastRecordEntity e"
                    + " WHERE e.db.id=:db AND e.record.language = :la ORDER BY e.label"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_TR_VO_BY_NUMBER_AND_LANG,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO(e) FROM LastRecordEntity e"
                    + " WHERE e.db.id=:db AND e.record.number=:nu AND e.record.language = :la"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_DELETE_BY_DB,
            query = "DELETE FROM LastRecordEntity e WHERE e.db.id=:db"
        ),
        @NamedQuery(
            name = LastRecordEntity.QUERY_DELETE_BY_NUMBER,
            query = "DELETE FROM LastRecordEntity e WHERE e.number=:nu"
        )
    })

public class LastRecordEntity extends AbstractRecord {
    static final String QUERY_VO_ALL = "selectAllLastRecordVO";
    static final String QUERY_BY_NUMBER_AND_TYPE = "selectLastRecordByNumberAndType";
    static final String QUERY_VO_BY_NUMBER_AND_TYPE = "selectLastRecordVOByNumberAndType";
    static final String QUERY_VO_BY_NUMBERS_AND_TYPE = "selectLastRecordVOsByNumbersAndType";
    static final String QUERY_LANG_BY_NUMBER_AND_TYPE = "selectLastRecordLangByNumberAndType";
    static final String QUERY_COUNT_TR_BY_LANG = "selectCountLastTranslationByLang";
    static final String QUERY_COUNT_TR_UNIQUE = "selectCountLastTranslationUnique";
    static final String QUERY_TR_VO_BY_LANG = "selectLastTranslationVOsByLang";
    static final String QUERY_TR_VO_BY_NUMBER_AND_LANG = "selectLastTranslationVOsByNumberAndLang";
    static final String QUERY_DELETE_BY_DB = "deleteLastRecordByDb";
    static final String QUERY_DELETE_BY_NUMBER = "deleteLastRecordByNumber";

    private DbRecordEntity record;

    // for usability
    private int issue;

    public LastRecordEntity() {
    }

    public LastRecordEntity(DbRecordEntity e, ClDbEntity db) {

        setLabel(e.getLabel());
        setNumber(e.getNumber());
        setDb(db);

        setRecord(e);
        setIssue(e.getDb().getIssue().getFullNumber());
    }

    public static Query queryAllRecordVOs(int skip, int batchSize, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_VO_ALL), skip, batchSize);
    }

    public static Query queryRecordVOs(int number, int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_BY_NUMBER_AND_TYPE).setParameter("nu", number).setParameter(
                "db", dbType);
    }

    public static Query queryRecordVOs(Collection<Integer> numbers, int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_VO_BY_NUMBERS_AND_TYPE).setParameter("nu", numbers).setParameter(
                "db", dbType);
    }

    public static Query queryRecords(int number, int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_BY_NUMBER_AND_TYPE).setParameter("nu", number).setParameter(
                "db", dbType);
    }

    public static Query queryLanguages(int number, int dbType, int formatLimiterFrom, int formatLimiterTo,
                                       EntityManager manager) {
        return manager.createNamedQuery(QUERY_LANG_BY_NUMBER_AND_TYPE).setParameter("nu", number).setParameter(
                "db", dbType).setParameter("lt", formatLimiterFrom).setParameter("ht", formatLimiterTo);
    }

    public static Query queryTranslationCount(String lang, int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_TR_BY_LANG).setParameter("la", lang).setParameter("db", dbType);
    }

    public static Query queryTranslationUniqueCount(int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_TR_UNIQUE).setParameter("db", dbType);
    }

    public static Query queryTranslationVOs(String lang, int dbType, int start, int size, EntityManager manager) {
        return manager.createNamedQuery(QUERY_TR_VO_BY_LANG).setParameter(
            "la", lang).setParameter("db", dbType).setFirstResult(start).setMaxResults(size);
    }

    public static Query queryTranslationVO(int number, String lang, int dbType, EntityManager manager) {
        return manager.createNamedQuery(QUERY_TR_VO_BY_NUMBER_AND_LANG).setParameter("nu", number).setParameter(
            "la", lang).setParameter("db", dbType).setMaxResults(1);
    }

    public static Query deleteRecordsByDb(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DB).setParameter("db", dbId);
    }

    public static Query deleteRecord(int number, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_NUMBER).setParameter("nu", number);
    }

    @OneToOne
    @JoinColumn(name = "record_id", nullable = false)
    public DbRecordEntity getRecord() {
        return record;
    }

    public void setRecord(DbRecordEntity record) {
        this.record = record;
    }

    @Column(name = "issue", nullable = false)
    public int getIssue() {
        return issue;
    }

    public void setIssue(int issue) {
        this.issue = issue;
    }
}
