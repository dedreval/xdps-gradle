package com.wiley.cms.cochrane.cmanager.data.meshterm;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 02.11.2009
 */

@Entity
@Table(name = "COCHRANE_MESHTERM_RECORD_DATES")
@NamedQueries({
        @NamedQuery(
                name = "findUpdatedRecords",
                query = "select mrd.recordName from MeshtermRecordDatesEntity mrd "
                        + "where mrd.date >= :date"
        ),
        @NamedQuery(
                name = "findRecords",
                query = "select mrd.recordName from MeshtermRecordDatesEntity mrd "
                        + "where mrd.recordName in (:recordNames)"
        )
    })
public class MeshtermRecordDatesEntity implements Serializable {
    private String recordName;
    private Integer date;

    private List<MeshtermRecordEntity> meshterms;

    @Id
    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    @Column(name = "lastModified")
    public Integer getDate() {
        return date;
    }

    public void setDate(Integer date) {
        this.date = date;
    }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "recordName")
    public List<MeshtermRecordEntity> getMeshterms() {
        return meshterms;
    }

    public void setMeshterms(List<MeshtermRecordEntity> meshterms) {
        this.meshterms = meshterms;
    }
}
