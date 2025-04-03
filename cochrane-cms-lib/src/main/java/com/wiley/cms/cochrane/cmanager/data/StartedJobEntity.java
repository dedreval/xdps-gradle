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
 *         Date: 25-Apr-2007
 */

@Entity
@Table(name = "COCHRANE_STARTED_JOB")
@NamedQueries({
        @NamedQuery(
                name = "notCompletedJobId",
                query = "select j.jobId from StartedJobEntity j where j.deliveryFile=:df"
        ),
        @NamedQuery(
                name = "deleteStartedJobId",
                query = "delete from StartedJobEntity j where j.jobId=:jobId"
        ),
        @NamedQuery(
                name = "startedJobIdForDfAndPlan",
                query = "select j.jobId from StartedJobEntity j where j.deliveryFile.id=:df and j.planId=:planId"
        ),
        @NamedQuery(
                name = "deleteStartedJobByDb",
                query = "delete from StartedJobEntity where deliveryFile.id IN ("
                        + "select dfe.id from DeliveryFileEntity dfe where dfe.db=:db)"
        ),
        @NamedQuery(
                name = "selectStartedJobByDb",
                query = "select e from StartedJobEntity e where e.deliveryFile.id IN ("
                        + "select dfe.id from DeliveryFileEntity dfe where dfe.db=:db)"
        )})
public class StartedJobEntity {
    private Integer id;

    private Integer jobId;
    private Integer planId;
    private DeliveryFileEntity deliveryFile;

    //private boolean isCompleted;
    private boolean isSuccessful;


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

    public Integer getPlanId() {
        return planId;
    }

    public void setPlanId(Integer planId) {
        this.planId = planId;
    }

//    public boolean isCompleted()
//    {
//        return isCompleted;
//    }
//
//    public void setCompleted(boolean completed)
//    {
//        isCompleted = completed;
//    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
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
