package com.wiley.cms.cochrane.cmanager.data.cca;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.tes.util.XmlUtils;


/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 09.12.11
 */
@Entity
@Table(name = "COCHRANE_CCA")
@NamedQueries({
        @NamedQuery(
                name = CcaEntity.QUERY_SELECT_LAST_BY_NAME,
                query = "SELECT e FROM CcaEntity e WHERE e.name = :nm AND e.published = true ORDER BY e.date DESC"
        ),
        @NamedQuery(
                name = CcaEntity.QUERY_SELECT_FIRST_BY_NAME,
                query = "SELECT e FROM CcaEntity e WHERE e.name = :nm AND e.published = true ORDER BY e.date"
        ),
        @NamedQuery(
                name = CcaEntity.QUERY_UPDATE_PUBLISHING_STATE_BY_IDS,
                query = "UPDATE CcaEntity r SET r.published=:st WHERE r.id IN (:id)"
        )
    })
public class CcaEntity implements Serializable {
    static final String QUERY_SELECT_LAST_BY_NAME = "lastCCAByName";
    static final String QUERY_SELECT_FIRST_BY_NAME = "firstCCAByName";
    static final String QUERY_UPDATE_PUBLISHING_STATE_BY_IDS = "updateCCAPublishStateByIds";

    private Integer id;
    private String name;
    private Date date;
    private List<CcaCdsrDoiEntity> ccaCdsrDoiEntityList;
    private boolean published = false;

    public static Query queryLastRecordByName(String name, int max, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_LAST_BY_NAME).setParameter("nm", name).setMaxResults(max);
    }

    public static Query queryFirstRecordByName(String name, int max, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_FIRST_BY_NAME).setParameter("nm", name).setMaxResults(max);
    }

    public static Query querySetPublishingState(Collection<Integer> ids, boolean state, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_PUBLISHING_STATE_BY_IDS).setParameter(
                "st", state).setParameter("id", ids);
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

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = XmlUtils.normalize(name);
    }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "cca")
    public List<CcaCdsrDoiEntity> getCcaCdsrDoiEntityList() {
        return ccaCdsrDoiEntityList;
    }

    public void setCcaCdsrDoiEntityList(List<CcaCdsrDoiEntity> ccaCdsrDoiEntityList) {
        this.ccaCdsrDoiEntityList = ccaCdsrDoiEntityList;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean value) {
        published = value;
    }
}
