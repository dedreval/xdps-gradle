package com.wiley.cms.cochrane.cmanager.data.cca;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 09.12.11
 */
@Entity
@Table(name = "COCHRANE_CCA_CDSR_DOI")
public class CcaCdsrDoiEntity implements Serializable {
    private Integer id;
    private String doi;
    private String cdsrName;

    private CcaEntity cca;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(name = "doi", nullable = false)
    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    @Column(name = "cdsrName", nullable = false)
    public String getCdsrName() {
        return cdsrName;
    }

    public void setCdsrName(String cdsrName) {
        this.cdsrName = cdsrName;
    }

    @ManyToOne
    @JoinColumn(name = "cca_id")
    public CcaEntity getCca() {
        return cca;
    }

    public void setCca(CcaEntity cca) {
        this.cca = cca;
    }
}
