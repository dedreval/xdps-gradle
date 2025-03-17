package com.wiley.cms.cochrane.cmanager.data.cca;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 27.04.2012
 */
@Entity
@Table(name = "CCA_CDSR_DOI_VIEW")
@NamedQueries({
        @NamedQuery(
                name = "selectCcaByCdsrNane",
                query = "SELECT Cca FROM CcaCdsrDoiViewEntity Cca WHERE Cca.cdsrName = :cdsrName"
        )
    })
public class CcaCdsrDoiViewEntity implements Serializable {

    @Id
    @Column(name = "name", insertable = false, updatable = false)
    private String ccaName;
    @Column(insertable = false, updatable = false)
    private String cdsrName;
    @Column(insertable = false, updatable = false)
    private String doi;
    @Column(insertable = false, updatable = false)
    private Date date;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getCcaName() {
        return ccaName;
    }

    public void setCcaName(String ccaName) {
        this.ccaName = ccaName;
    }

    public String getCdsrName() {
        return cdsrName;
    }

    public void setCdsrName(String cdsrName) {
        this.cdsrName = cdsrName;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

}
