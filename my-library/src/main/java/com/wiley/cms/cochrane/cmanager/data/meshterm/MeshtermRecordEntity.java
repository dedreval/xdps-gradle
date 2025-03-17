package com.wiley.cms.cochrane.cmanager.data.meshterm;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 30.10.2009
 */

@Entity
@Table(name = "COCHRANE_MESHTERM_RECORDS",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recordName", "descriptorId", "qualifierId"}))
@NamedQueries({
        @NamedQuery(
                name = "selectMeshterms",
                query = "SELECT id FROM MeshtermRecordEntity WHERE recordName=:recordName"
        ),
        @NamedQuery(
                name = "findMeshtermRecordId",
                query = "SELECT id FROM MeshtermRecordEntity WHERE recordName=:recordName "
                        + "AND descriptorId=:descriptorId AND qualifierId=:qualifierId"
        ),
        @NamedQuery(
                name = "findDistinctDescriptors",
                query = "select distinct mre.descriptorEntity from MeshtermRecordEntity mre "
                        + "where mre.recordName = :recordName order by mre.descriptorEntity.descriptor"
        ),
        @NamedQuery(
                name = "findQualifiersByRecordNameAndDescriptor",
                query = "select mre.qualifierEntity from MeshtermRecordEntity mre where mre.recordName = :recordName "
                        + "and mre.descriptorEntity = :descriptorEntity order by mre.qualifierEntity.qualifier"
        )
    })
public class MeshtermRecordEntity implements Serializable {
    private Integer id;
    private String recordName;
    private DescriptorEntity descriptorEntity;
    private QualifierEntity qualifierEntity;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    @ManyToOne
    @JoinColumn(name = "descriptorId")
    public DescriptorEntity getDescriptorEntity() {
        return descriptorEntity;
    }

    public void setDescriptorEntity(DescriptorEntity descriptorEntity) {
        this.descriptorEntity = descriptorEntity;
    }

    @ManyToOne
    @JoinColumn(name = "qualifierId")
    public QualifierEntity getQualifierEntity() {
        return qualifierEntity;
    }

    public void setQualifierEntity(QualifierEntity qualifierEntity) {
        this.qualifierEntity = qualifierEntity;
    }
}
