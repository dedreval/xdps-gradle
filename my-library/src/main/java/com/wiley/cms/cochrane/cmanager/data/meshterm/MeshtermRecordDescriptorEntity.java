package com.wiley.cms.cochrane.cmanager.data.meshterm;

import com.wiley.cms.cochrane.utils.Constants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Query;
import javax.persistence.EntityManager;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 22.02.2013
 */
@Entity
@Table(name = "COCHRANE_MESHTERM_RECORD_DESCRIPTORS")
@NamedQueries({
        @NamedQuery(
                name = "meshtermRecordDescriptorCountByRecordId",
                query = "SELECT COUNT(rd.id) FROM MeshtermRecordDescriptorEntity rd WHERE rd.record.id IN (:ids)"
        ),
        @NamedQuery(
                name = MeshtermRecordDescriptorEntity.Q_DEL,
                query = "DELETE FROM MeshtermRecordDescriptorEntity rd"
        ),
        @NamedQuery(
                name = MeshtermRecordDescriptorEntity.Q_DEL_BY_ID,
                query = "DELETE FROM MeshtermRecordDescriptorEntity rd WHERE rd.record.id"
                        + " IN (SELECT r.id FROM MeshtermRecord4CheckEntity r WHERE r.database.id = :id)"
        )
    })
public class MeshtermRecordDescriptorEntity {

    static final String Q_DEL = "deleteMeshtermRecordDescriptor";
    static final String Q_DEL_BY_ID = "deleteMeshtermRecordDescriptorByDbId";

    private int id;
    private String descriptor;
    private MeshtermRecord4CheckEntity record;

    public static Query qDel(EntityManager em) {
        return em.createNamedQuery(Q_DEL);
    }

    public static Query qDel(int dbId, EntityManager em) {
        return em.createNamedQuery(Q_DEL_BY_ID).setParameter(Constants.ID_PRM, dbId);
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
    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    @ManyToOne
    @JoinColumn(name = "record_id", nullable = false)
    public MeshtermRecord4CheckEntity getRecord() {
        return record;
    }

    public void setRecord(MeshtermRecord4CheckEntity record) {
        this.record = record;
    }

}
