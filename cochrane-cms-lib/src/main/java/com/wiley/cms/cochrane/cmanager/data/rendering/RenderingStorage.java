package com.wiley.cms.cochrane.cmanager.data.rendering;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.ejb.EJBException;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.StartedJobEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RenderingVO;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IRenderingStorage.class)
public class RenderingStorage implements IRenderingStorage {
    private static final String PLAN_CD = "html_cd";
    private static final String PLAN_HTML = "html_diamond";
    private static final String PLAN_PDF = "pdf_tex";

    private static final String RECORD_PARAM = "record";
    private static final Logger LOG = Logger.getLogger(RenderingStorage.class);
    private static final MessageFormat QUERY_TEMPLATE;
    private static final String PLAN = "plan";

    @PersistenceContext
    private EntityManager manager;

    protected enum Operation {
        COUNT,
        DELETE
    }

    static {

        StringBuilder sb = new StringBuilder()
            .append(" {0}")
            .append(" from RenderingEntity r ").append(" where r.record.id in")
            .append(" (select cr.id from RecordEntity cr where cr.deliveryFile=:df")
            .append(" {1})")
            .append(" {2}");

        QUERY_TEMPLATE = new MessageFormat(sb.toString());
    }


    public RenderingEntity getByRecordAndPlanDescription(int recordId, String plan) {
        RecordEntity record = manager.find(RecordEntity.class, recordId);
        RenderingPlanEntity planEntity = (RenderingPlanEntity) manager.createNamedQuery("rndPlan")
            .setParameter("d", plan).getSingleResult();
        return (RenderingEntity) manager.createNamedQuery("findRenderingByRecordAndPlan")
            .setParameter(RECORD_PARAM, record)
            .setParameter(PLAN, planEntity)
            .getSingleResult();
    }


    public void unapproved(int dbId) {
        manager.createNamedQuery("unapprovedRenderingByDb")
            .setParameter("dbId", dbId)
            .executeUpdate();
    }

    public List<RenderingEntity> findRenderingsByRecord(RecordEntity record) {
        return manager.createNamedQuery("findRendering").setParameter(RECORD_PARAM, record).getResultList();
    }


