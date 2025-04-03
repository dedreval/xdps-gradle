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
 *         Date: 25-Jul-2007
 */

@Entity
@Table(name = "COCHRANE_RND_NEXT_JOB")
@NamedQueries({
        @NamedQuery(
                name = "rndNextJob",
                query = "SELECT q from RndNextJobEntity q "
        ),
        @NamedQuery(
                name = "deleteRndNextJob",
                query = "DELETE from RndNextJobEntity where id=:id"
        ),
        @NamedQuery(
                name = "deleteRndNextJobByDb",
                query = "DELETE from RndNextJobEntity ent where "
                        + "ent.jobId in (SELECT sj.jobId from StartedJobEntity sj where "
                        + "sj.deliveryFile.id in (SELECT df.id from DeliveryFileEntity df where df.db=:db))"
        )
    })
public class RndNextJobEntity {
    private static final int STRING_MEDIUM_TEXT_LENGTH = 2097152;

    private Integer id;
    private int jobId;
    private String result;
    //private DeliveryFileEntity deliveryFile;

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

