package com.wiley.cms.cochrane.cmanager.data.entire;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.EntireRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */

@Entity
@Table(name = "COCHRANE_ENTIRE_DB")
@NamedQueries({
        @NamedQuery(
                name = "findDistinctRecordNames",
                query = "select distinct edb.name from EntireDBEntity edb where edb.database.name = :db"
        ),
        @NamedQuery(
                name = EntireDBEntity.Q_DISTINCT_RECORD_NAMES_BY_DB_ID_AND_PRODUCT_SUBTITLE_ID,
                query = "SELECT DISTINCT edb.name FROM EntireDBEntity edb WHERE edb.database.id = :id"
                        + " AND edb.productSubtitle.id = :sbId"
        ),
        @NamedQuery(
                name = "findDistinctRecordNamesByDbAndUnitStatus",
                query = "SELECT DISTINCT edb.name FROM EntireDBEntity edb WHERE edb.database.name = :db"
                        + " AND edb.unitStatus.id IN (:unitStatus)"
        ),
        @NamedQuery(
                name = "findDistinctRecordNamesFromListByDb",
                query = "SELECT DISTINCT edb.name FROM EntireDBEntity edb WHERE edb.name IN (:recordName)"
                        + " AND edb.database.name = :db"
        ),
        @NamedQuery(
                name = "findDistinctRecordNamesFromListByDbAndUnitStatus",
                query = "SELECT DISTINCT edb.name FROM EntireDBEntity edb WHERE edb.name IN (:recordName)"
                        + " AND edb.database.name = :db AND edb.unitStatus.id IN (:unitStatus)"
        ),
        @NamedQuery(
                name = EntireDBEntity.Q_UNIT_TITLE_BY_REC_NAME_AND_DB,
                query = "SELECT edb.titleEntity.title FROM EntireDBEntity edb WHERE edb.name = :name"
                        + " AND edb.database = :db ORDER BY edb.lastIssuePublished DESC"
        ),
        @NamedQuery(
                name = EntireDBEntity.Q_UNIT_STATUS_BY_REC_NAME_AND_DB,
                query = "SELECT edb.unitStatus FROM EntireDBEntity edb WHERE edb.name = :name"
                        + " AND edb.database = :db ORDER BY edb.lastIssuePublished DESC"
        ),
        @NamedQuery(
                name = EntireDBEntity.QUERY_COUNT_BY_DB,
                query = "SELECT COUNT (DISTINCT edb.name) from EntireDBEntity edb where edb.database.id = :db"
        ),
        @NamedQuery(
                name = "findEntityByIds",
                query = "SELECT edb FROM EntireDBEntity edb WHERE edb.id IN (:ids)"
        ),
        //@NamedQuery(
        //        name = "findEntityByDbAndRecordName",
        //        query = "select edb from EntireDBEntity edb where edb.database = :db and edb.name = :recordName "
        //                + "order by edb.lastIssuePublished desc"
        //),
        @NamedQuery(
                name = EntireDBEntity.QUERY_SELECT_BY_DB_AND_NAME,
                query = "select edb from EntireDBEntity edb where edb.database.id = :dbId and edb.name = :recordName "
                        + "order by edb.lastIssuePublished desc"
        ),
        @NamedQuery(
                name = EntireDBEntity.QUERY_SELECT_BY_DB_AND_NAMES,
                query = "select edb from EntireDBEntity edb where edb.database.id = :dbId and edb.name IN (:recordName)"
                    + " order by edb.lastIssuePublished desc"
        ),
        //@NamedQuery(
        //        name = EntireDBEntity.QUERY_SELECT_BY_DB_AND_NAME_AND_ISSUE,
        //        query = "select edb from EntireDBEntity edb where edb.database.id = :dbId and edb.name = :recordName "
        //                + "and edb.lastIssuePublished = :issue order by edb.lastIssuePublished desc"
        //),
        /*@NamedQuery(
                name = "findEntityByDbAndSortTitle",
                query = "select edb from EntireDBEntity edb where edb.database = :db "
                        + "and edb.unitTitleForSort = :unitTitle order by edb.lastIssuePublished desc"
        ),
        @NamedQuery(
                name = EntireDBEntity.QUERY_SELECT_BY_DB_AND_SORTTITLE,
                query = "select edb from EntireDBEntity edb where edb.database.id = :dbId "
                        + "and edb.unitTitleForSort = :unitTitle order by edb.lastIssuePublished desc"
        ),*/
        @NamedQuery(
                name = "findRecordNames",
                query = "select edb.name from EntireDBEntity edb where edb.id in (:ids)"
        ),
        @NamedQuery(
                name = "findEntityByDbAndRecordNameAndIssue",
                query = "select edb from EntireDBEntity edb where edb.name = :recordName and edb.database = :db "
                        + "and edb.lastIssuePublished= :issue"
        ),
        @NamedQuery(
                name = EntireDBEntity.QUERY_SELECT_NAMES_BY_DB_AND_LAST_ISSUE,
                query = "select edb.name from EntireDBEntity edb where edb.database.id = :dbId"
                        + " and edb.lastIssuePublished= :issue"
        ),
        @NamedQuery(
                name = "entireRecordIdsAndNamesByIds",
                query = "SELECT edb.id, edb.name FROM EntireDBEntity edb WHERE edb.id IN (:ids)"
        ),
        @NamedQuery(
                name = EntireDBEntity.Q_LAST_ISSUE_PUBLISHED_BY_DB_ID,
                query = "SELECT DISTINCT e.lastIssuePublished FROM EntireDBEntity e WHERE e.database.id = :id"
        ),
        @NamedQuery(
                name = EntireDBEntity.Q_ID_BELONGS_TO_ISSUE,
                query = "SELECT e.id FROM EntireDBEntity e WHERE e.lastIssuePublished = :lastIssuePublished"
                        + " AND e.id IN (:id)"
        ),
        @NamedQuery(
                name = "deleteEntireRecordsByIds",
                query = "DELETE FROM EntireDBEntity edb WHERE edb.id IN (:ids)"
        ),
        @NamedQuery(
                name = "deleteRecordsByIssueAndDb",
                query = "delete from EntireDBEntity edb where edb.lastIssuePublished=:lastIssuePublished "
                        + "and edb.database=:db"
        ),
        @NamedQuery(
                name = "deleteRecordsByIssueAndName",
                query = "delete from EntireDBEntity edb where edb.lastIssuePublished=:lastIssuePublished "
                        + "and edb.name=:recordName"
        ),
        @NamedQuery(
                name = "findRecordNamesByIssueAndDb",
                query = "select edb from EntireDBEntity edb where edb.lastIssuePublished=:lastIssuePublished "
                        + "and edb.database=:db order by edb.name"
        ),
        @NamedQuery(
                name = "recordsCountByIssue",
                query = "SELECT COUNT (DISTINCT edb.name) from EntireDBEntity edb where edb.database=:db "
                                + "and edb.lastIssuePublished=:lastIssuePublished"
        ),
        @NamedQuery(
                name = "findRecordNamesByIssueAndName",
                query = "select edb from EntireDBEntity edb where edb.lastIssuePublished=:lastIssuePublished "
                        + "and edb.name=:recordName"
        ),
        @NamedQuery(
                name = "findIssuesByDbName",
                query = "select distinct edb.lastIssuePublished from EntireDBEntity edb where edb.database=:db "
                                + "order by edb.lastIssuePublished desc"
        )
    })