    public Long getRecordsCountByDFileAndPlan(String recordNames, String plans, DeliveryFileEntity dfEntity) {
        Object[] args = buildQueryArgs(Operation.COUNT, recordNames, false, plans);
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));

        return (Long) query.setParameter("df", dfEntity).getSingleResult();
    }

    public void deleteRecordsByDFileAndPlan(String recordNames, String plans, DeliveryFileEntity dfEntity) {
        Object[] args = buildQueryArgs(Operation.DELETE, recordNames, false, plans);
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));

        query.setParameter("df", dfEntity).executeUpdate();
    }

    public void deleteRecordsByIds(Collection<Integer> ids) {
        RenderingEntity.queryDeleteRenderings(ids, manager).executeUpdate();
    }

    public void deleteRecordsByIds(Collection<Integer> ids, Integer planId) {
        RenderingEntity.queryDeleteRenderings(ids, planId, manager).executeUpdate();
    }

    public Long getRecordsCountByDFileAndPlan(String plans, DeliveryFileEntity dfEntity) {
        Object[] args = buildQueryArgs(Operation.COUNT, "", true, plans);
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));

        return (Long) query.setParameter("df", dfEntity).getSingleResult();
    }

    public void deleteRecordsByDFileAndPlan(String plans, DeliveryFileEntity dfEntity) {
        Object[] args = buildQueryArgs(Operation.DELETE, "", true, plans);
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));

        query.setParameter("df", dfEntity).executeUpdate();
    }

    private Object[] buildQueryArgs(Operation operation, String recordNames,
                                    boolean addRenderingSuccessCriteria, String plans) {

        String criteria = addRenderingSuccessCriteria ? " and renderingSuccessful=false"
            : " and cr.name in(" + recordNames + ")";

        if (Operation.COUNT.equals(operation)) {
            return new Object[]{"SELECT count(r.id)", criteria, plans};

        } else if (Operation.DELETE.equals(operation)) {
            return new Object[]{"delete", criteria, plans};
        }

        throw new IllegalArgumentException();
    }

    public int updateRenderings(int planId, Collection<Integer> ids, boolean success) {
        return RenderingEntity.queryUpdateRenderings(planId, ids, success, manager).executeUpdate();
    }

    public int updateRenderings(int dbId, String condition, int planId, boolean success) {
        LOG.debug("updateRen started");
        int count = 0;
        try {
            count = manager.createQuery("update RenderingEntity rnd set rnd.completed=true, rnd.successful=:success"
                + " where rnd.record.id in(select r.id from RecordEntity r where r.db.id=:db and r.name in("
                + condition + ")) and rnd.renderingPlan.id=:plan")
                .setParameter(PLAN, planId)
                .setParameter("db", dbId)
                .setParameter("success", success)
                .executeUpdate();
            LOG.debug("updated " + count);
        } catch (EJBException e) {
            LOG.debug(e, e);
        }
        return count;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createRenderingRecords(Collection<? extends IRecord> records, int planId) {
        for (IRecord record: records) {
            RenderingEntityPlain re = new RenderingEntityPlain(record.getId(), planId);
            manager.persist(re);
        }
    }

    public void createRenderingRecords(String recIds, int[] planIds) {
        //todo  remove native query

        for (int planId : planIds) {

            StringBuilder query = new StringBuilder();
            query.append("insert into COCHRANE_RENDERING (completed, successful, approved, rejected, ").append(
                "record_id, plan_id) select false, false, false, false, id,").append(planId).append(
                    " from COCHRANE_RECORD where id in (").append(recIds).append(")");

            int count = manager.createNativeQuery(query.toString()).executeUpdate();
            LOG.debug(String.format("a number inserted renderings = " + count));
        }
    }

    public void createRenderingRecords(String recs, RenderingPlanEntity[] planEntitys, DeliveryFileEntity dfEntity) {
        for (RenderingPlanEntity planEntity : planEntitys) {
            String query = "insert  into COCHRANE_RENDERING "
                + "(completed,successful,approved,rejected,record_id,plan_id) "
                + "select false,false,false,false,id,"
                + planEntity.getId() + " from COCHRANE_RECORD where name in ("
                + recs + ") and " + "delivery_file_id=" + dfEntity.getId();

            int count = manager.createNativeQuery(query).executeUpdate();

            LOG.debug("a number inserted renderings " + count);
        }

        manager.flush();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RenderingVO> findRenderingsByRecords(Collection<Integer> recordIds, boolean onlyCompleted) {

        if (recordIds.size() == 0) {
            LOG.warn("renderings collection size is 0");
            return Collections.emptyList();
        }
        return onlyCompleted
                ? (List<RenderingVO>) RenderingEntity.queryCompletedRenderings(recordIds, manager).getResultList()
                : (List<RenderingVO>) RenderingEntity.queryRenderings(recordIds, manager).getResultList();
    }

    @Deprecated
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public RenderingPlanEntity[] getPlanEntities(boolean pdfCreate, boolean htmlCreate, boolean cdCreate, int count) {

        RenderingPlanEntity[] planEntities;
        planEntities = new RenderingPlanEntity[count];
        int aNumber = 0;
        if (pdfCreate) {
            planEntities[aNumber++] = getPlanEntity(RenderingPlan.PDF_TEX.id());
        }

        if (htmlCreate) {
            planEntities[aNumber++] = getPlanEntity(RenderingPlan.HTML.id());
        }

        if (cdCreate) {
            planEntities[aNumber] = getPlanEntity(RenderingPlan.CD.id());
        }
        return planEntities;
    }

    private RenderingPlanEntity getPlanEntity(int id) {
        return manager.find(RenderingPlanEntity.class, id);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createStartedJob(int jobId, RenderingPlan plan, DeliveryFileEntity dfEntity) {
        if (dfEntity == null
            || jobId == -1) {
            //delivery file is null in record saving/modifying process
            return;
        }
        StartedJobEntity st = new StartedJobEntity();
        st.setJobId(jobId);
        st.setDeliveryFile(dfEntity);
        st.setPlanId(getPlanNameToId().get(plan));
        manager.persist(st);
        if (plan.equals(PLAN_CD)) {
            dfEntity.setCdCompleted(false);
        }
        if (plan.equals(PLAN_HTML)) {
            dfEntity.setHtmlCompleted(false);
        }
        if (plan.equals(PLAN_PDF)) {
            dfEntity.setPdfCompleted(false);
        }
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, Integer> getPlanNameToId() {
        HashMap<String, Integer> planNameToId;
        planNameToId = new HashMap<String, Integer>();
        List<RenderingPlanEntity> plans = manager.createNamedQuery("rndPlans").getResultList();
        for (RenderingPlanEntity plan : plans) {
            planNameToId.put(plan.getDescription(), plan.getId());
        }
        return planNameToId;
    }
}