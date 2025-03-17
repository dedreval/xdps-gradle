package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RenderingWrapper extends AbstractWrapper {
    private static final Logger LOG = Logger.getLogger(RenderingWrapper.class);
    private static final String PLAN = "plan=";

    private RenderingEntity entity;
    private RecordWrapper record;
    private RenderingPlanEntity plan;

    public RenderingWrapper(int renderingId) {
        askEntity(renderingId);
    }

    public RenderingWrapper(RenderingEntity entity) {
        this.entity = entity;
        askFields();
    }

    public String getRenderingPlanName() {
        return plan.getShortName();
    }

    public String getDescription() {
        return plan.getDescription();
    }

    public boolean isCompleted() {
        return entity.isCompleted();
    }

    public boolean isSuccessful() {
        return entity.isSuccessful();
    }

    public boolean isApproved() {
        return entity.isApproved();
    }

    public boolean isRejected() {
        return entity.isRejected();
    }

    public String getStatus() {
        String status;
        if (entity.isApproved()) {
            status = CochraneCMSProperties.getProperty("rendering.status.approved");
        } else if (entity.isRejected()) {
            status = CochraneCMSProperties.getProperty("rendering.status.rejected");
        } else {
            status = CochraneCMSProperties.getProperty("rendering.status.not_approved");
        }
        return status;
    }

    public Action[] getActions() {
        ArrayList<Action> actions = new ArrayList<Action>();

        if (entity.isApproved()) {
            actions.add(new UnApproveAction());
        } else if (entity.isRejected()) {
            actions.add(new UnRejectAction());
        } else if (entity.isSuccessful()) {
            actions.add(new ApproveAction());
            //actions.add(new RejectAction());
        }

        Action[] actionArray = new Action[actions.size()];
        actions.toArray(actionArray);

        return actionArray;
    }

    public void performAction(int action, IVisit visit) {
        switch (action) {
            case Action.APPROVE_ACTION:
                new ApproveAction().perform(visit);
                break;
            case Action.UN_APPROVE_ACTION:
                new UnApproveAction().perform(visit);
                break;
            case Action.REJECT_ACTION:
                new RejectAction().perform(visit);
                break;
            case Action.UN_REJECT_ACTION:
                new UnRejectAction().perform(visit);
                break;
            default:
                throw new IllegalArgumentException();
        }
        askEntity(entity.getId());
    }

    public Integer getId() {
        return entity.getId();
    }

    public RecordWrapper getRecord() {
        return record;
    }

    private void askEntity(int renderingId) {
        entity = getResultStorage().getRendering(renderingId);
        askFields();
    }

    private void askFields() {
        record = new RecordWrapper(entity.getRecord());
        plan = entity.getRenderingPlan();
    }

    public static List<RenderingWrapper> getRenderingWrapperList(int recordId) {
        return getRenderingWrapperList(getResultStorage().getRenderingList(recordId));
    }

    private static List<RenderingWrapper> getRenderingWrapperList(List<RenderingEntity> list) {
        List<RenderingWrapper> wrapperList = new ArrayList<RenderingWrapper>();
        RenderingPlanEntity lastPlan = null;
        for (RenderingEntity entity : list) {
            RenderingPlanEntity plan = entity.getRenderingPlan();
            if (lastPlan == null || !plan.getId().equals(lastPlan.getId())) {
                lastPlan = plan;
                wrapperList.add(new RenderingWrapper(entity));
            }
        }         
        return wrapperList;
    }

    private class ApproveAction extends AbstractAction {
        public int getId() {
            return Action.APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("rendering.action.approve.name");
        }

        public void perform(IVisit visit) {
            entity.setRejected(false);
            entity.setApproved(true);

            try {
                getResultStorage().mergeRendering(entity);
            } catch (Exception e) {
                reloadResultStorage();
                getResultStorage().mergeRendering(entity);
            }

            List<RenderingEntity> rndList = getResultStorage().getRenderingList(record.getId());
            boolean setRecApproved = true;
            for (RenderingEntity rndEnt : rndList) {
                if (!rndEnt.isApproved()) {
                    setRecApproved = false;
                }
            }
            if (setRecApproved) {
                record.performAction(Action.APPROVE_ACTION, visit);
            }

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.APPROVE_RND,
                      RenderingWrapper.this.getRecord().getId(), RenderingWrapper.this.getRecord().getName(),
                    PLAN + getDescription());
        }
    }

    private class UnApproveAction extends AbstractAction {
        public int getId() {
            return Action.UN_APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("rendering.action.unapprove.name");
        }

        public void perform(IVisit visit) {
            entity.setRejected(false);
            entity.setApproved(false);
            getRecord().performAction(Action.UN_APPROVE_ACTION, visit);
            DbWrapper dbw = getRecord().getDb();
            if (!dbw.isExistsMinApprovedRecordsCount()) {
                dbw.performAction(Action.UN_APPROVE_ACTION, visit);
            }
            getResultStorage().mergeRendering(entity);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.UNAPPROVE_RND,
                    RenderingWrapper.this.getRecord().getId(), RenderingWrapper.this.getRecord().getName(),
                    PLAN + getDescription());
        }
    }

    private class RejectAction extends AbstractAction {
        public int getId() {
            return Action.REJECT_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("rendering.action.reject.name");
        }

        public void perform(IVisit visit) {
            entity.setApproved(false);
            entity.setRejected(true);
            getRecord().performAction(Action.UN_APPROVE_ACTION, visit);
            DbWrapper dbw = getRecord().getDb();
            dbw.performAction(Action.UN_APPROVE_ACTION, visit);
            getResultStorage().mergeRendering(entity);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.REJECT_RND,
                    RenderingWrapper.this.getRecord().getId(), RenderingWrapper.this.getRecord().getName(),
                    PLAN + getDescription());
        }
    }

    private class UnRejectAction extends AbstractAction {
        public int getId() {
            return Action.UN_REJECT_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("rendering.action.unreject.name");
        }

        public void perform(IVisit visit) {
            entity.setApproved(false);
            entity.setRejected(false);
            getResultStorage().mergeRendering(entity);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.UNREJECT_RND,
                    RenderingWrapper.this.getRecord().getId(), RenderingWrapper.this.getRecord().getName(),
                    PLAN + getDescription());
        }
    }


}