public class EntireDBEntity implements Serializable {

    static final String QUERY_SELECT_BY_DB_AND_NAME = "entireRecordsByDbAndName";
    static final String QUERY_SELECT_BY_DB_AND_NAMES = "entireRecordsByDbAndNames";
    static final String QUERY_COUNT_BY_DB = "entireRecordsCountByDb";
    //static final String QUERY_SELECT_BY_DB_AND_NAME_AND_ISSUE = "entireRecordsByDbAndNameAndIssue";

    //static final String QUERY_SELECT_BY_DB_AND_SORTTITLE = "entireRecordsByDbAndSortTitle";
    static final String QUERY_SELECT_NAMES_BY_DB_AND_LAST_ISSUE = "entireRecordNamesByDbAndLastIssue";


    static final String Q_DISTINCT_RECORD_NAMES_BY_DB_ID_AND_PRODUCT_SUBTITLE_ID =
            "entireDistinctRecordNamesByDbIdProductSubtitleId";
    static final String Q_UNIT_TITLE_BY_REC_NAME_AND_DB = "entireUnitTitleByRecNameAndDb";
    static final String Q_UNIT_STATUS_BY_REC_NAME_AND_DB = "entireUnitStatusByRecNameAndDb";
    static final String Q_LAST_ISSUE_PUBLISHED_BY_DB_ID = "entireLastIssuePublishedByDbId";
    static final String Q_ID_BELONGS_TO_ISSUE = "entireIdBelongsToIssue";

    private static final String LAST_ISSUE_PUBLISHED = "lastIssuePublished";
    //private static final int TITLE_LENGTH = 512;

    private Integer id;
    private DatabaseEntity database;
    private String name;
    private int lastIssuePublished;
    //private String unitTitle;
    private TitleEntity titleEntity;
    private UnitStatusEntity unitStatus;
    private ProductSubtitleEntity productSubtitle;
    private EntireRecordPublishEntity recordPublishEntity;

    /*public static Query queryRecordsBySortTitle(int dbId, String sortTitle, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_SORTTITLE).setParameter(
            RecordEntity.PARAM_DB_ID, dbId).setParameter(RecordEntity.PARAM_TITLE, sortTitle);
    }*/

