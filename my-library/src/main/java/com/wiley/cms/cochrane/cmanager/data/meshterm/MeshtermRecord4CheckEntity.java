package com.wiley.cms.cochrane.cmanager.data.meshterm;

import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.utils.Constants;

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

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 26.02.2013
 */
@Entity
@Table(name = "COCHRANE_MESHTERM_RECORD_4_CHECK")
@NamedQueries({
        @NamedQuery(
                name = "meshtermRecord4CheckCountByDbId",
                query = "SELECT COUNT(DISTINCT r.name) FROM MeshtermRecord4CheckEntity r WHERE r.database.id = :dbId"
        ),
        @NamedQuery(
                name = "meshtermRecord4CheckNotCheckedDbId",
                query = "SELECT r FROM MeshtermRecord4CheckEntity r WHERE r.checked = false AND r.database.id = :dbId"
        ),
        @NamedQuery(
                name = "meshtermRecord4CheckOutdatedByDbId",
                query = "SELECT r FROM MeshtermRecordDescriptorEntity rd JOIN rd.record r"
                        + " WHERE r.database.id = :dbId"
                        + " AND rd.descriptor IN (SELECT d.descriptor FROM MeshtermChangedDescriptorEntity d)"
        ),
        @NamedQuery(
                name = "updateMeshtermRecord4CheckStatusByIds",
                query = "UPDATE MeshtermRecord4CheckEntity r SET r.checked = :status WHERE r.id IN (:ids)"
        ),
        @NamedQuery(
                name = MeshtermRecord4CheckEntity.Q_DEL,
                query = "DELETE FROM MeshtermRecord4CheckEntity r"
        ),
        @NamedQuery(
                name = MeshtermRecord4CheckEntity.Q_DEL_BY_DB_ID,
                query = "DELETE FROM MeshtermRecord4CheckEntity r WHERE r.database.id = :id"
        )
    })
public class MeshtermRecord4CheckEntity {

    static final String Q_DEL = "delMeshtermRecord4Check";
    static final String Q_DEL_BY_DB_ID = "delMeshtermRecord4CheckByDbId";

    private Integer id;
    private boolean checked;
    private Integer recordId;
    private String name;
    private Integer version;
    private boolean latestVersion;
    private String doi;
    private DatabaseEntity database;

    public MeshtermRecord4CheckEntity() {
    }

    public MeshtermRecord4CheckEntity(String name, Integer recordId, String doi, Integer version, boolean last) {
        setName(name);
        setRecordId(recordId);
        setLatestVersion(last);
        setDoi(doi);
        setVersion(version);
    }

    public static Query qDel(EntityManager em) {
        return em.createNamedQuery(Q_DEL);
    }

    public static Query qDel(int dbId, EntityManager em) {
        return em.createNamedQuery(Q_DEL_BY_DB_ID).setParameter(Constants.ID_PRM, dbId);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(nullable = false)
    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Column(name = "record_id", nullable = false)
    public int getRecordId() {
        return recordId;
    }

    public void setRecordId(int recordId) {
        this.recordId = recordId;
    }

    @Column(nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Column(nullable = false)
    public boolean isLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(boolean latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    @ManyToOne
    @JoinColumn(name = "database_id", nullable = false)
    public DatabaseEntity getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseEntity database) {
        this.database = database;
    }

}
