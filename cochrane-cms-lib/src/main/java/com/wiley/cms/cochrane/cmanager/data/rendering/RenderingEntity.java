package com.wiley.cms.cochrane.cmanager.data.rendering;

import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 04.12.2006
 */
@Entity
@Table(name = "COCHRANE_RENDERING")
@NamedQueries({
        //@NamedQuery(
        //    name = RenderingEntity.QUERY_INSERT_BY_IDS,
        //    query = "INSERT INTO RenderingEntity r (r.record.id, ) where record.id in (:ids)"
        //),
        @NamedQuery(
                name = "findRendering",
                query = "SELECT r from RenderingEntity r where r.record=:record"
        ),
        @NamedQuery(
                name = "findRenderingByRecordAndPlan",
                query = "SELECT r from RenderingEntity r where r.record=:record and r.renderingPlan=:plan"
        ),
        @NamedQuery(
                name = "findRenderingByOrder",
                query = "SELECT r FROM RenderingEntity r WHERE r.record.id=:record"
                        + " ORDER BY r.renderingPlan.priority, r.renderingPlan.id, r.id DESC"
        ),
        @NamedQuery(
                name = "findAllRendering",
                query = "SELECT count(r.id) from RenderingEntity r "
                        + "where r.record.id in(select cr.id from RecordEntity cr where cr.deliveryFile=:df "
                        + " and cr.renderingSuccessful=false)"
        ),
        @NamedQuery(
                name = "deleteRenderingbyDf",
                query = "delete from RenderingEntity r "
                        + "where r.record.id in(select cr.id from RecordEntity cr where cr.deliveryFile=:df "
                        + " and cr.renderingSuccessful=false)"
        ),
        @NamedQuery(
                name = "rndCountByDFileAndRndCompleted",
                query = "select count(rnd.id) from RenderingEntity rnd "
                        + " where rnd.record.id in(select r.id from RecordEntity r where r.deliveryFile=:df)"
                        + " and rnd.completed=true and rnd.renderingPlan=:plan"
        ),
        @NamedQuery(
                name = "deleteRenderingByDb",
                query = "delete from RenderingEntity where record.id in "
                        + "(select r.id from RecordEntity r where r.db=:db)"
        ),
        @NamedQuery(
                name = RenderingEntity.QUERY_DELETE_BY_RECORDS,
                query = "delete from RenderingEntity where record.id in (:ids)"
        ),
        @NamedQuery(
                name = RenderingEntity.QUERY_DELETE_BY_RECORDS_AND_PLAN,
                query = "DELETE from RenderingEntity WHERE record.id IN (:ids) AND renderingPlan.id =:pl"
        ),
        @NamedQuery(
                name = RenderingEntity.QUERY_SELECT_COMPLETED_BY_RECORDS,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.record.RenderingVO(r) FROM RenderingEntity r "
                    + "WHERE r.record.id in (:ids) AND r.completed=true"
        ),
        @NamedQuery(
                name = RenderingEntity.QUERY_SELECT_BY_RECORDS,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.record.RenderingVO(r) FROM RenderingEntity r "
                    + "WHERE r.record.id in (:ids) ORDER BY r.record.id"
        ),
        @NamedQuery(
                name = "selectRenderingByDb",
                query = "select r from RenderingEntity r where r.record.id in "
                        + "(select r.id from RecordEntity r where r.db=:db)"
        ),
        @NamedQuery(
                name = "unapprovedRenderingByDb",
                query = "update RenderingEntity re set re.approved=false where re.record.id in "
                        + "(select r.id from RecordEntity r where r.db.id=:dbId)"
        ),
        @NamedQuery(
                name = RenderingEntity.QUERY_UPDATE_SUCCESS_BY_RECORDS,
                query = "UPDATE RenderingEntity re SET re.successful=:success, re.completed=true "
                    + "WHERE re.renderingPlan.id=:plan AND re.record.id IN(:ids)"

        )
    })
public class RenderingEntity implements java.io.Serializable {
    static final String QUERY_DELETE_BY_RECORDS = "deleteRenderingByRecords";
    static final String QUERY_DELETE_BY_RECORDS_AND_PLAN = "deleteRenderingByRecordsAndPlan";
    static final String QUERY_UPDATE_SUCCESS_BY_RECORDS = "updateRenderingSuccessByRecords";
    static final String QUERY_SELECT_COMPLETED_BY_RECORDS = "renderingCompletedByRecords";
    static final String QUERY_SELECT_BY_RECORDS = "renderingByRecords";

    private static final String PARAM_PLAN = "plan";

    private Integer id;
    private RecordEntity record;
    private RenderingPlanEntity renderingPlan;
    private boolean isCompleted;
    private boolean isSuccessful;
    private boolean isApproved;
    private boolean isRejected;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public static Query queryDeleteRenderings(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_RECORDS).setParameter(DbEntity.PARAM_IDS, ids);
    }

    public static Query queryDeleteRenderings(Collection<Integer> ids, Integer planId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_RECORDS_AND_PLAN).setParameter(
                DbEntity.PARAM_IDS, ids).setParameter("pl", planId);
    }

    public static Query queryUpdateRenderings(int planId, Collection<Integer> ids, boolean success,
        EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_SUCCESS_BY_RECORDS).setParameter(
                DbEntity.PARAM_IDS, ids).setParameter(PARAM_PLAN, planId).setParameter("success", success);
    }

    public static Query queryCompletedRenderings(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COMPLETED_BY_RECORDS).setParameter(DbEntity.PARAM_IDS, ids);
    }

    public static Query queryRenderings(Collection<Integer> recordIds, EntityManager manager){
        return manager.createNamedQuery(QUERY_SELECT_BY_RECORDS).setParameter(DbEntity.PARAM_IDS, recordIds);
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "record_id")
    public RecordEntity getRecord() {
        return record;
    }

    public void setRecord(RecordEntity record) {
        this.record = record;
    }

    @ManyToOne
    @JoinColumn(name = "plan_id")
    public RenderingPlanEntity getRenderingPlan() {
        return renderingPlan;
    }

    public void setRenderingPlan(RenderingPlanEntity renderingPlan) {
        this.renderingPlan = renderingPlan;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }

    public boolean isApproved() {
        return isApproved;
    }

    public void setApproved(boolean approved) {
        isApproved = approved;
    }

    public boolean isRejected() {
        return isRejected;
    }

    public void setRejected(boolean rejected) {
        isRejected = rejected;
    }
}