    public static Query queryRecordsByName(int dbId, String name, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME).setParameter(
                RecordEntity.PARAM_DB_ID, dbId).setParameter(RecordEntity.PARAM_NAME, name);
    }

    public static Query queryRecordsByNames(int dbId, Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAMES).setParameter(
               RecordEntity.PARAM_DB_ID, dbId).setParameter(RecordEntity.PARAM_NAME, names);
    }

    public static Query queryRecordsCount(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_BY_DB).setParameter("db", dbId);
    }

    //public static Query queryRecordsByName(int dbId, int issueNumber, String name, EntityManager manager) {
    //    return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME_AND_ISSUE).setParameter(
    //        RecordEntity.PARAM_DB_ID, dbId).setParameter(RecordEntity.PARAM_NAME, name).setParameter(
    //            RecordMetadataEntity.PARAM_ISSUE, issueNumber);
    //}

    public static Query queryRecordNames(int dbId, int lastIssue, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_NAMES_BY_DB_AND_LAST_ISSUE).setParameter(
                RecordEntity.PARAM_DB_ID, dbId).setParameter(RecordMetadataEntity.PARAM_ISSUE, lastIssue);
    }

    public static Query qDistinctRecordNamesByDbIdProductSubtitleId(int dbId,
                                                                    int productSubtitleId,
                                                                    EntityManager manager) {
        return manager.createNamedQuery(Q_DISTINCT_RECORD_NAMES_BY_DB_ID_AND_PRODUCT_SUBTITLE_ID)
                .setParameter(Constants.ID_PRM, dbId)
                .setParameter("sbId", productSubtitleId);
    }

    public static Query qUnitTitleByRecNameAndDb(String recName, DatabaseEntity dbEntity, EntityManager manager) {
        return manager.createNamedQuery(Q_UNIT_TITLE_BY_REC_NAME_AND_DB)
                .setParameter(Constants.NAME_PRM, recName)
                .setParameter("db", dbEntity);
    }

    public static Query qUnitStatusByRecNameAndDb(String recName, DatabaseEntity dbEntity, EntityManager manager) {
        return manager.createNamedQuery(Q_UNIT_STATUS_BY_REC_NAME_AND_DB)
                .setParameter(Constants.NAME_PRM, recName)
                .setParameter("db", dbEntity);
    }

    public static Query qLastIssuePublishedByDbId(int dbId, EntityManager manager) {
        return manager.createNamedQuery(Q_LAST_ISSUE_PUBLISHED_BY_DB_ID).setParameter(Constants.ID_PRM, dbId);
    }

    public static Query qIdBelongsToIssue(int lastIssuePublished, List<Integer> recIds, EntityManager manager) {
        return manager.createNamedQuery(Q_ID_BELONGS_TO_ISSUE).setParameter(LAST_ISSUE_PUBLISHED, lastIssuePublished)
                .setParameter(Constants.ID_PRM, recIds);
    }

    public static Query qCountLatestRecordsByIssueAndDb(int issuePublished, DatabaseEntity db, EntityManager manager) {
        return manager.createNamedQuery("recordsCountByIssue").setParameter(
                LAST_ISSUE_PUBLISHED, issuePublished).setParameter("db", db);
    }

    public static Query qRecordsByIssueAndDb(int lastIssuePublished, DatabaseEntity dbEntity, EntityManager manager) {
        return manager.createNamedQuery("findRecordNamesByIssueAndDb").setParameter(
                LAST_ISSUE_PUBLISHED, lastIssuePublished).setParameter("db", dbEntity);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }


    public void setId(Integer id) {
        this.id = id;
    }

    @Transient
    public String getDbName() {
        if (database != null) {
            return database.getName();
        }

        return null;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLastIssuePublished() {
        return lastIssuePublished;
    }

    public void setLastIssuePublished(int lastIssuePublished) {
        this.lastIssuePublished = lastIssuePublished;
    }

    //@Column(length = TITLE_LENGTH)
    @Transient
    public String getUnitTitle() {
        return titleEntity ==  null ? null : titleEntity.getTitle();
    }

    //public void setUnitTitle(String unitTitle) {
    //    this.unitTitle = XmlUtils.normalize(unitTitle);
    //}

    @Transient
    public String getTitle() {
        return getUnitTitle();
    }

    //@ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne
    @JoinColumn(name = "title_id")
    public TitleEntity getTitleEntity() {
        return titleEntity;
    }

    public void setTitleEntity(TitleEntity title) {
        titleEntity = title;
    }

    @ManyToOne
    public UnitStatusEntity getUnitStatus() {
        return unitStatus;
    }

    public void setUnitStatus(UnitStatusEntity unitStatus) {
        this.unitStatus = unitStatus;
    }

    @ManyToOne
    public ProductSubtitleEntity getProductSubtitle() {
        return productSubtitle;
    }

    public void setProductSubtitle(ProductSubtitleEntity productSubtitle) {
        this.productSubtitle = productSubtitle;
    }

    @ManyToOne
    @JoinColumn(name = "database_id")
    public DatabaseEntity getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseEntity database) {
        this.database = database;
    }

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "record")
    public EntireRecordPublishEntity getRecordPublishEntity() {
        return recordPublishEntity;
    }

    public void setRecordPublishEntity(EntireRecordPublishEntity recordPublishEntity) {
        this.recordPublishEntity = recordPublishEntity;
    }

    @Override
    public String toString() {
        return String.format("%s [%d] %d", getName(), getId(), getLastIssuePublished());
    }
}
