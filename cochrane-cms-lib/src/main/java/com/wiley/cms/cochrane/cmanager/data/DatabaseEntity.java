package com.wiley.cms.cochrane.cmanager.data;

import com.wiley.cms.process.entity.DbEntity;

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
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 05.08.2011
 */
@Entity
@Table(name = "COCHRANE_DATABASE")
@Cacheable
@NamedQueries({
        @NamedQuery(
                name = DatabaseEntity.QUERY_SELECT_BY_NAME,
                query = "SELECT db from DatabaseEntity db WHERE db.name=:na"
        )
    })
public class DatabaseEntity implements java.io.Serializable {

    /** There are predefined database identifiers:*/
    public static final int CENTRAL_KEY = 1;
    public static final int CDSR_KEY    = 2;
    public static final int CMR_KEY     = 3;
    public static final int DARE_KEY    = 4;
    public static final int HTA_KEY     = 5;
    public static final int EED_KEY     = 6;
    public static final int METHREV_KEY = 7;
    public static final int ABOUT_KEY   = 8;
    public static final int EDITORIAL_KEY = 9;
    public static final int CCA_KEY     = 10;
    public static final int CDSR_TA_KEY = 11;

    static final String QUERY_SELECT_BY_NAME = "findDatabaseByName";

    private static final long serialVersionUID = 1L;

    private String name;
    private Integer id;

    @Column(name = "name", length = DbEntity.STRING_VARCHAR_LENGTH_32, updatable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Query queryDatabase(String name, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_NAME).setParameter("na", name).setMaxResults(1);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
