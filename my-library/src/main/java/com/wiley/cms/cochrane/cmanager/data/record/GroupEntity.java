package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

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
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 06.07.12
 */
@Entity
@Table(name = "COCHRANE_GROUP")
@Cacheable
@NamedQueries({
        @NamedQuery(
                name = GroupEntity.QUERY_SELECT_BY_SID,
                query = "SELECT g FROM GroupEntity g WHERE g.sid= :sid"
        ),
        @NamedQuery(
                name = GroupEntity.QUERY_SELECT_ALL,
                query = "SELECT g FROM GroupEntity g ORDER BY g.title"
        )
    })
public class GroupEntity implements Serializable {

    static final String QUERY_SELECT_BY_SID = "groupBySid";
    static final String QUERY_SELECT_ALL = "groupsAll";
    private static final int SID_LENGTH   = 24;
    private static final int CODE_LENGTH  = 24;
    private static final int TITLE_LENGTH = 128;

    private int id;
    private String sid;
    private String code;
    private String title;

    public GroupEntity() {
    }

    public GroupEntity(String sid, String code, String title) {
        this.sid = sid;
        this.code = code;
        this.title = title;
    }

    public static Query queryGroup(String sid, EntityManager manager) {
        return manager.createNamedQuery(GroupEntity.QUERY_SELECT_BY_SID).setParameter("sid", sid);
    }

    public static Query queryGroups(EntityManager manager) {
        return manager.createNamedQuery(GroupEntity.QUERY_SELECT_ALL);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    @Column(length = CODE_LENGTH, nullable = false)
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Column(length = SID_LENGTH, nullable = false)
    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    @Column(length = TITLE_LENGTH, nullable = false)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return String.format("%s %s [%d]", sid, code, id);
    }
}
