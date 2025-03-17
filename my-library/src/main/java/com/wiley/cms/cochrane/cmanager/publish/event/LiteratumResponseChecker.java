package com.wiley.cms.cochrane.cmanager.publish.event;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.process.task.BaseDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.CollectionCommitter;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

import org.quartz.StatefulJob;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 06.07.2017
 */
@Singleton
@Local({LiteratumResponseCheckerControler.class, IDownloader.class})
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LiteratumResponseChecker extends BaseDownloader implements StatefulJob, LiteratumResponseCheckerControler {
    public static final String MSG_LTP_ERROR = "Failed to load a publishing entry for testing purposes";
    public static final String MSG_LTP_FULL_ERROR = "Failed to load a full publishing package for testing purposes";
    public static final String MSG_OFFLINE_FULL_ERROR = "Failed to offline a full withdrawn list for testing purposes";

    public static final Res<Property> MAX_DOI_COUNT_PER_EVENT = Property.get("cms.cochrane.publish.json_batch", "5000");

    private static final Logger LOG = Logger.getLogger(LiteratumResponseChecker.class);


    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private QueueConnectionFactory localConnectFactory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/accept_publish")
    private Queue acceptPublishQueue;
    
    private final Responder responder = Responder.instance();

    @PostConstruct
    public void start() {
        responder.responseHandler.initContext(localConnectFactory, acceptPublishQueue);
        startDownloader(
                getClass().getSimpleName(),
                CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.checker.schedule", "0 0/2 * * * ?"),
                true,
                false);
        responder.queueConnectionData = null;
    }

    @Override
    protected boolean canDownload() {
        return CochraneCMSPropertyNames.isLiteratumIntegrationEnabled();
    }

    @Override
    public void update() {
        start();
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    @Override
    protected void download() {
        LOG.trace("Check literatum events");
        checkResponses();
    }

    public String checkResponses() {
        int consumedEvents = consumeEvents();
        LOG.debug("Consumed events: " + consumedEvents);
        return "events amount: " + consumedEvents;
    }

    private int consumeEvents() {
        try {
            ConnectionFactory connectionFactory = responder.getQueueConnectionData().getConnectionFactory();
            Queue responseQueue = responder.getQueueConnectionData().getQueue();
            String userName = responder.getQueueConnectionData().getUserName();
            String password = responder.getQueueConnectionData().getPassword();
            JMSSender.consume(connectionFactory, responseQueue, responder.responseHandler, userName, password);

        } catch (CmsException ce) {
            LOG.warn(String.format("Failed to consume literatum events %s", ce.getMessage()));

        } catch (Exception e) {
            LOG.error("Failed to consume literatum events", e);
        }
        return responder.responseHandler.getAcceptedMessagesCountAndReset();
    }

    /**
     *  An instance for Literatum responder
     */
    public static final class Responder {
        private static final int SEND_RESPONSE_DELAY = Now.calculateMillisInMinute();

        private static final Responder INSTANCE = new Responder();

        private JmsQueueConnectionData queueConnectionData;
        private final LiteratumResponseHandler responseHandler;

        private Responder() {
            responseHandler = new LiteratumResponseHandler();
        }

        public static Responder instance() {
            return INSTANCE;
        }

        public void imitateCDSRAndEditorialResponseForHW(String fileName, Map<String, Boolean[]> pubOptions) {
            boolean withErr = CochraneCMSPropertyNames.getHWLiteratumEventsImitateError() > 0;
            try {
                for (Map.Entry<String, Boolean[]> entry : pubOptions.entrySet()) {
                    imitateHWResponse(fileName, RecordHelper::buildDoiCDSRAndEditorial, entry.getKey(), !withErr,
                            withErr, entry.getValue());
                }
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }

        /**
         * Imitate HW LTP & CO events specifically for CENTRAL.
         * @param fileName         the deliveryId
         * @param cdNumbers        list of CENTRAL names related to the deliveryId, it is used for errors emulation.
         *                         If NULL, an event for full failure is generated, or else an event with partial
         *                         success is generated. The specific names (except the first one in the list) are used
         *                         only for LTP event with partial success to emulate the errors for the worst case.
         *                         (the first name is always considered as successful).
         * @param deletedCdNumbers If not NULL, CONTENT_OFFLINE is emulated and the online flag is ignored.
         * @param online           The online flag. If TRUE CO event is emulated; if FALSE LTP event is emulated.
         */
        public void imitateCentralResponseForHW(String fileName, Collection<String> cdNumbers,
                                                Collection<String> deletedCdNumbers, boolean online) {
            boolean err = CochraneCMSPropertyNames.getHWLiteratumEventsImitateError() > 0;
            int maxCount = MAX_DOI_COUNT_PER_EVENT.get().asInteger();
            boolean toWithdraw = deletedCdNumbers != null;
            if (!err && cdNumbers != null && cdNumbers.size() >= maxCount) {
                // do not use event batching if no errors
                maxCount = cdNumbers.size() + 1;
            }
            if ((!online && err) || (toWithdraw && deletedCdNumbers.size() <= maxCount) || (!toWithdraw
                    && (cdNumbers == null || cdNumbers.size() <= maxCount))) {
                imitateCentralResponseForHW(fileName, RecordHelper::buildDoiCentral, cdNumbers, deletedCdNumbers,
                        online, err);
                return;
            }
            CollectionCommitter<String> committer = new CollectionCommitter<String>(maxCount, new ArrayList<>()) {
                @Override
                public void commit(Collection<String> list) {
                    if (toWithdraw) {
                        imitateCentralResponseForHW(fileName, RecordHelper::buildDoiCentral, null, list, online, err);
                    } else {
                        imitateCentralResponseForHW(fileName, RecordHelper::buildDoiCentral, list, null, online, err);
                    }
                }
            };
            if (toWithdraw) {
                deletedCdNumbers.forEach(committer::commit);
            } else {
                cdNumbers.forEach(committer::commit);
            }
            committer.commitLast();
        }

        private void imitateCentralResponseForHW(String fileName, Function<String, String> buildDoi,
                Collection<String> cdNumbers, Collection<String> deletedDois, boolean online, boolean withErr) {
            try {
                if (deletedDois != null) {
                    imitateHWResponseForCentralOffline(fileName, buildDoi, deletedDois, withErr);
                } else {
                    imitateHWResponseForCentral(fileName, buildDoi, cdNumbers, online, withErr);
                }
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }

        public void imitateCcaResponseForHW(String fileName, Map<String, Boolean[]> pubOptions) {
            boolean withErr = CochraneCMSPropertyNames.getHWLiteratumEventsImitateError() > 0;
            try {
                for (Map.Entry<String, Boolean[]> entry : pubOptions.entrySet()) {
                    imitateHWResponse(fileName, RecordHelper::buildDoiCCA, entry.getKey(), !withErr,
                            withErr, entry.getValue());
                }
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }

        private void imitateHWResponseForCentral(String fileName, Function<String, String> buildDoi,
            Collection<String> cdNumbers, boolean online, boolean withErr) throws Exception {

            LOG.debug(String.format("HW event for %s (%d) is emulating...", fileName,
                    cdNumbers == null ? 0 : cdNumbers.size()));
            LiteratumSentConfirm response = createHWResponse(fileName, Constants.DOI_CENTRAL, online, false, false);
            if (!online && withErr) {                          // LTP with errors
                if (cdNumbers == null || cdNumbers.size() <= 1) { // LTP full failure
                    response.setMessages(createErrorMessages(MSG_LTP_FULL_ERROR));
                } else {                                          // make LTP with partial success
                    List<LiteratumSentConfirm.HasPart> parts = new ArrayList<>(cdNumbers.size() - 1);
                    cdNumbers.stream().skip(1).forEach(f -> addErrorPart(buildDoi.apply(f), parts));
                    response.setHasPart(parts);
                }
            } else if (withErr) {                                // CO with errors
                if (cdNumbers != null) {                         // CO partial success
                    response.setFullGroup(false);

                } else {                                         // CO full failure
                    LOG.debug(String.format("no CO HW event for %s will be emulated for full failure.", fileName));
                    return;
                }
            } else {                                             // CO full success
                response.setFullGroup(true);
            }
            response.setRawData(response.toString());
            sendResponse(response);
        }

        private void imitateHWResponseForCentralOffline(String fileName, Function<String, String> buildDoi,
                Collection<String> deletedDois, boolean withErr) throws Exception {

            LOG.debug(String.format("HW event for %s with %d withdrawn is emulating...", fileName, deletedDois.size()));

            LiteratumSentConfirm response = createHWResponse(null, Constants.DOI_CENTRAL, false, true, false);

            Collection<LiteratumSentConfirm.WorkflowEventGroup> eventGroups = new ArrayList<>();
            addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, response.getEndedAtTime(), eventGroups);

            List<LiteratumSentConfirm.HasPart> parts = new ArrayList<>(deletedDois.size());
            //if (imitateErr) {
            //    deletedDois.forEach(f -> addErrorPart(buildDoi.apply(f), parts));
            //else  {
            deletedDois.forEach(f -> addPart(buildDoi.apply(f), parts));
            //}
            response.setHasPart(parts);
            response.setFullGroup(true);
            response.setRawData(response.toString());
            sendResponse(response);
        }

        private void imitateHWResponse(String fileName, Function<String, String> buildDoi, String pubName,
                                       boolean online, boolean withErr, Boolean[] options) throws Exception {
            boolean spd = options != null && options[1] != null;
            boolean offline = spd && options[1];
            if (spd && CochraneCMSPropertyNames.getLiteratumPublishTestModeForSPD() == 0 && !offline) {
                return;
            }
            LOG.debug(String.format("HW event for %s %s (spd=%s, offline=%s) is emulating ...", fileName, pubName, spd,
                    offline));
            LiteratumSentConfirm response = createHWResponse(fileName,
                    buildDoi.apply(pubName), online, offline, options == null || options[0]);
            if (!online && withErr) {
                response.setMessages(createErrorMessages("a fatal error for testing purposes"));
            }
            response.setRawData(response.toString());
            sendResponse(response);
        }

        private static LiteratumSentConfirm createHWResponse(String fileName, String doi, boolean online,
                                                             boolean offline, boolean newDoi) {
            LiteratumSentConfirm response = new LiteratumSentConfirm();
            Date dt = new Date();
            response.setEventId("testHW_EventId" + dt.getTime());
            //response.setTransactionId("testHW_TransactionId" + dt.getTime());
            response.setSourceSystem(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterSemantico());

            response.setEventType(online ? CochraneCMSPropertyNames.getLiteratumEventOnlineFilter()
                : (offline ? CochraneCMSPropertyNames.getLiteratumEventOfflineFilter()
                    : CochraneCMSPropertyNames.getLiteratumEventOnLoadFilter()));

            response.setDoi(doi);

            String offsetDate = OffsetDateTime.now().plus(1, ChronoUnit.SECONDS).toString();
            response.setEndedAtTime(offsetDate);
            if (fileName != null) {
                response.setDeliveryId(fileName);
            }
            if (!offline && online) {
                List<LiteratumSentConfirm.WorkflowEventGroup> eventGroups = new ArrayList<>();
                addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, offsetDate, eventGroups);
                if (newDoi) {

                    addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, offsetDate, eventGroups);
                    addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_FIRST_ONLINE, offsetDate, eventGroups);
                    response.setFirstPublishedOnline(true);
                }
                response.setWorkflowEventGroup(eventGroups);
            }
            return response;
        }

        private static LiteratumSentConfirm.HasPart addErrorPart(String doi,
                                                                 Collection<LiteratumSentConfirm.HasPart> parts) {
            LiteratumSentConfirm.HasPart hasPart = addPart(doi, parts);
            hasPart.setMessages(createErrorMessages(MSG_LTP_ERROR));
            parts.add(hasPart);
            return hasPart;
        }

        private static LiteratumSentConfirm.HasPart addPart(String doi,
                                                            Collection<LiteratumSentConfirm.HasPart> parts) {
            LiteratumSentConfirm.HasPart hasPart = new LiteratumSentConfirm.HasPart();
            hasPart.setDoi(doi);
            parts.add(hasPart);
            return hasPart;
        }

        private static List<LiteratumSentConfirm.Messages> createErrorMessages(String msgText) {
            LiteratumSentConfirm.Messages msg = new LiteratumSentConfirm.Messages();
            msg.setMessageLevel("fatal");
            msg.setMessageText(msgText);
            return Collections.singletonList(msg);
        }

        private static void addWorkflowEventGroup(String workflowEventType, String offsetDate,
                                                  Collection<LiteratumSentConfirm.WorkflowEventGroup> eventGroups) {
            eventGroups.add(new LiteratumSentConfirm.WorkflowEventGroup(workflowEventType, offsetDate));
        }

        public void imitateCDSRResponseForWOL(String fileName, Collection<String> pubNames) {
            boolean withErr = CochraneCMSPropertyNames.getWOLLiteratumEventsImitateError() > 0;
            String doiPrefix = PublishHelper.defineLiteratumDoiPrefixByDatabase(
                    CochraneCMSPropertyNames.getCDSRDbName());
            imitateResponseForWOL(fileName, doiPrefix, RecordHelper::buildDoiCDSRAndEditorial, pubNames, !withErr,
                                  withErr, CochraneCMSPropertyNames.getWollitEventBatch());
        }

        public void imitateEditorialResponseForWOL(String fileName, Collection<String> pubNames) {
            boolean withErr = CochraneCMSPropertyNames.getWOLLiteratumEventsImitateError() > 0;
            String doiPrefix = PublishHelper.defineLiteratumDoiPrefixByDatabase(
                    CochraneCMSPropertyNames.getEditorialDbName());
            imitateResponseForWOL(fileName, doiPrefix, RecordHelper::buildDoiCDSRAndEditorial, pubNames, !withErr,
                                  withErr, CochraneCMSPropertyNames.getWollitEventBatch());
        }

        public void imitateCcaResponseForWOL(String fileName, Collection<String> pubNames) {
            boolean withErr = CochraneCMSPropertyNames.getWOLLiteratumEventsImitateError() > 0;
            String doiPrefix = PublishHelper.defineLiteratumDoiPrefixByDatabase(
                    CochraneCMSPropertyNames.getCcaDbName());
            imitateResponseForWOL(fileName, doiPrefix, RecordHelper::buildDoiCCA, pubNames, !withErr, withErr,
                    CochraneCMSPropertyNames.getWollitEventBatch());
        }

        public void imitateCentralResponseForWOL(String fileName, Collection<String> pubNames) {
            boolean withErr = CochraneCMSPropertyNames.getWOLLiteratumEventsImitateError() > 0;
            String eventFileName = Constants.DOI_PREFIX + fileName.replace(Extensions.ZIP, "").replace("#", "_");
            imitateResponseForWOL(fileName, eventFileName, RecordHelper::buildDoiCentral, pubNames, !withErr,
                        withErr, CochraneCMSPropertyNames.getWollitEventBatch());
        }

        private void imitateResponseForWOL(String fileName, String doi, Function<String, String> buildDoi,
            Collection<String> pubNames, boolean online, boolean withErr, int batch) {
            if (pubNames.size() <= batch) {
                imitateResponseForWOL(fileName, doi, buildDoi, pubNames, online, withErr);
                return;
            }
            CollectionCommitter<String> committer = new CollectionCommitter<String>(batch, new ArrayList<>()) {
                @Override
                public void commit(Collection<String> list) {
                    imitateResponseForWOL(fileName, doi, buildDoi, list, online, withErr);
                }
            };
            pubNames.forEach(committer::commit);
            committer.commitLast();
        }

        private void imitateResponseForWOL(String fileName, String doi, Function<String, String> buildDoi,
            Iterable<String> pubNames, boolean online, boolean withErr) {
            try {
                LiteratumSentConfirm response = new LiteratumSentConfirm();
                Date dt = new Date();
                response.setEventId("testWOLLIT_EventId" + dt.getTime());
                //response.setTransactionId("testWOLLIT_TransactionId" + dt.getTime());
                response.setDeliveryId(fileName);
                response.setSourceSystem(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterWol());
                response.setEventType(online ? CochraneCMSPropertyNames.getLiteratumEventOnlineFilter()
                        : CochraneCMSPropertyNames.getLiteratumEventOnLoadFilter());
                response.setDoi(doi);
                response.setEndedAtTime(OffsetDateTime.now().plus(1, ChronoUnit.SECONDS).toString());

                List<LiteratumSentConfirm.HasPart> parts = new ArrayList<>();
                for (String pubName : pubNames) {
                    LiteratumSentConfirm.HasPart part = addPart(buildDoi.apply(pubName), parts);
                    if (!online) {
                        LiteratumSentConfirm.Messages msg = new LiteratumSentConfirm.Messages();
                        msg.setMessageLevel(withErr ? "error" : "info");
                        msg.setMessageText(withErr ? "an error for testing purposes" : "OK");

                        List<LiteratumSentConfirm.Messages> list = new ArrayList<>();
                        list.add(msg);
                        part.setMessages(list);
                    }
                }
                response.setHasPart(parts);
                response.setRawData(response.toString());
                sendResponse(response);

            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }

        public void imitateLocalResponseForHW(String fileName, String doi, String date, boolean firstOnline,
                                              boolean offLine) throws Exception {

            LiteratumSentConfirm response = new LiteratumSentConfirm();
            Date dt = new Date();
            response.setEventId("testHW_localEventId" + dt.getTime());
            //response.setTransactionId("testHW_localTransactionId" + dt.getTime());
            response.setSourceSystem(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterSemantico());

            response.setEventType(offLine ? CochraneCMSPropertyNames.getLiteratumEventOfflineFilter()
                    : CochraneCMSPropertyNames.getLiteratumEventOnlineFilter());
            response.setDoi(doi);
            response.setEndedAtTime(date);
            response.setDeliveryId(fileName);

            List<LiteratumSentConfirm.WorkflowEventGroup> eventGroups = new ArrayList<>();
            if (!offLine) {
                addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, date, eventGroups);
                if (firstOnline) {
                    addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, date, eventGroups);
                    addWorkflowEventGroup(LiteratumEvent.WRK_EVENT_FIRST_ONLINE, date, eventGroups);
                    response.setWorkflowEventGroup(eventGroups);
                    response.setFirstPublishedOnline(true);

                } else {
                    response.setFirstPublishedOnline(false);
                }
            }
            response.setRawData(response.toString());
            sendResponseToLocal(response, SEND_RESPONSE_DELAY);
        }

        private void sendResponse(LiteratumSentConfirm response) throws Exception {
            if (CochraneCMSPropertyNames.isLiteratumPublishTestModeDev()) {
                sendResponseToLocal(response, SEND_RESPONSE_DELAY);
            } else {
                sendResponseToGlobal(LiteratumResponseParser.asJsonString(response));
            }
        }

        public void testConnection() throws Exception {
            ConnectionFactory connectionFactory = getQueueConnectionData().getConnectionFactory();
            Queue responseQueue = getQueueConnectionData().getQueue();
            String userName = getQueueConnectionData().getUserName();
            String password = getQueueConnectionData().getPassword();

            JMSSender.send(connectionFactory, responseQueue, f -> null, userName, password);
        }

        public void sendResponseToLocal(LiteratumSentConfirm response, int delay) throws Exception {
            ConnectionFactory connectionFactory = responseHandler.getLocalConnectFactory();
            Queue responseQueue = responseHandler.getLocalQueue();
            if (connectionFactory == null || responseQueue == null) {
                return;
            }
            JMSSender.send(connectionFactory, responseQueue,
                    f -> PublishHelper.createAcceptPublishMessage(response, f), delay);
        }

        private void sendResponseToGlobal(String response) throws Exception {
            ConnectionFactory connectionFactory = getQueueConnectionData().getConnectionFactory();
            Queue responseQueue = getQueueConnectionData().getQueue();
            String userName = getQueueConnectionData().getUserName();
            String password = getQueueConnectionData().getPassword();

            JMSSender.send(connectionFactory, responseQueue, f -> f.createTextMessage(response), userName, password);
        }

        private JmsQueueConnectionData getQueueConnectionData() throws Exception {
            if (queueConnectionData == null) {
                queueConnectionData = createJmsQueueConnectionData();
            }
            return queueConnectionData;
        }

        private static JmsQueueConnectionData createJmsQueueConnectionData() throws Exception {
            PublishProfile publishProfile = PublishProfile.PUB_PROFILE.get();
            Properties contextProperties = getContextProperties(publishProfile);
            String queueName = publishProfile.getLiteratumResponse();
            return new JmsQueueConnectionData(contextProperties, queueName);
        }

        private static Properties getContextProperties(PublishProfile publishProfile) throws Exception {
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
            properties.put(Context.SECURITY_PRINCIPAL, publishProfile.getLiteratumUser());
            properties.put(Context.SECURITY_CREDENTIALS, publishProfile.getLiteratumPassword());
            String uri = publishProfile.getLiteratumUrl();
            if (uri == null) {
                throw new CmsException("'literatum-url' is not configured");
            }
            uri = uri.trim();
            if (uri.isEmpty()) {
                throw new CmsException("'literatum-url' is empty");
            }
            properties.put(Context.PROVIDER_URL, uri);
            return properties;
        }

        protected static String asString(LiteratumSentConfirm response) {
            return response.getRawData();
        }
    }
}
