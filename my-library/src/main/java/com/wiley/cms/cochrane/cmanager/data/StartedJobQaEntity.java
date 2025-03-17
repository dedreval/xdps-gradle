package com.wiley.cms.cochrane.cmanager.data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 08-Jun-2007
 */
@Entity
@Table(name = "COCHRANE_STARTED_JOB_QA")
@NamedQueries({
        @NamedQuery(
                name = "deleteStartedJobQaId",
                query = "delete from StartedJobQaEntity j where j.jobId=:jobId"
        ),
        @NamedQuery(
                name = "notCompletedQaJobId",
                query = "select j.jobId from StartedJobQaEntity j where j.deliveryFile=:df"
        ),
        @NamedQuery(
                name = "deleteStartedJobQaByDb",
                query = "delete from StartedJobQaEntity where deliveryFile.id IN ("
                        + "select dfe.id from DeliveryFileEntity dfe where dfe.db=:db)"
        ),
        @NamedQuery(
                name = "selectStartedJobQaByDb",
                query = "select e from StartedJobQaEntity e where e.deliveryFile.id IN ("
                        + "select dfe.id from DeliveryFileEntity dfe where dfe.db=:db)"
        )
    })
public class StartedJobQaEntity {


    private Integer id;

    private Integer jobId;
    private DeliveryFileEntity deliveryFile;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    @ManyToOne
    @JoinColumn(name = "delivery_file_id")
    public DeliveryFileEntity getDeliveryFile() {
        return deliveryFile;
    }

    public void setDeliveryFile(DeliveryFileEntity deliveryFile) {
        this.deliveryFile = deliveryFile;
    }
}

