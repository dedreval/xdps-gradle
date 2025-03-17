package com.wiley.cms.cochrane.cmanager.data;

import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 09-Jan-2007
 */

@Entity
@Table(name = "COCHRANE_JOB_QAS_RESULT")
@NamedQueries({
        @NamedQuery(
                name = "getQasResultByRecord",
                query = "select qr.result from JobQasResultEntity qr where qr.record.id=:id"
        ),
        @NamedQuery(
                name = "getQasResultEntityByRecord",
                query = "select qr from JobQasResultEntity qr where qr.record.id=:id"
        ),
        @NamedQuery(
                name = "qasResultDeleteByDf",
                query = "delete from JobQasResultEntity  where record.id in"
                        + "(select e.id from RecordEntity e where e.deliveryFile.id=:dfId)"
        ),
        @NamedQuery(
                name = "selectQasResultByDb",
                query = "select q from JobQasResultEntity  q where q.record.id in"
                        + "(select e.id from RecordEntity e where e.db=:db)"
        ),
        @NamedQuery(
                name = JobQasResultEntity.QUERY_SELECT_BY_RECORD_IDS_DESC_ORDERED_BY_ID,
                query = "select q from JobQasResultEntity q where q.record.id in (:ids) order by id desc"
        ),
        @NamedQuery(
                name = "deleteQasResultByDb",
                query = "delete from JobQasResultEntity  where record.id in"
                        + " (select r.id from RecordEntity r where r.db=:db)"
        )})
public class JobQasResultEntity {
    static final String QUERY_SELECT_BY_RECORD_IDS_DESC_ORDERED_BY_ID = "qasByRecordIdsDescOrderedById";

    private static final int STRING_MEDIUM_TEXT_LENGTH = 65536;

    private Integer id;
    private String result;

    private RecordEntity record;

    public static Query queryLatestQasResultByRecord(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_RECORD_IDS_DESC_ORDERED_BY_ID)
                .setParameter(DbEntity.PARAM_IDS, ids);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    @Column(length = STRING_MEDIUM_TEXT_LENGTH)
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @OneToOne()
    public RecordEntity getRecord() {
        return record;
    }

    public void setRecord(RecordEntity record) {
        this.record = record;
    }
}
