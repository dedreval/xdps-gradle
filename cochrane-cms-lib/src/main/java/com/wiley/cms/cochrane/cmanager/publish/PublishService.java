package com.wiley.cms.cochrane.cmanager.publish;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWClient;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWDeleteMsg;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWMsg;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWResponse;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.services.WREvent;
import com.wiley.cms.cochrane.services.PublishEvent;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.MessageBuilder;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * Type comments here.     *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Stateless
@Local(IPublishService.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PublishService implements IPublishService {
    private static final Logger LOG = Logger.getLogger(PublishService.class);
    private static final int MSG_DELAY_INTERVAL = 1000;

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/publishing")
    private javax.jms.Queue publishQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/entire_publishing")
    private javax.jms.Queue entirePublishQueue;

    @Resource(mappedName = CDSRPublishQueue.MAP_NAME)
    private javax.jms.Queue clsysrevQueue;

    @Resource(mappedName = HPPublishQueue.MAP_NAME)
    private javax.jms.Queue hpQueue;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "BulkPublishManager")
    private IBulkPublishManager bpm;

    @EJB(beanName = "PublishStorage")
    private IPublishStorage ps;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    public boolean acceptLiteratumDeliveryOnLoadToPublish(LiteratumSentConfirm response, Date responseDate) {
        PublishDestination dest = WREvent.determinePublishDestinationWithLiteratum(response.getSourceSystem(),
                response.getEventType(), false);
        if (PublishDestination.WOLLIT == dest) {
            acceptLiteratumDeliveryOnLoadToPublish(PublishHelper.defineBaseTypeByWolLitDoi(response.getDoi()), response,
                dest, false, CochraneCMSPropertyNames.getLiteratumErrorFilterWol(), PubType.LITERATUM_DB_TYPES);

        } else if (PublishDestination.SEMANTICO == dest) {
            acceptLiteratumDeliveryOnLoadToPublish(PublishHelper.defineBaseTypeByHWDoi(response.getDoi()), response,
                dest, true, CochraneCMSPropertyNames.getLiteratumErrorFilterSemantico(), PubType.SEMANTICO_DB_TYPES);
        } else {
            return false;
        }
        return true;
    }

    private void acceptLiteratumDeliveryOnLoadToPublish(BaseType bt, LiteratumSentConfirm response,
        PublishDestination dest, boolean hw, String[] errFilter, Collection<Integer> pubTypesIds) {

        if (bt == null) {
            LOG.warn(String.format("cannot define database from doi: %s", response.getDoi()));
            return;
        }
        flowLogger.getActivityLog().info(LogEntity.EntityLevel.FILE, ILogEvent.GET_PUBLISH_NOTIFICATION,
                DbEntity.NOT_EXIST_ID, response.getDeliveryId(), dest.getShortStr(), response.getEventType());
        boolean centralPartialErr = false;
        boolean emulateCentralOnline = bt.isCentral() && CochraneCMSPropertyNames.isLiteratumEventPublishTestMode();
        LiteratumResponseReport report = new LiteratumResponseReport(response, dest, bt.getId());
        if (report.takeLiteratumDeliveryErrorMessage(response.getMessages(), errFilter)) {
            checkLiteratumDeliveryErrorMessage(bt, response.getDoi(), pubTypesIds, dest, hw, false, report);
        }
        List<LiteratumSentConfirm.HasPart> parts = response.getHasPart();
        for (LiteratumSentConfirm.HasPart part: parts) {
            if (part != null && report.takeLiteratumDeliveryErrorMessage(part.getMessages(), errFilter)
                && checkLiteratumDeliveryErrorMessage(bt, part.getDoi(), pubTypesIds, dest, hw, true, report)) {
                centralPartialErr = emulateCentralOnline;
            }
        }
        if (emulateCentralOnline) {
            LiteratumResponseChecker.Responder.instance().imitateCentralResponseForHW(
                response.getDeliveryId(), centralPartialErr ? Collections.emptyList() : null, null, true);
        }
        report.sendReport();
    }

    private boolean checkLiteratumDeliveryErrorMessage(BaseType bt, String doi, Collection<Integer> pubTypesIds,
            PublishDestination dest, boolean hw, boolean fromParts, LiteratumResponseReport report) {

        List<PublishRecordEntity> failed;
        if (fromParts || (hw && !bt.isCentral())) {
            String cdNumber = bt.getCdNumberByDoi(doi);
            int pub =  bt.isCDSR() ? RevmanMetadataHelper.parsePubNumber(doi) : 0;
            failed = ps.setPublishRecordsFailed(RecordHelper.buildRecordNumber(cdNumber), pub, pubTypesIds,
                    bt.getDbId(), report.deliveryId, report.getAfterDate());
        }  else {
            failed = ps.setPublishRecordsFailed(pubTypesIds, bt.getDbId(), report.deliveryId, report.getAfterDate());
        }
        return !failed.isEmpty() && acceptLiteratumDeliveryErrorMessage(bt, dest, hw, failed, report);
    }

    private boolean acceptLiteratumDeliveryErrorMessage(BaseType bt,
            PublishDestination dest, boolean hw, List<PublishRecordEntity> failedList, LiteratumResponseReport report) {
        boolean cdsr = bt.isCDSR();
        Collection<String> checkedNames = new HashSet<>();
        int wrTypeId = PublishProfile.getProfile().get().getDestination().getWhenReadyTypeId(dest.getLastType());
        PublishEntity publishPackage = failedList.get(0).getPublishPacket();
        boolean hwPackage = hw && !publishPackage.getDb().isEntire();
        OpStats statsByDf = bt.isCentral() ? new OpStats() : OpStats.EMPTY;

        for (PublishRecordEntity pre: failedList) {
            String cdNumber = RecordHelper.buildCdNumber(pre.getNumber());
            int pub = pre.getPubNumber();
            String pubName = cdsr ? RevmanMetadataHelper.buildPubName(cdNumber, pub) : cdNumber;
            if (checkedNames.contains(pubName)) {
                continue;
            }
            if (wrTypeId == pre.getPublishPacket().getPublishType()) {
                String eventStr = PublishEvent.toString(cdNumber, pub, report.event);
                LOG.debug(String.format("%s got %s", eventStr, report.lastErrorMessage));
                flowLogger.onProductError(ILogEvent.PRODUCT_ERROR, null, cdNumber, pub, // log failed event for WR
                     report.lastErrorMessage == null ? null : report.lastErrorMessage.toString(), bt.hasSFLogging());
            }
            Integer dfId = pre.getDeliveryId();
            if (hwPackage && report.lastErrorMessage != null) {
                RecordEntity record = rm.setRecordState(RecordEntity.STATE_HW_PUBLISHING_ERR,
                        publishPackage.getDb().getId(), cdNumber);
                if (record != null) {
                    dfId = record.getDeliveryFileId();
                    flowLogger.getActivityLog().logRecordError(ILogEvent.PRODUCT_ERROR, record.getId(), cdNumber,
                        bt.getDbId(), publishPackage.getDb().getIssue().getFullNumber(),
                            report.lastErrorMessage.toString());
                }
            }
            checkedNames.add(pubName);
            statsByDf.addTotalCompletedByKey(dfId, pre.getNumber());
            report.appendLiteratumDeliveryErrorMessage(pubName);
        }
        if (bt.isCentral()) {
            addCentralLoggingOnError(bt, publishPackage.getDb().getId(), publishPackage.getFileName(), hw,
                    dest.getShortStr(), statsByDf);
        }
        return !checkedNames.isEmpty();
    }

    private void addCentralLoggingOnError(BaseType bt, Integer dbId, String fileName, boolean hw, String dest,
                                          OpStats stats) {
        int eventId = hw ? ILogEvent.PRODUCT_PUBLISHED_HW : ILogEvent.PRODUCT_PUBLISHED_WOLLIT;
        rs.getTotalPublishStatsOnPublished(dbId, hw ? PubType.SEMANTICO_DB_TYPES : PubType.LITERATUM_DB_TYPES, stats);
        String errorMsg = String.format("some errors happened during publishing on %s", dest);
        stats.getMultiCounters().forEach((dfId, st) -> addCentralLoggingOnError(eventId, bt, fileName, dfId,
                st.getTotalCompleted(), st.getTotal(), errorMsg));
    }

    private void addCentralLogging(BaseType bt, Integer dbId, String fileName, boolean hw, OpStats stats) {
        int eventId = hw ? ILogEvent.PRODUCT_PUBLISHED_HW : ILogEvent.PRODUCT_PUBLISHED_WOLLIT;
        rs.getTotalPublishStatsOnPublished(dbId, hw ? PubType.SEMANTICO_DB_TYPES : PubType.LITERATUM_DB_TYPES, stats);
        stats.getMultiCounters().forEach((dfId, st) -> flowLogger.onPackageFlowEvent(eventId, bt, fileName, dfId,
                PackageChecker.METAXIS, null, st.getTotalCompleted(), st.getTotal()));
    }

    private void addCentralLoggingOffline(BaseType bt, String fileName, OpStats stats) {
        
        stats.getMultiCounters().forEach((dfId, st) -> flowLogger.onPackageFlowEvent(ILogEvent.PRODUCT_OFFLINE_HW,
                bt, fileName, dfId, PackageChecker.METAXIS, null, st.getTotalCompleted(), st.getTotal()));
    }

    private void addCentralLoggingOnError(int eventId, BaseType bt, String fileName, Integer dfId,
                                          Integer count, Integer totalCount, String errorMsg) {
        flowLogger.onPackageFlowEventError(eventId, bt, fileName, dfId, PackageChecker.METAXIS, null, errorMsg,
                count, totalCount);
    }

    public boolean acceptLiteratumDeliveryOnline(LiteratumSentConfirm response, Date responseDate) {
        PublishDestination dest = WREvent.determinePublishDestinationWithLiteratum(response.getSourceSystem(),
            response.getEventType(), true);
        return (PublishDestination.WOLLIT == dest
                    && acceptLiteratumDelivery(response, response.getDeliveryId(), responseDate, dest, false)
            || (PublishDestination.SEMANTICO == dest
                    && acceptLiteratumDelivery(response, response.getDeliveryId(), responseDate, dest, true)));
    }

    private boolean acceptLiteratumDelivery(LiteratumSentConfirm response, String fileName, Date responseDate,
                                            PublishDestination dest, boolean hw) {
        boolean centralHW = hw && PublishHelper.isCentralHWDoi(response.getDoi());
        flowLogger.getActivityLog().info(LogEntity.EntityLevel.FILE, ILogEvent.GET_PUBLISH_NOTIFICATION,
                DbEntity.NOT_EXIST_ID, fileName, dest.getShortStr(), response.getEventType());
        boolean centralOffline = centralHW && response.isOffline();
        List<Integer> publishIds = !centralOffline && (centralHW || !hw) ? ps.findPublishWait(fileName)
                : Collections.emptyList();
        boolean centralWOLLITManual = false;
        if (publishIds.isEmpty()) {
            boolean centralWOLLIT = !hw && response.getDoi().startsWith(Constants.DOI_CENTRAL);
            if (centralWOLLIT) {
                publishIds = ps.findPublishesByFileName(DatabaseEntity.CENTRAL_KEY, fileName);
            }
            if (publishIds.isEmpty()) {
                return centralHW && acceptCentralHwOffline(response, fileName, centralOffline) || centralWOLLIT;
            } else {
                centralWOLLITManual = true;
            }
        }
        OpStats statsByDf = new OpStats();
        PublishWrapper pw = bpm.acceptLiteratumDelivery(response, responseDate, publishIds, hw, statsByDf);
        String err = statsByDf.getErrors();
        if (pw != null) {
            if (centralWOLLITManual) {
                pw.setCdNumbers(null);   // reset automatic publishing to HW for manual operations
            }
            onLiteratumDeliveryAccepted(pw, fileName, responseDate, dest, hw, statsByDf, err);

        } else if (err != null && !err.isEmpty()) {
            String cdNumbers = pw.getCdNumbers() != null ? String.join(", ", pw.getCdNumbers()) : "N/A";
            MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_ERROR, dest.getShortStr(),
                    PublishHelper.buildPublicationEventErrorMessage(fileName, err,
                            response.getRawData()), fileName, cdNumbers);
        }
        return true;
    }

    private boolean acceptCentralHwOffline(LiteratumSentConfirm response, String fileName, boolean offline) {
        if (offline) {
            BaseType bt = BaseType.getCentral().get();
            List<LiteratumSentConfirm.HasPart> parts = response.getHasPart();
            Set<Integer> recordNumbers = new HashSet<>();
            for (LiteratumSentConfirm.HasPart part: parts) {
                if (part != null) {
                    recordNumbers.add(bt.getProductType().buildRecordNumber(bt.getCdNumberByDoi(part.getDoi())));
                }
            }
            LOG.info(String.format("%d (%d) CENTRAL offline was received", parts.size(), recordNumbers.size()));
            addCentralLoggingOffline(bt, fileName != null && !fileName.isEmpty() ? fileName : null,
                    rs.getPublishStatsOnOffline(bt, recordNumbers));
        }
        return true;
    }

    private void onLiteratumDeliveryAccepted(PublishWrapper pw, String fileName, Date responseDate,
                                             PublishDestination dest, boolean hw, OpStats statsByDf, String err) {
        ClDbEntity db = pw.getPublishEntity().getDb();
        BaseType bt = BaseType.find(db.getTitle()).get();
        if (pw.getCdNumbers() != null) {
            boolean cca = bt.isCCA();
            String packageName = // cca doesn't support a new package name generation from PublishWrapper
                    cca ? "a package for cca" : pw.getNewPackageName();
            if (cca && !hw) {
                pw.getCdNumbers().forEach(cdNumber -> flowLogger.onProductPublished(
                        cdNumber, dest, responseDate, null, null, bt.hasSFLogging(), false));
            }
            if (err == null || err.isEmpty()) {
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_SUCCESS, db.getTitle(),
                        String.format(CochraneCMSProperties.getProperty("literatum.publishing_success"), fileName,
                                packageName), CochraneCMSPropertyNames.isLiteratumEventPublishTestMode());
            } else {
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_WARN, db.getTitle(),
                        String.format(CochraneCMSProperties.getProperty("literatum.publishing_warn"), fileName,
                                packageName, err), CochraneCMSPropertyNames.isLiteratumEventPublishTestMode());
            }
            publishOnPublicationEventAccepted(bt, db, pw);
        }
        if (bt.isCentral()) {
            addCentralLogging(BaseType.getCentral().get(), pw.getPublishEntity().getDb().getId(), fileName, hw,
                    statsByDf);
        }
    }

    private void publishOnPublicationEventAccepted(BaseType bt, ClDbEntity db, PublishWrapper pw) {
        List<PublishWrapper> list = new ArrayList<>();
        list.add(pw);
        if (db.isEntire()) {
            publishEntireDb(db.getTitle(), list);
            return;
        }
        if (PubType.isSemantico(pw.getPath().getPubType().getMajorType()) && bt.isCentral()) {
            PublishWrapper pwAwait = PublishWrapper.createIssuePublishWrapper(PubType.TYPE_DS, db.getTitle(),
                    db.getId(), false, true, pw.getStartDate());
            if (pwAwait != null) {
                pwAwait.initWorkflow(true, true, false, false);
            }
            pw.setPublishToAwait(pwAwait);
        }
        publishDb(db.getId(), list);
    }

    public String notifySemantico(int publishId, String filename, String type, String freq,
                                  boolean disableStaticContent, boolean testMode, HWClient hw) throws Exception {
        return notifySemantico(new HWMsg(publishId, filename, type, freq, disableStaticContent), false, testMode, hw);
    }

    public String notifySemantico(int publishId, String type, String freq, Collection<String> deletedCdNumbers,
                                  boolean testMode, HWClient hw) throws Exception {
        HWDeleteMsg msg = new HWDeleteMsg(publishId, type, freq, deletedCdNumbers.toArray(FileUtils.ZERO_STRING_ARRAY));
        if (testMode) {
            hw.logRequest(msg.asJSONString());
        }
        return addDoiInfo(testMode ? "test mode!" : hw.deleteDois(msg), deletedCdNumbers.size());
    }

    private String addDoiInfo(String result, int size) {
        return String.format("Number of records: %d\n%s", size, result != null ? result : "");
    }

    public String notifySemantico(HWMsg msg, boolean deleteDois, boolean testMode, HWClient hw) throws Exception {
        HWResponse ret;
        if (testMode) {
            hw.logRequest(msg.asJSONString());
            ret = new HWResponse("test mode");

        } else if (!deleteDois) {
            ret = hw.sendPackage(msg);
        } else {
            return hw.deleteDois(msg);
        }
        return ret != null ? ret.toString() : null;
    }

    public void publishWhenReadyHW(String dbName, int dbId, Set<String> names, String hwFrequency) {
        publishWhenReadyByNames(dbName, dbId, IPublish.BY_WHEN_READY, PubType.TYPE_SEMANTICO, names, hwFrequency, null);
    }

    public void sendAcknowledgementAriesOnPublish(String dbName, PublishedAbstractEntity pae) {
        if (pae.getAcknowledgementId() != null) {
            LOG.debug("%s is to be acknowledged on publication", pae);
            Set<String> names = Collections.singleton(pae.getRecordName());
            publishWhenReadyByNames(dbName, pae.getDbId(), pae.getAcknowledgementId(),
                    PubType.TYPE_ARIES_ACK_P, names, null, flowLogger.findTransactionId(pae.getInitialDeliveryId()));
        }
    }

    public void sendAcknowledgementAriesOnPublish(String dbName, int dbId, int ackId, String manuscriptNumber,
                                                  Integer whenReadyId, boolean generateNewUUID) throws Exception {
        PublishedAbstractEntity pae = ps.updateWhenReadyOnPublished(whenReadyId, ackId, false);
        if (pae != null) {
            publishWhenReadySync(dbName, pae.getDbId(), ackId, pae.getRecordName(), PubType.TYPE_ARIES_ACK_P,
                    generateNewUUID ? UUIDEntity.UUID : flowLogger.findTransactionId(pae.getInitialDeliveryId()));
        } else {
            LOG.warn("cannot find a published when-ready record by manuscript: %s [%d]", manuscriptNumber, whenReadyId);
        }
    }

    public void publishWhenReadySync(String dbName, int dbId, int ackId, String cdNumber, String pubType, String uuid)
            throws Exception {
        PublishWrapper pw = PublishWrapper.createIssuePublishWrapperEx(pubType, dbName, dbId, ackId, new Date(),
                new boolean[] {true, false, false});
        pw.setGenerate(true);
        pw.setSend(true);
        if (cdNumber != null) {
            Set<String> names = new HashSet<>();
            names.add(cdNumber);
            pw.setCdNumbers(names);
            pw.setTransactionId(uuid);
        }
        QueuelessIssuePublisher publisher = new QueuelessIssuePublisher(rs, ps, flowLogger);
        publisher.start(pw, dbId);
    }

    public void sendAcknowledgementAries(int publishId) throws Exception {
        PublishEntity pe = ifNotPublished(publishId);
        if (pe != null) {
            boolean onPublish = PubType.isAriesAckPublish(pe.getPublishType());
            if (!onPublish && !PubType.isAriesAckDeliver(pe.getPublishType())) {
                throw new Exception(String.format("publish type [%d] doesn't match to %s or %s", pe.getPublishType(),
                        PubType.TYPE_ARIES_ACK_D, PubType.TYPE_ARIES_ACK_P));
            }
            PublishWrapper pw = PublishWrapper.createPublishWrapperToResendOnly(pe,
                    onPublish ? PubType.TYPE_ARIES_ACK_P : PubType.TYPE_ARIES_ACK_D, true);
            publishDeliveryPacket(pe.getDb().getId(), pw, false);
        }
    }

    public void sendWhenReadyHW(int publishId, String hwPayLoad) {
        PublishEntity pe = ifNotPublished(publishId);
        if (pe == null) {
            return;
        }
        boolean del = PubType.SEMANTICO_DEL_TYPES.contains(pe.getPublishType());
        boolean wr = !del && pe.getPublishType().equals(
                PublishProfile.PUB_PROFILE.get().getDestination().getWhenReadyTypeId(PubType.TYPE_SEMANTICO));
        PublishWrapper pw = PublishWrapper.createPublishWrapperToResendOnly(pe, PubType.TYPE_SEMANTICO, wr || del);
        if (!pe.noPrioritySet()) {
            pw.setHWFrequency(HWFreq.values()[pe.getHwFrequency()].getValue());
        }
        if (wr && !prepareRecords4resentHW(pe)) {
            return;
        }
        publishDeliveryPacket(pe.getDb().getId(), pw, pe.highPriority());
    }

    private boolean prepareRecords4resentHW(PublishEntity pe) {
        Collection<String> names = ps.findPublishCdNumbers(pe.getId());
        if (names.isEmpty()) {
            LOG.info(String.format("%s [%d] contains no records", pe.getFileName(), pe.getId()));
            return false;
        }
        rm.setRecordState(RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION, pe.getDb().getId(), names);
        return true;
    }

    private PublishEntity ifNotPublished(int publishId) {
        PublishEntity pe = rs.findPublish(publishId);
        if (pe == null) {
            LOG.info(String.format("publishing package [%d] not found", publishId));
        } else if (pe.sent()) {
            LOG.info(String.format("%s [%d] was already sent", pe.getFileName(), pe.getId()));
            pe = null;
        }
        return pe;
    }

    public void publishWhenReadyDS(String dbName, int dbId, int dfId, String cdNumber) {
        Set<String> names = Collections.singleton(cdNumber);
        rm.setRecordState(RecordEntity.STATE_WR_PUBLISHED, RecordEntity.STATE_DS_PUBLISHED, dbId, names);
        publishDbSync(dbId, prepareWhenReady(dbName, dbId, dfId, PubType.TYPE_DS, names, null,
                flowLogger.findTransactionId(dfId)));
    }

    private List<PublishWrapper> prepareWhenReady(String dbName, int dbId, int dfId, String pubType, Set<String> names,
                                                  String hwFreq, String uuid) {
        List<PublishWrapper> publishList = new ArrayList<>();
        PublishWrapper pw = createWhenReadyPublishWrapper(new PublishJobs(dbName, dbId, dfId, hwFreq),
                pubType, new Date(), false);
        if (pw != null) {
            pw.setCdNumbers(names);
            pw.setTransactionId(uuid);
            publishList.add(pw);
        }
        return publishList;
    }

    private void publishWhenReadyByNames(String dbName, int dbId, int dfId, String pubType,
                                         @NotNull Set<String> names, String hwFrequency, String uuid) {
        if (!names.isEmpty()) {
            publishDeliveryPacket(dbId, prepareWhenReady(dbName, dbId, dfId, pubType, names, hwFrequency, uuid),
                    CochraneCMSPropertyNames.getPublishAfterUploadDelay(), HWFreq.isHighPriority(hwFrequency));
        }
    }

    public void publishWhenReady(int dfId) throws Exception {
        DeliveryFileVO df = rs.getDeliveryFileVO(dfId);
        if (df == null) {
            LOG.warn(String.format("cannot publish delivery package [%d] as it hasn't been found", dfId));
            return;
        }
        String dfName = df.getName();
        if (df.getStatus() == IDeliveryFileStatus.STATUS_PUBLISHING_STARTED)  {
            throw new CmsException(String.format("WR delivery package %s [%d] is publishing now by other process",
                    dfName, dfId));
        }
        if (df.getStatus() == IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS)  {
            LOG.info(String.format("WR delivery package %s [%d] was already published with success", dfName, dfId));
            return;
        }
        rs.updateDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_STARTED,
                DeliveryFileVO.FINAL_FAILED_STATES);
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_STARTED, true);
        BaseType bt = BaseType.find(df.getDbName()).get();
        boolean central = bt.isCentral();
        boolean mesh = !central && DeliveryPackage.isMeshterm(dfName);
        boolean pu = !mesh && !central && DeliveryPackage.isPropertyUpdate(dfName);
        boolean puMl3g = pu && DeliveryPackage.isPropertyUpdateMl3g(dfName);

        publishWhenReady(bt, df, mesh, pu, puMl3g, !mesh && !central && !pu
                && DeliveryFileEntity.isAriesSFTP(df.getType()));
    }

    public void publishWhenReady(BaseType bt, DeliveryFileVO df, boolean mesh, boolean pu, boolean puPdf,
                                 boolean aries) {
        int dbId = df.getDbId();
        int dfId = df.getId();
        if (mesh) {
            Set<String> pubTypes = new HashSet<>();
            pubTypes.add(PubType.TYPE_SEMANTICO);
            pubTypes.add(PubType.TYPE_DS_MONTHLY);
            publishWhenReady(bt, dbId, dfId, pubTypes, HWFreq.BULK.getValue(), true);
        } else if (pu) {
            Set<String> pubTypes = new HashSet<>();
            if (puPdf) {
                pubTypes.add(PubType.TYPE_SEMANTICO);
            }
            pubTypes.add(PubType.TYPE_DS);
            publishWhenReady(bt, dbId, dfId, pubTypes,
                isHighPublicationPriority(dfId) ? HWFreq.HIGH.getValue() : null, false);
        } else {
            boolean highPriority = aries && isHighPublicationPriority(dfId);
            publishWhenReady(bt, df.getIssue(), dbId, dfId, highPriority ? HWFreq.HIGH.getValue() : null, highPriority);
        }
    }

    private boolean isHighPublicationPriority(int dfId) {
        List<PublishedAbstractEntity> list = rm.getWhenReadyByDeliveryPackage(dfId);
        for (PublishedAbstractEntity pae: list) {
            if (pae.isHighFrequency()) {
                return true;
            }
        }
        return false;
    }

    public void publishWhenReady(BaseType baseType, int dbId, int dfId, Set<String> pubTypes, String hwFrequency,
                                 boolean generateNewUUID) {
        if (pubTypes == null || pubTypes.isEmpty()) {
            return;
        }
        String dbName = baseType.getId();
        DeliveryFileEntity df = rs.getDeliveryFileEntity(dfId);
        rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_PROCESSING, dfId, true);
        int recordCount = rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_PROCESSING,
                RecordEntity.STATE_WR_PUBLISHING, dfId);
        if (recordCount == 0) {
            LOG.warn("no last records are belonging to %s found", df.getName());
            if (DeliveryFileEntity.isAriesSFTP(df.getType()) && pubTypes.contains(PubType.TYPE_ARIES_ACK_D)) {
                List<PublishedAbstractEntity> list = rm.getWhenReadyByDeliveryPackage(dfId);
                Set<String> names = new HashSet<>();
                list.forEach(pa -> names.add(pa.getRecordName()));
                publishWhenReadyByNames(dbName, dbId, dfId, PubType.TYPE_ARIES_ACK_D, names, hwFrequency,
                        generateNewUUID ? UUIDEntity.UUID : flowLogger.findTransactionId(dfId));
            }
            return;
        }
        rs.updateDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_STARTED,
                DeliveryFileVO.FINAL_FAILED_STATES);
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PUBLISHING_STARTED, true);
        List<PublishWrapper> list = new ArrayList<>();
        Date dt = new Date();
        PublishJobs jobs = new PublishJobs(dbName, dbId, dfId, hwFrequency);
        PublishWrapper prev = addMandatory(null, jobs, dt, PubType.TYPE_LITERATUM,
                !pubTypes.contains(PubType.TYPE_LITERATUM), true, list);
        prev = addMandatory(prev, jobs, dt, PubType.TYPE_SEMANTICO,
                !pubTypes.contains(PubType.TYPE_SEMANTICO), true, list);
        prev = addMandatory(prev, jobs, dt, PubType.TYPE_ARIES_ACK_D,
                !pubTypes.contains(PubType.TYPE_ARIES_ACK_D), true, list);
        if (pubTypes.contains(PubType.TYPE_DS)
                && (generateNewUUID || !addByDelivery(prev, jobs, dt, PubType.TYPE_DS, list))) {
            addMandatory(prev, jobs, dt, PubType.TYPE_DS, false, true, list);
        } else {
            addMandatory(prev, jobs, dt, PubType.TYPE_DS_MONTHLY,
                !pubTypes.contains(PubType.TYPE_DS_MONTHLY), true, list);
        }
        if (generateNewUUID) {
            list.forEach(pw -> pw.setTransactionId(UUIDEntity.UUID));
        }
        publishDeliveryPacket(dbId, list, CochraneCMSPropertyNames.getPublishAfterUploadDelay(),
                HWFreq.isHighPriority(hwFrequency));
    }

    private boolean addByDelivery(PublishWrapper start, PublishJobs jobs, Date dt, String pubType,
                                  Collection<PublishWrapper> ret) {
        Map<String, Set<String>> map = new HashMap<>();
        List<Object[]> list = rs.getRecordsByDeliveryFile(jobs.dfId);
        for (Object[] names: list) {
            String cdNumber = names[0].toString();
            String uuid = flowLogger.findTransactionId(cdNumber);
            Set<String> set = map.computeIfAbsent(uuid, f -> new HashSet<>());
            set.add(cdNumber);
        }
        PublishWrapper prev = start;
        for (Map.Entry<String, Set<String>> entry: map.entrySet())  {
            PublishWrapper pw = addMandatory(prev, jobs, dt, pubType, false, true, ret);
            if (pw != prev) {
                pw.setTransactionId(entry.getKey());
                pw.setCdNumbers(entry.getValue());
            }
            prev = pw;
        }
        return !map.isEmpty();
    }

    private void publishWhenReady(BaseType bt, Integer issueId, int clDbId, int dfId, String hwFrequency,
                                  boolean highPriority) {
        boolean central = bt.isCentral();
        int recordCount = central ? 0 : rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_PROCESSING,
            RecordEntity.STATE_WR_PUBLISHING, dfId);
        if (recordCount == 0) {
            recordCount = rs.getRecordCountByDeliveryFileAndState(dfId, 
                    central ? RecordEntity.STATE_UNDEFINED : RecordEntity.STATE_WR_PUBLISHING);
        }
        if (central) {
            if (recordCount != 0) {
                flowLogger.onPackageFlowEvent(ILogEvent.PRODUCT_PUBLISHING_STARTED, bt, null, dfId,
                        PackageChecker.METAXIS, null, recordCount, recordCount);
            } else if (rs.getDeletedRecordsCount(bt, dfId) == 0) {
                return;
            }
        }
        Date dt = new Date();
        List<PublishWrapper> pubList = recordCount > 0
                ? publishWhenReadyWithRecords(bt, issueId, clDbId, dfId, dt, hwFrequency)
                : publishWhenReadyWithoutRecords(bt, issueId, clDbId, dfId, dt, hwFrequency);
        publishDeliveryPacket(clDbId, pubList, CochraneCMSPropertyNames.getPublishAfterUploadDelay(), highPriority);
    }

    private List<PublishWrapper> publishWhenReadyWithRecords(BaseType bt, Integer issueId, int clDbId, int dfId,
                                                             Date dt, String hwFrequency) {
        List<PublishWrapper> ret = new ArrayList<>();
        PublishDestination dest = PublishProfile.PUB_PROFILE.get().getDestination();
        Collection<String> keyTypes = dest.getMainTypes();
        PublishJobs jobs = new PublishJobs(bt.getId(), clDbId, dfId, hwFrequency);
        boolean central = bt.isCentral();

        PublishWrapper prev = addMandatory(null, jobs, dt, PubType.TYPE_SEMANTICO,
            keyTypes.contains(PubType.TYPE_SEMANTICO), ret);
        prev = addMandatory(prev, jobs, dt, PubType.TYPE_LITERATUM,
            keyTypes.contains(PubType.TYPE_LITERATUM), ret);
        prev = addWhenReady(keyTypes, prev, jobs, dt, central, ret);
        if (bt.isCDSR() && RevmanPackage.hasPreviousEditorial()) {
            addEditorial(prev, jobs, dt, dest, ret);

        } else if (central && rs.getDeletedRecordsCount(bt, dfId) > 0) {
            createWhenReadyPublishWrapper(jobs, PubType.TYPE_SEMANTICO_DELETE, prev, ret, dt, false);
        }
        return ret;
    }

    private PublishWrapper addMandatory(PublishWrapper prev, PublishJobs jobs, Date dt, String pubType,
                                        boolean notAdd, Collection<PublishWrapper> ret) {
        PublishWrapper pw = notAdd ? null : createWhenReadyPublishWrapper(jobs, pubType, prev, ret, dt, false);
        return pw != null ? pw : prev;
    }

    private PublishWrapper addMandatory(PublishWrapper prev, PublishJobs jobs, Date dt, String pubType,
                                        boolean notAdd, boolean incremental, Collection<PublishWrapper> ret) {
        PublishWrapper pw = notAdd ? null : createWhenReadyPublishWrapper(jobs, pubType, prev, ret, dt, incremental);
        return pw != null ? pw : prev;
    }

    private PublishWrapper addEditorial(PublishWrapper prev, PublishJobs jobs, Date dt, PublishDestination dest,
                                        Collection<PublishWrapper> ret) {
        return !dest.hasPubType(PubType.TYPE_SEMANTICO_TOPICS) ? null
                : createWhenReadyPublishWrapper(jobs, PubType.TYPE_SEMANTICO_TOPICS, prev, ret, dt, false);
    }

    private PublishWrapper addWhenReady(Iterable<String> keyTypes, PublishWrapper prev,
                                        PublishJobs jobs, Date dt, boolean central, Collection<PublishWrapper> ret) {
        PublishWrapper retPrev = prev;
        PublishWrapper pwLit = null;
        PublishWrapper pwHW = null;
        for (String keyType: keyTypes) {
            if (PubType.TYPE_SEMANTICO.equals(keyType)) {
                pwHW = createWhenReadyPublishWrapper(jobs, keyType, null, null, dt, false);
                continue;
            }
            retPrev = createWhenReadyPublishWrapper(jobs, keyType, retPrev, ret, dt, false);
            if (PubType.TYPE_LITERATUM.equals(keyType)) {
                pwLit = retPrev;
            }
        }
        if (pwLit != null && pwHW != null) {
            pwLit.setPublishToAwait(pwHW);
            if (central) {
                // any 'aut' central packages for HW should be handled by HW LTP events for common DS monthly delivery
                pwHW.setPublishToAwait(createWhenReadyPublishWrapper(jobs, PubType.TYPE_DS, null, ret, dt, false));
            }
        }
        return retPrev;
    }

    private List<PublishWrapper> publishWhenReadyWithoutRecords(BaseType bt, Integer issueId, int clDbId, int dfId,
                                                                Date dt, String hwFrequency) {
        List<PublishWrapper> ret = Collections.emptyList();
        if (bt.isCentral()) {
            PublishJobs jobs = new PublishJobs(bt.getId(), clDbId, dfId, hwFrequency);
            ret = new ArrayList<>();
            createWhenReadyPublishWrapper(jobs, PubType.TYPE_SEMANTICO_DELETE, null, ret, dt, false);

        } else if (RevmanPackage.hasPreviousEditorial()) {
            PublishJobs jobs = new PublishJobs(bt.getId(), clDbId, dfId, hwFrequency);
            ret = new ArrayList<>();
            addEditorial(null, jobs, dt, PublishProfile.PUB_PROFILE.get().getDestination(), ret);
        }
        return ret;
    }

    private PublishWrapper createWhenReadyPublishWrapper(PublishJobs jobs, String exportType,
        PublishWrapper prev, Collection<PublishWrapper> publishList, Date startDate, boolean incremental) {

        PublishWrapper ret = createWhenReadyPublishWrapper(jobs, exportType, startDate, incremental);
        if (ret != null) {
            if (prev != null) {
                prev.setNext(ret);
            }
            if (publishList != null && publishList.isEmpty()) {
                publishList.add(ret);
            }
        }
        return ret;
    }

    private void publishDeliveryPacket(int clDbId, PublishWrapper pw, boolean highPriority) {
        List<PublishWrapper> publishList = new ArrayList<>();
        publishList.add(pw);
        publishDeliveryPacket(clDbId, publishList, 0, highPriority);
    }

    private void publishDeliveryPacket(int dbId, List<PublishWrapper> publishList, int delay, boolean highPriority) {
        try {
            prepare(dbId, publishList);
            for (PublishWrapper publish : publishList) {
                JMSSender.send(
                    connectionFactory, highPriority ? hpQueue : clsysrevQueue, new JMSSender.MessageCreator() {
                            public Message createMessage(Session session) throws JMSException {
                                return PublishHelper.createPublishMessage(publish, dbId, session);
                            }
                    }, delay > 0 ? delay + MSG_DELAY_INTERVAL : 0);
            }
        } catch (Exception e) {
            resetWaiting(publishList);
        }
    }

    public void publishDb(int dbId, List<PublishWrapper> publishList) {
        try {
            prepare(dbId, publishList);

            for (PublishWrapper publish: publishList) {
                JMSSender.send(connectionFactory, publishQueue, new JMSSender.MessageCreator() {
                    public Message createMessage(Session session) throws JMSException {
                        return PublishHelper.createPublishMessage(publish, dbId, session);
                    }
                }, HWFreq.getHWFreq(publish.getHWFrequency()).getProcessPriority(), 0);
            }
        } catch (Exception e) {
            resetWaiting(publishList);
        }
    }

    public void publishDbSync(int dbId, List<PublishWrapper> publishList) {
        prepare(dbId, publishList);
        QueuelessIssuePublisher publisher = new QueuelessIssuePublisher(rs, ps, flowLogger);
        publishList.forEach(f -> publisher.start(f, dbId));
    }

    public void publishEntireDb(String dbName, List<PublishWrapper> publishList) {
        try {
            prepare(null, publishList);

            for (PublishWrapper publish : publishList) {
                JMSSender.send(connectionFactory, entirePublishQueue, new JMSSender.MessageCreator() {
                    public Message createMessage(Session session) throws JMSException {
                        return PublishHelper.createPublishMessage(publish, dbName, session);
                    }
                }, HWFreq.getHWFreq(publish.getHWFrequency()).getProcessPriority(), 0);
            }
        } catch (Exception e) {
            resetWaiting(publishList);
        }
    }
    
    public void publishEntireDbSync(String dbName, List<PublishWrapper> publishList) {
        prepare(null, publishList);
        QueuelessEntirePublisher publisher = new QueuelessEntirePublisher(rs, ps, flowLogger);
        publishList.forEach(f -> publisher.start(f, dbName));
    }

    private void prepare(Integer dbId, Collection<PublishWrapper> publishList) {
        Date date = new Date();
        PublishWrapper lit = null;
        PublishWrapper pwHW = null;
        boolean skipHw = CochraneCMSPropertyNames.isPublishToSemanticoAfterLiteratum();
        Iterator<PublishWrapper> it = publishList.iterator();
        
        while (it.hasNext()) {
            PublishWrapper publish = it.next();
            String type = publish.getType();
            if (skipHw) {
                if (PubType.TYPE_LITERATUM.equals(type)) {
                    lit = publish;

                } else if (PubType.TYPE_SEMANTICO.equals(type)) {
                    pwHW = publish;
                    it.remove();
                }
            }
            if (!publish.isPublishEntityExist()) {
                continue;
            }
            prepare(publish, date);
        }
        if (pwHW != null)  {
            if (lit == null || !lit.setPublishToAwait(pwHW)) {
                publishList.add(pwHW);
            }
            if (dbId != null && pwHW.getPublishToAwait() == null && pwHW.getPath().getBaseType().isCentral()) {
                pwHW.setPublishToAwait(PublishWrapper.createIssuePublishWrapper(PubType.TYPE_DS,
                        CochraneCMSPropertyNames.getCentralDbName(), dbId, false, true, pwHW.getStartDate()), true);
            }
        }
    }

    private void prepare(PublishWrapper publish, Date date) {
        boolean send = publish.isSend();
        if (publish.isDelete() || publish.isGenerate() || (send && !publish.canGenerate())) {
            // it's a new generating
            ps.updatePublishForWaiting(publish.getId(), true);
            publish.resetPublishEntity();    // reset, but keep the package information

        } else if (send || publish.isUnpack()) {

            ps.updatePublishForWaiting(publish.getId(), true);
            rs.setStartSendingAndUnpackingDates(date, publish.getId(), send, publish.isUnpack());
            publish.setStartSendingAndUnpackingDates(date, send, publish.isUnpack());
        }
    }

    private void resetWaiting(Iterable<PublishWrapper> publishList) {
        publishList.forEach(f -> f.resetPublishForWaiting(ps));
    }

    private PublishWrapper createWhenReadyPublishWrapper(PublishJobs jobs, String type, Date date,
                                                         boolean incremental) {
        PublishWrapper pwr = incremental
            ? PublishWrapper.createIssuePublishWrapper(type, jobs.dbName, jobs.dbId, jobs.dfId, date, false, true)
            : PublishWrapper.createIssuePublishWrapper(type, jobs.dbName, jobs.dbId, jobs.dfId, date, true, false);
        if (pwr == null) {
            return null;
        }
        pwr.setGenerate(jobs.generate && pwr.getPath().getPubType().canGenerate());
        pwr.setSend(jobs.send);
        pwr.setUnpack(jobs.unpack);
        if (jobs.hwFrequency != null && pwr.getHWFrequency() == null) {
            pwr.setHWFrequency(jobs.hwFrequency);
        }
        return pwr;
    }
         
    private static final class PublishJobs {
        final String dbName;
        final int dbId;
        final int dfId;
        final boolean generate;
        final boolean send;
        final boolean unpack;
        final String hwFrequency;

        PublishJobs(String dbName, int dbId) {
            this(dbName, dbId, DbEntity.NOT_EXIST_ID, true, true, false, null);
        }

        PublishJobs(String dbName, int dbId, int dfId, String hwFrequency) {
            this(dbName, dbId, dfId, true, true, false, hwFrequency);
        }

        PublishJobs(String dbName, int dbId, boolean generate, boolean send, boolean unpack) {
            this(dbName, dbId, DbEntity.NOT_EXIST_ID, generate, send, unpack, null);
        }

        PublishJobs(String dbName, int dbId, int dfId, boolean generate, boolean send, boolean unpack, String hwFreq) {
            this.dbName = dbName;
            this.dbId = dbId;
            this.dfId = dfId;
            this.generate = generate;
            this.send = send;
            this.unpack = unpack;
            this.hwFrequency = hwFreq;
        }
    }

    private static final class LiteratumResponseReport {
        final String event;
        final String deliveryId;
        final PublishDestination dest;
        final String dbName;
        private StringBuilder report;
        private Date afterDate;
        private StringBuilder lastErrorMessage;
        private int counter;

        private LiteratumResponseReport(LiteratumSentConfirm response, PublishDestination dest, String dbName) {
            deliveryId = response.getDeliveryId();
            event = response.getEventType();
            this.dest = dest;
            this.dbName = dbName;
        }

        private boolean takeLiteratumDeliveryErrorMessage(Iterable<LiteratumSentConfirm.Messages> messages,
                                                          String[] errFilter) {
            lastErrorMessage = null;
            for (LiteratumSentConfirm.Messages message: messages) {
                if (!isErrorMessage(message, errFilter)) {
                    continue;
                }
                String msg = "\n\t" + message.getMessageAsString();
                lastErrorMessage = lastErrorMessage == null ? new StringBuilder(msg) : lastErrorMessage.append(msg);
            }
            return lastErrorMessage != null;
        }

        private static boolean isErrorMessage(LiteratumSentConfirm.Messages messages, String[] errFilter) {
            return messages != null && CochraneCMSPropertyNames.isAmongValues(messages.getMessageLevel(), errFilter);
        }

        private void appendLiteratumDeliveryErrorMessage(String doi) {
            if (!hasReport())  {
                report = createLiteratumDeliveryErrorMessage();
            }
            report.append(doi).append(" ").append(lastErrorMessage).append("\n");
            counter++;
            if (counter >= MessageBuilder.MAX_MESSAGES_LINES) {
                sendReport();
                counter = 0;
            }
        }

        private Date getAfterDate() {
            return afterDate == null ? Now.convertToDate(LocalDateTime.now().minusMonths(
                CochraneCMSPropertyNames.getAmountOfLastActualMonths())) : afterDate;
        }

        private StringBuilder createLiteratumDeliveryErrorMessage() {
            return deliveryId != null && !deliveryId.isEmpty() ? new StringBuilder(deliveryId).append(":\n")
                    : new StringBuilder();
        }

        private boolean hasReport() {
            return report != null;
        }

        private void sendReport() {
            if (hasReport()) {
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_ERROR, dbName,
                        report.toString(), CochraneCMSPropertyNames.isLiteratumEventPublishTestMode());
                report = null;
            }
        }
    }
}
