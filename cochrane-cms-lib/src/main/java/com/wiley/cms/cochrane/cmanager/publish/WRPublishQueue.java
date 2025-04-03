package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Collection;
import java.util.List;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.StatusEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>OLga Soletskaya</a>
 * @version 1.0
 */
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public abstract class WRPublishQueue extends IssuePublisher {

    public void onMessage(Message message) {

        int publishId = DbEntity.NOT_EXIST_ID;
        int dfId = DbEntity.NOT_EXIST_ID;
        PublishWrapper publish = null;
        try {
            int dbId = PublishHelper.getDataBaseId(message);
            publish = PublishHelper.getPublishWrapper(message);
            publishId = publish.getId();
            dfId = publish.getDeliveryFileId();

            DbWrapper dbWrapper = new DbWrapper(dbId, dfId);
            db = new ClDbVO(dbWrapper.getEntity());
            proceed(publish);

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);

            if (DbUtils.exists(publishId)) {
                rs.setGenerating(publishId, false, false, 0);
                rs.setUnpacking(publishId, false, false);
                rs.setSending(publishId, false, false, false);
            }

            if (DbUtils.exists(dfId)) {
                rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_FAILED, true);
                if (publish != null) {
                    flowLogger.getActivityLog().error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PUBLISH_FAILED,
                            publishId, publish.getFileName(), publish.getType(), publish.getStatus());
                } 
            }
        }
    }

    @Override
    protected void proceedLast(PublishWrapper publish, ActivityLogEntity.EntityLevel lvl) {

        String type = publish.getType();
        PublishDestination dest = PublishProfile.PUB_PROFILE.get().getDestination();

        if (canFinishWR(publish, type, dest.getMainTypes())) {
            finishWR(publish);

        } else if (!publish.hasNext()) {
            // last element in a chain
            switch (type) {
                case PubType.TYPE_DS_MONTHLY:
                    finishLast(publish, false);
                    break;
                case PubType.TYPE_DS:
                    finishLast(publish, true);
                    
                    break;
                default:
                    if (dest.hasPubType(type)) {
                        // if WR package has no records, but only topics
                        finishWR(publish);
                    }
                    break;
            }
        }
        publish.resetPublishForWaiting(ps);
    }

    private boolean canFinishWR(PublishWrapper publish, String type, Collection<String> mainTypes) {
        return mainTypes.contains(type) && (!publish.hasNext() || !canFinishWR(publish.getNext(),
                publish.getNext().getType(), mainTypes));
    }

    private void finishLast(PublishWrapper publish, boolean checkCache) {
        int dfId = publish.getDeliveryFileId();
        if (checkCache) {
            DeliveryFileEntity df = DbUtils.exists(dfId) ? rs.getDeliveryFileEntity(dfId) : null;
            if (df != null && df.isPropertyUpdate()) {
                flowLogger.onFlowCompleted(publish.getCdNumbers() != null ? publish.getCdNumbers()
                        : ps.findPublishCdNumbers(publish.getId()));
                rs.updateDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS,
                    DeliveryFileVO.FINAL_FAILED_STATES);

            } else if (publish.getCdNumbers() != null) {
                flowLogger.onFlowCompleted(publish.getCdNumbers());
            }
        }
        
        PublishHelper.onSendDSSuccess(dfId, rs);
        logWRPublishSent(publish, publish.getPublishEntity().getSent());
    }

    private void finishWR(PublishWrapper publish) {

        int dfId = publish.getDeliveryFileId();
        if (!DbUtils.exists(dfId)) {

            Boolean success = publish.getPublishEntity().getSent();
            logWRPublishSent(publish, success);
            if (success) {
                PublishHelper.onSendWhenReadySuccess(DbEntity.NOT_EXIST_ID, false, rs);
            }
            return;
        }

        DeliveryFileEntity df = rs.getDeliveryFileEntity(dfId);
        if (df == null) {
            LOG.error(String.format("Delivery file [%d] is null", publish.getDeliveryFileId()));
        } else {
            finishWR(publish, df);
        }
    }

    private void logWRPublishSent(PublishWrapper publish, Boolean success) {
        if (success != null) {
            flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.DB, success
                ? ILogEvent.PUBLISH_SUCCESSFUL : ILogEvent.PUBLISH_COMPLETED, publish.getId(), publish.getFileName(),
                    publish.getType(), publish.getStatus());
        }
        LOG.info(String.format("Publishing WR package has finished: %s", publish.getFileName()));
    }

    private void finishWR(PublishWrapper publish, DeliveryFileEntity de) {

        boolean ariesSFTP = de.isAriesSFTP();
        boolean mesh = !ariesSFTP && DeliveryPackage.isMeshterm(de.getName());

        if (de.getStatus().getId() == IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS
            || de.getStatus().getId() == IDeliveryFileStatus.STATUS_PUBLISHING_FAILED)  {

            if (mesh) {
                checkMeshDownloadedFlag(de);
            }
            return;
        }

        int dfId = de.getId();
        StatusEntity interim = de.getInterimStatus();

        if ((interim != null && IDeliveryFileStatus.STATUS_PUBLISHING_FAILED == interim.getId())
                || !publish.getPublishEntity().sent()) {

            rs.updateDeliveryFileStatus(de.getId(), IDeliveryFileStatus.STATUS_PUBLISHING_FAILED,
                    DeliveryFileVO.FINAL_FAILED_STATES);

            if (de.getStatus().getId() != IDeliveryFileStatus.STATUS_RND_SOME_FAILED) {
                rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_FAILED, false);
            }

            flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PUBLISH_COMPLETED, dfId,
                de.getName(), publish.getType(), publish.getStatus());
            logWRPublishSent(publish, false);
            
            publish.setNext(null);  // reset next KS publishing

        } else {

            flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PUBLISH_SUCCESSFUL, dfId,
                de.getName(), publish.getType(), publish.getStatus());

            rs.updateDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS,
                DeliveryFileVO.FINAL_FAILED_STATES);

            logWRPublishSent(publish, true);

            String dbName = de.getDb().getTitle();
            boolean preLiveQA = !mesh && !ariesSFTP && BaseType.find(dbName).get().isEditorial()
                && DeliveryPackage.isPreQA(de.isWml3g(), de.getName());
            if (preLiveQA) {
                notifySendingPreLifeQA(publish, de, dbName, ps.findPublishCdNumbers(publish.getId()));
            }

            PublishHelper.onSendWhenReadySuccess(dfId, mesh || DeliveryPackage.isPropertyUpdate(de.getName()) , rs);
        }

        if (mesh) {
            checkMeshDownloadedFlag(de);
        }

        LOG.info(String.format("Publishing delivery file [%d] has finished: %s -> %s", publish.getDeliveryFileId(),
            de.getName(), publish.getFileName()));
    }

    private void notifySendingPreLifeQA(PublishWrapper publish, DeliveryFileEntity de, String dbName,
                                        Collection<String> names) {
        if (!names.isEmpty()) {
            MessageSender.sendPreLifePublishingMessage(dbName, de.getName(), publish.getFileName(),
                names.iterator().next(), de.getIssue().getYear(), de.getIssue().getNumber(), null);
        }
    }

    private void checkMeshDownloadedFlag(DeliveryFileEntity df) {
        IssueEntity ie = df.getIssue();
        if (ie.isMeshtermsDownloading()) {
            IssueStorageFactory.getFactory().getInstance().setIssueMeshtermsDownloading(ie.getId(), false);
        }
        clearRecordCache(df);
    }

    private void clearRecordCache(DeliveryFileEntity de) {
        IRecordCache cache = flowLogger.getRecordCache();
        List<RecordEntity> records = RecordStorageFactory.getFactory().getInstance().getRecordsByDFile(de, false);
        records.forEach(r -> cache.removeRecord(r.getName()));
    }
}

