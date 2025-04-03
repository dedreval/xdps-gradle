package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 *         Date: 11.12.2006
 */
public class DeliveryFileWrapper extends AbstractWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(DeliveryFileWrapper.class);

    private static final long serialVersionUID = 1L;

    private static final Integer LAST_DELIVERY_FILES_AMOUNT = 3;

    private DeliveryFileEntity entity;
    private IssueWrapper issue;

    public DeliveryFileWrapper(DeliveryFileEntity entity) {
        this.entity = entity;
        askFields();
    }

    public DeliveryFileWrapper(int fileId) {
        askEntity(fileId);
    }

    public Integer getId() {
        return entity.getId();
    }

    public DeliveryFileEntity getEntity() {
        return entity;
    }

    public String getName() {
        return entity.getName();
    }

    public String getVendor() {
        return entity.getVendor();
    }

    public Date getDate() {
        return entity.getDate();
    }

    public IssueWrapper getIssue() {
        if (issue == null) {
            IssueEntity issueEnt = entity.getIssue();
            if (issueEnt != null) {
                issue = new IssueWrapper(issueEnt);
            }
        }
        return issue;
    }

    public int getDbId() {
        return entity.getDb().getId();
    }

    public String getDbName() {
        return entity.getDb().getTitle();
    }

    public int getType() {
        return entity.getType();
    }

    public String getStatus() {
        String status = "";
        if (entity.getStatus() != null) {
            status = entity.getStatus().getStatus();
        }

        return status;
    }

    public int getInterimStatusId() {
        int statusId = -1;
        if (entity.getInterimStatus() != null) {
            statusId = entity.getInterimStatus().getId();
        }

        return statusId;
    }

    public int getStatusId() {
        int statusId = -1;
        if (entity.getStatus() != null) {
            statusId = entity.getStatus().getId();
        }
        return statusId;
    }

    public List<Record> getRecords() {

        IRecordStorage rs = RecordStorageFactory.getFactory().getInstance();
        List<RecordEntity> records = rs.getRecordsByDFile(entity, false);
        List<Record> ret = new ArrayList<Record>();

        for (RecordEntity record: records) {
            ret.add(new Record(record));
        }

        return ret;
    }

    public Action[] getActions() {
        return canAcknowledge()
            ? new Action[]{new AcknowledgementAction()} : (canPublish()
                ?  new Action[]{new ResumeAction(this), new PublishAction()} : (canResumeRender()
                    ? new Action[]{new ResumeAction(this), new ResumeRenderAction(this)}
                    : new Action[]{new ResumeAction(this)}));
    }

    public void updateRenderingState(boolean htmlCompleted, boolean pdfCompleted) {
        entity.setHtmlCompleted(htmlCompleted);
        entity.setPdfCompleted(pdfCompleted);
        getDfStorage().mergeDB(entity);
    }

    public void performAction(int action, IVisit visit) {
        askEntity(entity.getId());

        switch (action) {
            case Action.RESUME_ACTION:
                new ResumeAction(this).perform(visit);
                break;
            case Action.RESUME_RENDER_ACTION:
                new ResumeRenderAction(this).perform(visit);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private boolean canAcknowledge() {
        int status = entity.getStatus().getId();
        return DeliveryFileEntity.isAriesAcknowledge(entity.getType())
                && (canPublish() || status == IDeliveryFileStatus.STATUS_PACKAGE_LOADED);
    }

    private boolean canPublish() {
        int status = entity.getStatus().getId();
        return status == IDeliveryFileStatus.STATUS_RND_SUCCESS_AND_FULL_FINISH
            || status == IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS
            || status == IDeliveryFileStatus.STATUS_PUBLISHING_FAILED
            || status == IDeliveryFileStatus.STATUS_PUBLISHING_STARTED;
    }

    private boolean canResumeRender() {
        int status = entity.getStatus().getId();
        return status == IDeliveryFileStatus.STATUS_RND_FAILED || status == IDeliveryFileStatus.STATUS_RND_SOME_FAILED;
    }

    private void askEntity(int fileId) {
        entity = getResultStorage().getDeliveryFileEntity(fileId);
        if (entity == null) {
            entity = new DeliveryFileEntity();
            entity.setId(fileId);
        }
    }

    private void askFields() {
        issue = new IssueWrapper(entity.getIssue());
    }

    public static List<DeliveryFileWrapper> getDeliveryFileWrapperList(List<DeliveryFileEntity> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeliveryFileWrapper> wrapperLists = new ArrayList<>(list.size());
        list.forEach(entity -> wrapperLists.add(new DeliveryFileWrapper(entity)));
        return wrapperLists;
    }

    public static List<DeliveryFileWrapper> getLastDeliveryFileWrapperList(int curIssueId) {
        return getDeliveryFileWrapperList(getResultStorage().getLastDeliveryFileList(curIssueId,
                CochraneCMSProperties.getIntProperty("cms.cochrane.show_last_delivery_files_amount",
                        LAST_DELIVERY_FILES_AMOUNT)));
    }

    public static List<DeliveryFileWrapper> getDeliveryFileWrapperList(int curIssueId, int interval) {
        return getDeliveryFileWrapperList(getResultStorage().getDeliveryFileList(curIssueId, interval));
    }

    public static List<DeliveryFileWrapper> getDbDeliveryFileWrapperList(int issueId, int dbId) {

        List<DeliveryFileWrapper> wrappersWithIssueId = getDeliveryFileWrapperList(issueId, 0);
        List<DeliveryFileWrapper> wrappersWithDbId = new ArrayList<DeliveryFileWrapper>();

        for (DeliveryFileWrapper wrapper : wrappersWithIssueId) {
            if (wrapper.entity.getDb().getId() == dbId) {
                wrappersWithDbId.add(wrapper);
            }
        }

        return wrappersWithDbId;
    }

    private static class PublishAction extends AbstractAction {

        public PublishAction() {
            setConfirmable(true);
        }

        public int getId() {
            return Action.PUBLISH_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("df.action.publish.name");
        }

        public void perform(IVisit visit) {
        }
    }

    private static class AcknowledgementAction extends AbstractAction {

        public AcknowledgementAction() {
            setConfirmable(true);
        }

        public int getId() {
            return Action.ACKNOWLEDGEMENT_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("df.action.acknowledgement.name");
        }

        public void perform(IVisit visit) {
        }
    }

    private static class ResumeRenderAction extends ResumeAction {

        public ResumeRenderAction(DeliveryFileWrapper wrapper) {
            super(wrapper);
        }

        public int getId() {
            return Action.RESUME_RENDER_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("df.action.resume.render.name");
        }

        @Override
        public void perform(IVisit visit) {
            try {
                resumeRendering();
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }
}
