package com.wiley.cms.cochrane.cmanager.export.data;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.utils.Constants;

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
import java.util.Date;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_EXPORT")
@NamedQueries({
        @NamedQuery(
                name = ExportEntity.Q_ENTITY_BY_CLDB_ID,
                query = "SELECT e FROM ExportEntity e WHERE e.clDb.id = :id ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_COUNT_BY_CLDB_ID,
                query = "SELECT count(e) FROM ExportEntity e WHERE e.clDb.id = :id ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_ENTITY_BY_CLDB_ID_AND_USER,
                query = "SELECT e FROM ExportEntity e WHERE e.clDb.id = :id AND e.user = :name ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_COUNT_BY_CLDB_ID_AND_USER,
                query = "SELECT count(e) FROM ExportEntity e WHERE e.clDb.id = :id AND e.user = :name"
                        + " ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_ENTITY_BY_DB_NAME_AND_EMPTY_CLDB_ID,
                query = "SELECT e FROM ExportEntity e WHERE e.db.name = :name AND e.clDb IS NULL ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_COUNT_BY_DB_NAME_AND_EMPTY_CLDB_ID,
                query = "SELECT count(e) FROM ExportEntity e WHERE e.db.name = :name AND e.clDb IS NULL"
                        + " ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_ENTITY_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID,
                query = "SELECT e FROM ExportEntity e WHERE e.db.name = :name AND e.user = :name1 AND e.clDb IS NULL"
                        + " ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_COUNT_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID,
                query = "SELECT count(e) FROM ExportEntity e WHERE e.db.name = :name AND e.user = :name1"
                        + " AND e.clDb IS NULL ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = ExportEntity.Q_DEL_BY_CLDB_ID,
                query = "DELETE FROM ExportEntity e WHERE e.clDb.id = :id"
        )
    })
public class ExportEntity implements java.io.Serializable {

    public static final int IN_PROGRESS = 0;
    public static final int FAILED = 1;
    public static final int COMPLETED_WITH_ERRS = 2;
    public static final int COMPLETED = 3;

    static final String Q_ENTITY_BY_CLDB_ID = "exportByClDbId";
    static final String Q_COUNT_BY_CLDB_ID = "countByClDbId";
    static final String Q_ENTITY_BY_CLDB_ID_AND_USER = "exportByClDbIdAndUser";
    static final String Q_COUNT_BY_CLDB_ID_AND_USER = "countByClDbIdAndUser";
    static final String Q_ENTITY_BY_DB_NAME_AND_EMPTY_CLDB_ID = "exportByDbNameAndEmptyClDbId";
    static final String Q_COUNT_BY_DB_NAME_AND_EMPTY_CLDB_ID = "countByDbNameAndEmptyClDbId";
    static final String Q_ENTITY_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID = "exportByDbNameAndUserAndEmptyClDbId";
    static final String Q_COUNT_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID = "countByDbNameAndUserAndEmptyClDbId";
    static final String Q_DEL_BY_CLDB_ID = "deleteExportByClDbId";

    private Integer id;
    private Date date;
    private String filePath;
    private int state;
    private int itemAmount;
    private String user;
    private ClDbEntity clDb;
    private DatabaseEntity db;

    public static Query qEntityByClDbId(int clDbId, int limit, EntityManager manager) {
        Query ret = manager.createNamedQuery(Q_ENTITY_BY_CLDB_ID).setParameter(Constants.ID_PRM, clDbId);
        if (limit > 0) {
            ret.setMaxResults(limit);
        }
        return ret;
    }

    public static Query qCountByClDbId(int clDbId, EntityManager manager) {
        return manager.createNamedQuery(Q_COUNT_BY_CLDB_ID).setParameter(Constants.ID_PRM, clDbId);
    }

    public static Query qEntityByClDbIdAndUser(int clDbId, String user, int limit, EntityManager manager) {
        Query ret = manager.createNamedQuery(Q_ENTITY_BY_CLDB_ID_AND_USER).setParameter(Constants.ID_PRM, clDbId)
                .setParameter(Constants.NAME_PRM, user);
        if (limit > 0) {
            ret.setMaxResults(limit);
        }
        return ret;
    }

    public static Query qCountByClDbIdAndUser(int clDbId, String user, EntityManager manager) {
        return manager.createNamedQuery(Q_COUNT_BY_CLDB_ID_AND_USER).setParameter(
            Constants.ID_PRM, clDbId).setParameter(Constants.NAME_PRM, user);
    }

    public static Query qEntityByDbNameAndEmptyClDbId(String dbName, int limit, EntityManager m) {
        Query ret = m.createNamedQuery(Q_ENTITY_BY_DB_NAME_AND_EMPTY_CLDB_ID)
                .setParameter(Constants.NAME_PRM, dbName);
        if (limit > 0) {
            ret.setMaxResults(limit);
        }
        return ret;
    }

    public static Query qCountByDbNameAndEmptyClDbId(String dbName, EntityManager m) {
        return m.createNamedQuery(Q_COUNT_BY_DB_NAME_AND_EMPTY_CLDB_ID).setParameter(Constants.NAME_PRM, dbName);
    }

    public static Query qEntityByDbNameAndUserAndEmptyClDbId(String dbName, String user, int limit, EntityManager m) {
        Query ret = m.createNamedQuery(Q_ENTITY_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID).setParameter(
                Constants.NAME_PRM, dbName).setParameter(Constants.NAME1_PRM, user);
        if (limit > 0) {
            ret.setMaxResults(limit);
        }
        return ret;
    }

    public static Query qCountByDbNameAndUserAndEmptyClDbId(String dbName, String user, EntityManager m) {
        return m.createNamedQuery(Q_COUNT_BY_DB_NAME_AND_USER_AND_EMPTY_CLDB_ID).setParameter(
                Constants.NAME_PRM, dbName).setParameter(Constants.NAME1_PRM, user);
    }

    public static Query qDeleteByClDbId(int clDbId, EntityManager manager) {
        return manager.createNamedQuery(Q_DEL_BY_CLDB_ID).setParameter(Constants.ID_PRM, clDbId);
    }

    void fillIn(ExportVO vo) {
        setDate(vo.getDate());
        setFilePath(vo.getFilePath());
        setState(vo.getState());
        setItemAmount(vo.getItemAmount());
        setUser(vo.getUser());

    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @ManyToOne
    @JoinColumn(name = "cldb_id")
    public ClDbEntity getClDb() {
        return clDb;
    }

    public void setClDb(ClDbEntity clDb) {
        this.clDb = clDb;
    }

    @ManyToOne
    @JoinColumn(name = "database_id")
    public DatabaseEntity getDb() {
        return db;
    }

    public void setDb(DatabaseEntity db) {
        this.db = db;
    }
}
