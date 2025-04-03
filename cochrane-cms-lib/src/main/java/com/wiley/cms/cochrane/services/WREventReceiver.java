package com.wiley.cms.cochrane.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.activitylog.FlowProduct;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.packagegenerator.IPackageGenerator;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieResponseBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;

import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;

import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;

import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.res.Property;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12.11.12
 */

@Local(IWREventReceiver.class)
@Singleton
@Startup
@DependsOn("CochraneCMSProperties")
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class WREventReceiver extends JMXHolder implements WREventReceiverMXBean, IWREventReceiver, IWREventVisitor {
    private static final Logger LOG = Logger.getLogger(WREventReceiver.class);

    @EJB
    private IPublishService pubs;

    @EJB(beanName = "PublishStorage")
    private IPublishStorage ps;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService ds;

    @EJB(beanName = "AddFromEntirePackageGenerator")
    private IPackageGenerator updatePropertiesPackageGenerator;

    private EventsState eventsState;
    private int batch4pubNotification = 1;

    //private String hwPackageCheckerTemplate = PublishHelper.buildHWPackageCheckerTemplate(
    //        CochraneCMSPropertyNames.getCDSRDbName());

    @PostConstruct
    public void start() {

        init();

        getEventsState().addEvent(new WREvent() {
            @Override
            int onReceiveEvent(IWREventVisitor receiver) {
                checkUnhandledEvents(getEventsState());
                return 0;
            }
        });
        registerInJMX();
    }

    @PreDestroy
    public void stop() {
        getEventsState().setFree();
        unregisterFromJMX();
    }

    public void update() {
        stop();
        start();
    }

    public void receiveEvent(WREvent event) {
        addEventToStart(event);
    }

    private void addEventToStart(WREvent event) {
        EventsState state = getEventsState();
        state.addEvent(event);
        state.start();
    }

    public int onEventReceive(PublishEvent event) {
        flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.WR, ILogEvent.GET_PUBLISH_NOTIFICATION, 0,
                event.recordName, event.dest.getShortStr(), event.toString());
        return ps.markForWhenReadyPublish(event.recordNumber, event.pubNumber, event.eventDate,
                event.dest.getWhenReadyTypeId(), flowLogger.getRecordCache().getLastDatabases().keySet());
    }

    public void receiveEvent(LiteratumEvent event) {
        if (registerEvent(event)) {
            addEventToStart(event);
        }
    }

    public boolean registerEvent(LiteratumEvent event) {
        String deliveryId = event.getDeliveryId();
        if (deliveryId != null && !deliveryId.isEmpty()
                ? ps.markForWhenReadyPublish(event.recordNumber, event.pubNumber, event.eventDate, deliveryId,
                    event.dest.getOnPubEventTypeIds(true), flowLogger.getRecordCache().getLastDatabases().keySet()) == 0
                : ps.markForWhenReadyPublish(event.recordNumber, event.pubNumber, event.eventDate,
                    event.dest.getWhenReadyTypeId(), flowLogger.getRecordCache().getLastDatabases().keySet()) == 0) {
            return false;
        }
        flowLogger.getActivityLog().info(ActivityLogEntity.EntityLevel.WR, ILogEvent.GET_PUBLISH_NOTIFICATION, 0,
                event.recordName, event.dest.getShortStr(), event.toString());
        return true;
    }

    public void handleEvents(List<? extends WREvent> events, boolean registered) {
        Date nowDate = new Date();
        PublishDestination dest = PublishProfile.getProfile().get().getDestination();
        Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> results = new HashMap<>();
        List<RecordMetadataEntity> updatedPubDates = new ArrayList<>();

        events.forEach(event -> {
                try {
                    handleEvent(event, dest, nowDate, registered, updatedPubDates, results);
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                }
            });
        for (Map.Entry<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> entry: results.entrySet()) {
            onHandleEvent(BaseType.getCDSR().get(), entry.getKey(), entry.getValue(), updatedPubDates);
        }
    }

    public void handleEvent(WREvent event) throws Exception {
        //String deliveryId = event.getDeliveryId();
        //if (event.getDest() == PublishDestination.SEMANTICO && hwPackageCheckerTemplate != null && deliveryId != null
        //        && deliveryId.length() > 0 && !deliveryId.matches(hwPackageCheckerTemplate)) {
        //    LOG.warn("%s event won't be handled as 'deliveryId'=%s doesn't match the pattern expected and not exists",
        //            event, deliveryId);
        //}
        String message = event.getDataForUiLog();
        flowLogger.getActivityLog().logInfoPublishEvent(ActivityLogEntity.EntityLevel.FLOW, ILogEvent.CONTENT_ONLINE,
                        0, event.getRecordName(), CochraneCMSPropertyNames.getSystemUser(), message);

        boolean wr = BaseType.isWRSupported(event.getBaseType());
        if ((wr && !event.isFirstPublishedOnline()
                && !flowLogger.getRecordCache().isRecordExists(event.getRecordName()))
            || (!wr && (!event.getBaseType().isActualPublicationDateSupported()
                    || event.getDest() != PublishDestination.SEMANTICO))) {
            LOG.debug(String.format("%s event won't be handled as such a WR record isn't being expected now", event));
            return;
        }

        Date nowDate = new Date();
        PublishDestination dest = PublishProfile.getProfile().get().getDestination();
        Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> results = new HashMap<>();
        List<RecordMetadataEntity> updatedPubDates = new ArrayList<>();

        handleEvent(event, dest, nowDate, false, updatedPubDates, results);

        for (Map.Entry<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> entry : results.entrySet()) {
            onHandleEvent(event.getBaseType(), entry.getKey(), entry.getValue(), updatedPubDates);
        }
    }

    public int onEventReceive(LiteratumEvent event) {
        return 1;
    }

    public void onPublish(int publishId) {

        EventsState state = getEventsState();
        state.addEvent(new WREvent() {
            @Override
            int onReceiveEvent(IWREventVisitor receiver) {
                return ps.markForWhenReadyPublish(publishId);
            }

            @Override
            public String toString() {
                return "publish package: "  + publishId;
            }
        });

        state.start();
    }

    public void printState() {
        LOG.info(String.format("batch4pubNotification=%d %s", batch4pubNotification, eventsState));
    }

    private EventsState getEventsState() {
        return eventsState;
    }

    private void receiveEvents(EventsState eventsState) {

        int eventCount = 0;
        int recordCount = 0;

        LOG.info("WR event queue is started...");
        int batch4eventHandling = Property.get("cms.cochrane.abstracts.publish.events", "50").get().asInteger();
        initProperties();

        List<WREvent> events = new ArrayList<>();
        do  {
            WREvent event = eventsState.getEvent();
            if (event == null) {
                break;
            }

            eventCount++;
            int count = event.onReceiveEvent(this);
            if (count > 0) {
                recordCount += count;
                events.add(event);
            }
        } while(--batch4eventHandling > 0);

        LOG.info(String.format("WR event queue: new events=%d, checked events=%d, found unpublished candidates=%d",
            eventCount, events.size(), recordCount));

        if (recordCount > 0) {
            handleEvents(events, false);
        }

        LOG.info("WR event queue is finished");
        eventsState.finish();
    }

    private void initProperties() {
        batch4pubNotification = Property.get("cms.cochrane.abstracts.publish.batch", "1").get().asInteger();
    }

    private void onHandleEvent(BaseType baseType, Integer dbId, Pair<Map<String, Integer>, Map<String, Boolean>> result,
                               List<RecordMetadataEntity> updatedPubDates) {
        String dbName = baseType.getId();
        if (!result.second.isEmpty()) {
            LOG.debug(String.format("Record for HW publication: %d, dbId=%d", result.second.size(), dbId));
            sendToHW(dbName, dbId, result);
        }
        if (result.first.isEmpty()) {
            return;
        }

        reloadToDatesUpdate(baseType, dbId, result.first.keySet(), updatedPubDates);

        int count = result.first.isEmpty() ? 0 : rm.setRecordState(RecordEntity.AWAITING_PUB_EVENTS_STATES,
                RecordEntity.STATE_WR_PUBLISHED, dbId, result.first.keySet());
        LOG.debug(String.format("Records for final DS publication: %d, dbId=%d", count, dbId));
        if (count > 0) {
            result.first.forEach((cdNumber, dfId) -> pubs.publishWhenReadyDS(dbName, dbId, dfId, cdNumber));
        }
        flowLogger.onFlowCompleted(result.first.keySet());
    }

    private void sendToHW(String dbName, Integer dbId, Pair<Map<String, Integer>, Map<String, Boolean>> result) {
        Set<String> highPriorityNames = null;
        for (Map.Entry<String, Boolean> entry: result.second.entrySet()) {
            if (entry.getValue()) {
                if (highPriorityNames == null) {
                    highPriorityNames =  new HashSet<>();
                }
                highPriorityNames.add(entry.getKey());
            }
        }
        if (highPriorityNames != null) {
            for (String name: highPriorityNames) {
                result.second.remove(name);
            }
            pubs.publishWhenReadyHW(dbName, dbId, highPriorityNames, HWFreq.HIGH.getValue());
        }
        if (!result.second.isEmpty()) {
            pubs.publishWhenReadyHW(dbName, dbId, new HashSet<>(result.second.keySet()), null);
        }
    }

    private void reloadToDatesUpdate(BaseType bt, Integer dbId, Set<String> cdNumbers,
                                     List<RecordMetadataEntity> updatedDates) {
        List<String> updatedMl3g = null;
        for (RecordMetadataEntity meta: updatedDates) {
            String cdNumber = meta.getName();
            if (cdNumbers.remove(cdNumber)) {
                rm.setRecordState(RecordEntity.AWAITING_PUB_EVENTS_STATES, RecordEntity.STATE_WR_PUBLISHED, dbId,
                        Collections.singletonList(cdNumber));
                if (bt.canPdfFopConvert() && (meta.notEqualIssuesVsCT()
                        || PublishHelper.notEqualDatesOrNull(meta.getReceived(), meta.getPublishedDate()))) {
                    reloadToDatesUpdatePDF(cdNumber, meta.getPublishedIssue(), bt.getId());
                } else {
                    updatedMl3g = addToDatesUpdate(cdNumber, updatedMl3g);
                }
            }
        }
        reloadToDatesUpdate(dbId, updatedMl3g, PackageChecker.PROPERTY_UPDATE_WML3G_SUFFIX);
    }

    private void reloadToDatesUpdatePDF(String cdNumber, int fullIssueNumber, String dbName) {
        try {
            LOG.debug(String.format("publication dates of %s are updating with %s for issue %d",
                    cdNumber, PackageChecker.PROPERTY_UPDATE_PDF_SUFFIX, fullIssueNumber));
            updatePropertiesPackageGenerator.generateAndUpload(Collections.singletonList(cdNumber),
                dbName, fullIssueNumber, PackageChecker.PROPERTY_UPDATE_PDF_SUFFIX);
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void reloadToDatesUpdate(Integer dbId, List<String> cdNumbers, String suffix) {
        if (cdNumbers != null) {
            try {
                LOG.debug(String.format("%d record(s) for publication dates are updating with %s, dbId=%d",
                        cdNumbers.size(), suffix, dbId));
                updatePropertiesPackageGenerator.generateAndUpload(cdNumbers, dbId, suffix);
            } catch (Exception e) {
                LOG.error(e);
            }
        }
    }

    private List<String> addToDatesUpdate(String cdNumber, List<String> list) {
        List<String> ret = list == null ? new ArrayList<>() : list;
        ret.add(cdNumber);
        return ret;
    }

    private void handleEvent(WREvent event, PublishDestination dest, Date nowDate, boolean registered,
        Collection<RecordMetadataEntity> updatedPubDates,
        Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> ret) throws Exception {
        LOG.info(String.format("publication event %s is handling... ", event));

        Map<Integer, List<PublishedAbstractEntity>> pubRecords = new HashMap<>();
        String cdNumber = event.getRecordName();
        String publisherId = RevmanMetadataHelper.buildPubName(cdNumber, event.getPubNumber());
        Exception exceptionToRepeat = null;
        IRecordCache cache = flowLogger.getRecordCache();
        boolean offline = event.isContentOffline();
        RecordEntity re;
        try {
            String otherPublisherId = cache.activateRecord(event.getRecordName(), publisherId, true);
            if (otherPublisherId != null) {
                throw new CmsException(new ErrorInfo<>(publisherId, ErrorInfo.Type.RECORD_BLOCKED, String.format(
                    "the event will be completed later because %s is now being processed or awaiting for publication",
                        otherPublisherId)));
            }
            re = ps.handleWhenReadyEvent(event, dest, nowDate, cache.getLastDatabases().keySet(),
                    registered, pubRecords);
            if (re != null && !offline) {
                if (re.getMetadata().isScheduled()) {
                    ds.loadScheduledContent(re);
                }
                if ((re.getMetadata().getVersion().isVersionLatest() && !re.getMetadata().isScheduled())
                        || re.getMetadata().isSPDChanged()) {
                    updatedPubDates.add(re.getMetadata());
                }
            }
        } catch (CmsException | CmsJTException ce) {
            re = null;
            pubRecords.clear();
            if (ce.getErrorInfo() == null || !ce.getErrorInfo().isRecordBlockedError()) {
                flowLogger.onProductError(ILogEvent.PRODUCT_ERROR, null, cdNumber, event.getPubNumber(),
                        ce.getMessage(), event.getBaseType().hasSFLogging());
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_ERROR,
                        event.getBaseType().getId(), PublishHelper.buildPublicationEventErrorMessage(
                            publisherId, ce.getMessage(), event.getRawData()), event.getRecordName());
            } else {
                exceptionToRepeat = ce;
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_WARN,
                        event.getBaseType().getId(), PublishHelper.buildPublicationEventWarningMessage(
                             publisherId, ce.getMessage(), event.getRawData()));
            }
        }

        String onlineDate = event.getPublishDate(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM).get();
        String firstOnlineDate = getFirstOnlineDate(event.getBaseType(), re, event);
        if (pubRecords.isEmpty()) {
            if (exceptionToRepeat != null) {
                throw exceptionToRepeat;
            }
            if (re != null) {
                Pair<Map<String, Integer>, Map<String, Boolean>> names = ret.computeIfAbsent(re.getDb().getId(),
                        f -> new Pair<>(new HashMap<>(), new HashMap<>()));
                names.first.put(re.getName(), re.getDeliveryFileId());
                flowLogger.onProductPublished(cdNumber, event.getDest(), event.getEventDate(), firstOnlineDate,
                        onlineDate, event.getBaseType().hasSFLogging(), offline);
            } else {
                LOG.info(String.format("publication event %s is handled with no results", event));
            }
            cache.activateRecord(event.getRecordName(), publisherId, false);
            return;
        }

        flowLogger.onProductPublished(cdNumber, event.getDest(), event.getEventDate(), firstOnlineDate, onlineDate,
                event.getBaseType().hasSFLogging(), offline);
        prepareNotification(event.getBaseType(), pubRecords, dest, nowDate, onlineDate, ret);

        if (offline && re != null) {
            ret.clear();
            removeCanceledSPD(event.getBaseType(), re);
            flowLogger.onFlowCompleted(cdNumber, true);
        }
    }

    private String getFirstOnlineDate(BaseType bt, RecordEntity re, WREvent event) {
        boolean hw = PublishDestination.SEMANTICO == event.getDest();
        String eventFirstOnlineDate = hw ? event.getPublishDate(LiteratumEvent.WRK_EVENT_FIRST_ONLINE).get() : null;

        if (!hw || eventFirstOnlineDate != null) {
            return eventFirstOnlineDate;
        }
        if ((bt.isEditorial() || bt.isCCA())) {
            // to support "productFirstOnlineDate" property of "eventType": "Product" for  EDI and CCA  updated
            RecordMetadataEntity rme =
                    ResultStorageFactory.getFactory().getInstance().findRecordMetadataForEDIAndCCAFirstOnline(
                            bt, event.getRecordName());
            if (rme != null) {
                eventFirstOnlineDate = rme.getFirstOnline();
            }
        }
        return eventFirstOnlineDate;
    }

    private void prepareNotification(BaseType bt, Map<Integer, List<PublishedAbstractEntity>> pubRecords,
                                     PublishDestination dest, Date now, String onlineDate,
                                     Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> ret) {
        int[] all = {0};
        pubRecords.forEach((notified, list) -> {
                if (dest.hasDelayedPubType(notified)) {
                    for (PublishedAbstractEntity pae: list) {

                        Pair<Map<String, Integer>, Map<String, Boolean>> names = ret.computeIfAbsent(pae.getDbId(),
                            f -> new Pair<>(new HashMap<>(), new HashMap<>()));
                        Boolean highPriority = names.second.get(pae.getRecordName());
                        if (highPriority == null || !highPriority) {
                            names.second.put(pae.getRecordName(), pae.isHighFrequency());
                        }
                    }
                } else {
                    if (CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled()) {
                        all[0] += prepareSFTPNotification(bt, list, now, onlineDate, all[0], notified, ret);
                    } else {
                        all[0] += prepareSOAPNotification(bt, list, now, onlineDate, all[0], notified, ret);
                    }
                }
            });
    }

    private void removeCanceledSPD(BaseType baseType, RecordEntity re) {
        Integer issueId = re.getDb().getIssue().getId();
        if (CmsUtils.isScheduledIssue(issueId)) {
            try {
                RecordHelper.removeRecordFolders(baseType, issueId, re);
                ResultStorageFactory.getFactory().getInstance().removeRecord(baseType, re.getId(), true, true);
                DbStorageFactory.getFactory().getInstance().updateRecordCount(re.getDb().getId());
                flowLogger.onProductDeleted(re.getDeliveryFile().getName(), re.getDeliveryFile().getId(),
                        re.getName(), baseType.hasSFLogging(), true);

            } catch (Throwable tr) {
                LOG.error(tr.getMessage());
            }
        }
    }

    private int prepareSOAPNotification(BaseType bt, Collection<PublishedAbstractEntity> recs, Date nowDate,
                                    String eventDate, int allCount, int notified,
                                    Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> ret) {
        ArchieResponseBuilder rb = bt.isCDSR()
                ? ArchieResponseBuilder.createOnPublished(nowDate, String.valueOf(allCount)) : null;
        int count = 0;
        Map<Integer, Integer> ids = new HashMap<>();

        for (PublishedAbstractEntity pe: recs) {

            String name = pe.getRecordName();
            boolean tr = pe.hasLanguage();
            if (rb != null) {
                rb.setForTranslations(tr);
                rb.sPD(pe.sPD().is(), pe.sPD().off());
                rb.addContent(rb.asSuccessfulElement(tr ? new TranslatedAbstractVO(pe)
                        : new ArchieEntry(pe), eventDate));
                flowLogger.onDashboardEventStart(KibanaUtil.Event.NOTIFY_PUBLISHED,
                        pe.sPD().off() ? FlowProduct.State.DELETED : FlowProduct.State.PUBLISHED,
                        rb.getProcess(), null, name, pe.getLanguage(), pe.sPD().is());
            }
            count++;
            ids.put(pe.getId(), tr ? null : flowLogger.getRecordCache().checkAriesRecordOnPublished(
                    pe.getManuscriptNumber(), name, pe.getId(), pe.sPD().off() ? pe.getDeliveryId()
                            : pe.getAcknowledgementId()).ackId());
            if (count % batch4pubNotification == 0) {
                sendSOAPNotification(bt, rb, ids, notified);
                rb = bt.isCDSR()
                        ? ArchieResponseBuilder.createOnPublished(nowDate, String.valueOf(allCount + count)) : null;
            }
            Integer dbId  = pe.getDbId();
            Pair<Map<String, Integer>, Map<String, Boolean>> names = ret.computeIfAbsent(
                    dbId, f -> new Pair<>(new HashMap<>(), new HashMap<>()));
            names.first.put(name, pe.getDeliveryId());
        }

        if (!ids.isEmpty()) {
            sendSOAPNotification(bt, rb, ids, notified);
        }
        return allCount + count;
    }

    private void sendSOAPNotification(BaseType bt, ArchieResponseBuilder reb, Map<Integer, Integer> ids, int notified) {
        List<PublishedAbstractEntity> list;
        if (reb == null || RevmanPackage.notifyPublished(reb, flowLogger)) {
            list = ps.updateWhenReadyOnPublished(ids, notified, true);

        } else {
            LOG.warn(String.format("%d records weren't notified about publishing", ids.size()));
            list = ps.updateWhenReadyOnPublished(ids, notified, false);
            if (!list.isEmpty()) {
                String errorMsg = reb.getErrorMessage();
                list.forEach(pae -> flowLogger.onProductError(ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED, null,
                        pae.getRecordName(), pae.getLanguage(), errorMsg != null ? errorMsg
                                : "a call Archie 'on published' failed", false, true, reb.sPD().is()));
            }
        }
        list.forEach(pae -> pubs.sendAcknowledgementAriesOnPublish(bt.getId(), pae));
        ids.clear();
    }

    private int prepareSFTPNotification(BaseType bt, Collection<PublishedAbstractEntity> recs, Date nowDate,
                                    String eventDate, int allCount, int notified,
                                    Map<Integer, Pair<Map<String, Integer>, Map<String, Boolean>>> ret) {
        ArchieResponseBuilder rb = bt.isCDSR()
                ? ArchieResponseBuilder.createOnPublished(nowDate, String.valueOf(allCount)) : null;
        int count = 0;
        Map<Integer, Integer> ids = new HashMap<>();
        boolean success;

        for (PublishedAbstractEntity pe: recs) {
            success = true;

            String name = pe.getRecordName();
            boolean tr = pe.hasLanguage();
            if (rb != null) {
                rb.setForTranslations(tr);
                rb.sPD(pe.sPD().is(), pe.sPD().off());
                rb.addContent(rb.asSuccessfulElement(tr ? new TranslatedAbstractVO(pe)
                        : new ArchieEntry(pe), eventDate));
                flowLogger.onDashboardEventStart(KibanaUtil.Event.NOTIFY_PUBLISHED,
                    pe.sPD().off() ? FlowProduct.State.DELETED : FlowProduct.State.PUBLISHED,
                        rb.getProcess(), null, name, pe.getLanguage(), pe.sPD().is());
            }
            count++;
            ids.put(pe.getId(), tr ? null : flowLogger.getRecordCache().checkAriesRecordOnPublished(
                    pe.getManuscriptNumber(), name, pe.getId(), pe.sPD().off() ? pe.getDeliveryId()
                            : pe.getAcknowledgementId()).ackId());
            if (count % batch4pubNotification == 0) {
                success = sendSFTPNotification(bt, rb, ids, notified);
                rb = bt.isCDSR()
                        ? ArchieResponseBuilder.createOnPublished(nowDate, String.valueOf(allCount + count)) : null;
            }
            if (success) {
                Integer dbId = pe.getDbId();
                Pair<Map<String, Integer>, Map<String, Boolean>> names = ret.computeIfAbsent(
                        dbId, f -> new Pair<>(new HashMap<>(), new HashMap<>()));
                names.first.put(name, pe.getDeliveryId());
            }
        }

        if (!ids.isEmpty()) {
            success = sendSFTPNotification(bt, rb, ids, notified);
            if (!success) {
                ret.clear();
            }
        }
        return allCount + count;
    }

    private boolean sendSFTPNotification(BaseType bt, ArchieResponseBuilder reb,
                                         Map<Integer, Integer> ids, int notified) {
        List<PublishedAbstractEntity> list;
        if (reb == null) {
            list = ps.updateWhenReadyOnPublished(ids, notified, true);
            list.forEach(pae -> pubs.sendAcknowledgementAriesOnPublish(bt.getId(), pae));
        } else {
            List<PublishedAbstractEntity> entities = getPublishedAbstractEntitiesFromReb(reb);

            for (PublishedAbstractEntity pae : entities) {
                ClDbVO dbVo = getClDbVO(pae.getDbId());
                String filePath = RevmanPackage.createNotificationFile(dbVo, pae, reb);

                String fileName = StringUtils.isNotBlank(filePath) ? CmsUtils.getPackageNameFromPath(filePath) : null;

                boolean success = false;
                if (StringUtils.isNotBlank(fileName)) {
                    ps.addDeliveryNotificationFileName(pae.getId(), fileName);
                    success = RevmanPackage.sendToCochraneBySftp(dbVo, filePath, pae, reb);
                }

                if (success) {
                    pae = ps.updateWhenReadyOnPublished(pae.getId(), ids.get(pae.getId()), notified, true);
                    pubs.sendAcknowledgementAriesOnPublish(bt.getId(), pae);
                    flowLogger.onDashboardEventEnd(reb.getProcess(), reb.getPackageId(), true, reb.sPD().is());
                } else {
                    pae = ps.updateWhenReadyOnPublished(pae.getId(), ids.get(pae.getId()), notified, false);
                    flowLogger.onProductError(ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED, null,
                            pae.getRecordName(), pae.getLanguage(), reb.getErrorMessage(),
                            false, true, reb.sPD().is());
                    return false;
                }
            }
            reb.commitWhenReady(CochraneCMSBeans.getRecordManager());
        }
        ids.clear();
        return true;
    }

    private List<PublishedAbstractEntity> getPublishedAbstractEntitiesFromReb(ArchieResponseBuilder reb) {
        List<ArchieEntry> entitiesForNotification = reb.getWrMap().get(true);
        List<Integer> ids = entitiesForNotification.stream()
                .map(ArchieEntry::getId)
                .collect(Collectors.toList());

        PublishStorage publishStorage = (PublishStorage) CochraneCMSBeans.getPublishStorage();
        return PublishedAbstractEntity.queryAbstractsByIds(ids, publishStorage.getManager()).getResultList();
    }

    private ClDbVO getClDbVO(Integer dbId) {
        IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
        return new ClDbVO(rs.getDb(dbId));
    }

    private void checkUnhandledEvents(EventsState eventsState) {
        List<PublishRecordEntity> list = ps.findWhenReadyMarkedForPublish(
                flowLogger.getRecordCache().getLastDatabases().keySet());
        if (list.isEmpty()) {
            return;
        }
        LOG.info(String.format("%d ready to publish records found", list.size()));
        for (PublishRecordEntity pre: list) {
            int type = pre.getPublishPacket().getPublishType();
            PublishDestination dest = PublishDestination.WOLLIT.getOnPubEventTypeIds(true).contains(type)
                ? PublishDestination.WOLLIT : (PublishDestination.SEMANTICO.getOnPubEventTypeIds(true).contains(type)
                ? PublishDestination.SEMANTICO : null);
            Date eventDate = pre.getDate();
            if (dest != null)  {
                BaseType bt = BaseType.find(pre.getPublishPacket().getDb().getDatabase().getName()).get();
                LiteratumEvent event = new LiteratumEvent(dest, bt,
                    RecordHelper.buildCdNumber(pre.getNumber()), pre.getPubNumber(),
                        "internal_reload_on_restart", eventDate, pre.getPublishPacket().getFileName());
                eventsState.addEvent(event);
            }
        }
    }

    private synchronized void init() {
        if (eventsState == null) {
            eventsState = new EventsState();
        }
        initProperties();
    }

    private static class EventsState {

        private boolean started;

        private final ConcurrentLinkedQueue<WREvent> deferredEvents = new ConcurrentLinkedQueue<>();

        synchronized boolean isFree() {
            return !started;
        }

        synchronized void setFree() {
            started = false;
        }

        void addEvent(WREvent event) {
            deferredEvents.offer(event);
        }

        WREvent getEvent() {
            return deferredEvents.poll();
        }

        synchronized void finish() {
            started = false;

            if (!deferredEvents.isEmpty()) {
                start();
            }
        }

        synchronized void start() {
            if (isFree()) {
                started = true;
            }
        }

        @Override
        public String toString() {
            return "started=" + started + " events=" + deferredEvents.size();
        }
    }
}
