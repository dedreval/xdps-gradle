package com.wiley.cms.cochrane.cmanager.publish.generate.aries;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
public class AriesAcknowledgementDeliverGenerator extends AriesAcknowledgementGenerator {

    public AriesAcknowledgementDeliverGenerator(ClDbVO db) {
        super(db, "ARIES:ACK_DELIVER:" + db.getTitle(), PubType.TYPE_ARIES_ACK_D);
    }

    @Override
    protected AriesArchiveHolder createArchiveHolder() {
        return new AriesArchiveHolder(errorCollector, PubType.TYPE_ARIES_ACK_D);
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames()
            ? getRecordListFromIncludedNames(getItemsFromIncludedNames(count)) : (byDeliveryPacket()
                ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(), startIndex, count)
                    : (byIssue() ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count)
                        : Collections.emptyList()));
    }

    @Override
    protected String buildImportXml(RecordWrapper record, String importPath, DeliveryFileEntity de) throws Exception {
        Date targetPubDate = CmsUtils.isScheduledIssue(
                record.getIssueId()) && !record.isPublishingCanceled() ? record.getPublishedDate() : de.getDate();
        return helper.createAckImport(importPath, null, Now.formatDate(targetPubDate), null, rps);
    }

    @Override
    protected void onAcknowledgementStart(String dfName, Integer recordId, String cdNumber) {
        flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.DB,
                ILogEvent.ARIES_ACKNOWLEDGEMENT_START, recordId, cdNumber, PubType.TYPE_ARIES_ACK_D, dfName);
    }

    @Override
    protected DeliveryFileEntity findAriesPackage(RecordWrapper rw) throws CmsException {

        if (byDeliveryPacket()) {
            DeliveryFileEntity ret = getDeliveryPackage(getDeliveryFileId(), rw);
            if (ret != null && !DeliveryFileEntity.isAriesSFTP(ret.getType())) {
                ret = findAriesPackageByWhenReadyArticle(rw);
            }
            return ret;
        }
        return (byRecords() || byIssue()) ? findAriesPackageByWhenReadyArticle(rw) : rw.getDeliveryFile().getEntity();
    }

    private  DeliveryFileEntity findAriesPackageByWhenReadyArticle(RecordWrapper rw) {
        PublishedAbstractEntity pe = findWhenReadyArticle(rw);
        return pe == null ? null : rs.getDeliveryFileEntity(pe.getInitialDeliveryId());
    }
}
