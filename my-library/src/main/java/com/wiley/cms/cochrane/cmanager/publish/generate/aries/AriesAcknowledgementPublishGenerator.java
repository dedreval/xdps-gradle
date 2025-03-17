package com.wiley.cms.cochrane.cmanager.publish.generate.aries;

import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
public class AriesAcknowledgementPublishGenerator extends AriesAcknowledgementGenerator {

    public AriesAcknowledgementPublishGenerator(ClDbVO db) {
        super(db, "ARIES:ACK_PUBLISH:" + db.getTitle(), PubType.TYPE_ARIES_ACK_P);
    }

    @Override
    protected AriesArchiveHolder createArchiveHolder() {
        return new AriesArchiveHolder(errorCollector, PubType.TYPE_ARIES_ACK_P);
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames() ? getRecordListFromIncludedNames(getItemsFromIncludedNames(count)) : (byIssue()
                ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count) : Collections.emptyList());
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        if (archive.getEntries().isEmpty() && byDeliveryPacket()) {
            processRecord();
            return archive.getEntries();
        }
        return super.createParticularFiles();
    }

    private void processRecord() {
        try {
            DeliveryFileEntity df = checkAriesPackage(null);

            String dfName = df.getName();
            Integer dfId = df.getId();
                        
            String srcFolder = BaseType.find(getDbName()).get().isCDSR()
                                ? FilePathBuilder.JATS.getPathToPackage(df.getIssue().getId(), dfId)
                                : FilePathBuilder.getPathToIssuePackage(df.getIssue().getId(), getDbName(), dfId);

            String importFileName = RecordHelper.findAriesImportSource(srcFolder, rps);

            String manuscriptNumber = AriesHelper.getManuscriptNumberByImportName(importFileName);
            PublishedAbstractEntity pae = findWhenReadyArticle(dfId, manuscriptNumber);
            if (pae == null) {
                throw new CmsException(String.format("%s is not found for an acknowledgement on publication",
                    manuscriptNumber));
            }
            archive.checkNext(dfName, rps, rs, ps);
            onAcknowledgementStart(dfName, pae.getRecordId(), pae.getPubName());
            archive.getEntries().add(new ArchiveEntry(importFileName, importFileName, helper.createAckImport(
                    srcFolder + importFileName, null, null, Now.formatDate(pae.getPublishedDate()), rps)));
            createAckGO(srcFolder, AriesHelper.buildGOFileName(dfName));

            createPublishRecord(pae.getRecordId(), pae.getDeliveryId(),
                    pae.getPubNumber(), pae.getNumber(), archive.getExport().getId());

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, e.getMessage());
        }
    }

    @Override
    protected void createPublishRecord(RecordWrapper rec, int pubNumb, int recordNumber, int publishId) {
        if (byDeliveryPacket()) {
            ps.createPublishRecord(recordNumber, pubNumb, getDeliveryFileId(), publishId, rec.getId());
        } else {
            super.createPublishRecord(rec, pubNumb, recordNumber, publishId);
        }
    }

    @Override
    protected void onAcknowledgementStart(String dfName, Integer recordId, String publisherId) {
        flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.DB,
                ILogEvent.ARIES_ACKNOWLEDGEMENT_START, recordId, publisherId, PubType.TYPE_ARIES_ACK_P, dfName);
    }

    @Override
    protected String buildImportXml(RecordWrapper record, String importPath, DeliveryFileEntity df) throws Exception {
        String onlineDate = record.getPublishedOnlineFinalForm();
        if (onlineDate == null) {
            throw new CmsException(String.format("'%s' date was not set for %s",
                    LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, record.getPubName()));
        }
        return helper.createAckImport(importPath, null, null, Now.formatDate(Now.parseDate(onlineDate)), rps);
    }

    @Override
    protected DeliveryFileEntity findAriesPackage(RecordWrapper rw) throws CmsException {
        if (byDeliveryPacket()) {
            return getDeliveryPackage(getDeliveryFileId(), rw);
        }
        PublishedAbstractEntity pe = rw == null ? null : findWhenReadyArticle(rw);
        if (pe == null || pe.getAcknowledgementId() == null
                || pe.getPubNotified() != PublishProfile.getProfile().get().getDestination().ordinal()) {
            throw new CmsException(String.format("%s is not ready for an acknowledgement on publication",
                    rw == null ? "an article" : rw.getPubName()));
        }
        return getDeliveryPackage(pe.getAcknowledgementId(), rw);
    }
}
