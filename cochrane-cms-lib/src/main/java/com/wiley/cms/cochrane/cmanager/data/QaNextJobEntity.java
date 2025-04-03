package com.wiley.cms.cochrane.cmanager.data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 24-Jul-2007
 */
@Entity
@Table(name = "COCHRANE_QA_NEXT_JOB")
@NamedQueries({
        @NamedQuery(
                name = "qaNextJob",
                query = "SELECT q from QaNextJobEntity q"
        ),
        @NamedQuery(
                name = "deleteQaNextJob",
                query = "DELETE from QaNextJobEntity where id=:id"
        ),
        @NamedQuery(
                name = "deleteQaNextJobByDb",
                query = "DELETE from QaNextJobEntity ent where "
                        + "ent.jobId in (SELECT sj.jobId from StartedJobQaEntity sj where "
                        + "sj.deliveryFile.id in (SELECT df.id from DeliveryFileEntity df where df.db=:db))"
        ),
        @NamedQuery(
                name = "findQaNextJobById",
                query = "SELECT q from QaNextJobEntity q where q.jobId=:jobId"
        )
    })
public class QaNextJobEntity {
    private static final int STRING_MEDIUM_TEXT_LENGTH = 2097152;

    private Integer id;
    private int jobId;
    private String result;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(length = STRING_MEDIUM_TEXT_LENGTH)
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }
}
