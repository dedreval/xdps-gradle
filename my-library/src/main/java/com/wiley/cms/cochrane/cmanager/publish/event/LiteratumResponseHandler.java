package com.wiley.cms.cochrane.cmanager.publish.event;

import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.services.WREvent;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.process.jms.JMSSender.MessageSelector;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.TextMessage;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 10.07.2017
 */
public class LiteratumResponseHandler implements MessageSelector {
    private static final Logger LOG = Logger.getLogger(LiteratumResponseHandler.class);
    private final LiteratumResponseParser responseMessageParser;

    private QueueConnectionFactory localConnectFactory;
    private Queue publishQueue;
    private int msgCount;

    public LiteratumResponseHandler() {
        this(new LiteratumResponseParser());
    }

    public LiteratumResponseHandler(LiteratumResponseParser responseMessageParser) {
        this.responseMessageParser = responseMessageParser;
    }

    @Override
    public boolean selectMessage(Message msg) {
        Optional<String> msgBody = asTextMessage(msg).flatMap(LiteratumResponseHandler::getMessageBody);
        try {
            Optional<LiteratumSentConfirm> opt = msgBody.flatMap(this::toLiteratumResponse);
            if (opt.isPresent()) {
                LiteratumSentConfirm response = opt.get();
                Date date = parseDate(response.getEndedAtTime());
                response.setResponseDate(date);
                JMSSender.send(localConnectFactory, publishQueue,
                        f -> PublishHelper.createAcceptPublishMessage(response, f));
                msgCount++;
            }
        } catch (Throwable tr)  {
            LOG.error(tr.getMessage());
        }
        return true;
    }

    private static Optional<TextMessage> asTextMessage(Message msg) {
        if (!(msg instanceof TextMessage)) {
            LOG.warn(String.format("Unexpected message type. Expected %s, but saw %s",
                    TextMessage.class, msg.getClass()));
            return Optional.empty();
        }
        return Optional.of((TextMessage) msg);
    }

    private static Optional<String> getMessageBody(TextMessage msg) {
        String body = null;
        try {
            body = msg.getText();
            if (StringUtils.isEmpty(body)) {
                LOG.error("Message body is empty");
            }
        } catch (JMSException e) {
            LOG.error("Failed to get message body", e);
        }
        return Optional.ofNullable(body);
    }

    private Optional<LiteratumSentConfirm> toLiteratumResponse(String msg) {
        LOG.debug("Literatum response:\n" + msg);

        LiteratumSentConfirm response = null;
        try {
            if (StringUtils.isNotEmpty(msg)) {
                response = LiteratumResponseParser.parse(msg);
            }
        } catch (Exception e) {
            LOG.error("Failed to parse literatum response. " + e.getMessage());
        }
        return Optional.ofNullable(response);
    }

    public static LiteratumEvent createLiteratumEvent(LiteratumSentConfirm response, String doi, boolean fromParts) {
        try {
            Date date = parseDate(response.getEndedAtTime());
            LiteratumEvent ret = (LiteratumEvent) WREvent.createOnOrOfflineLiteratumEvent(
                    response.getSourceSystem(),
                    response.getEventType(),
                    date,
                    doi,
                    response.getDeliveryId(), fromParts);

            ret.setEndedAtTime(response.getEndedAtTime());
            ret.setFirstPublishedOnline(response.isFirstPublishedOnline());
            ret.setRawData(response.getRawData());

            if (ret.getBaseType().isActualPublicationDateSupported() && isHWContentOnline(response)) {
                setDatesToEvent(doi, response, ret);
            }
            return ret;

        } catch (Exception e) {
            LOG.warn(String.format("Literatum response for %s is ignored (%s)", doi, e.getMessage()));
            return null;
        }
    }

    private static void setDatesToEvent(String doi, LiteratumSentConfirm response, LiteratumEvent event)
            throws Exception {
        if (response.getWorkflowEventGroup().isEmpty()) {
            return;
        }
        boolean isFirstOnline = response.isFirstPublishedOnline();
        setPublishDate(doi, LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, response, event, true, isFirstOnline);
        setPublishDate(doi, LiteratumEvent.WRK_EVENT_FIRST_ONLINE, response, event, false, isFirstOnline);
        setPublishDate(doi, LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, response, event, false, isFirstOnline);
    }

    private static void setPublishDate(String doi, String pubDateTag, LiteratumSentConfirm response,
                                       LiteratumEvent event, boolean mandatory, boolean firstOnline) throws Exception {
        String publishDate = response.getWorkflowEventValue(pubDateTag);
        if (publishDate != null) {
            event.setPublishDate(pubDateTag, publishDate);
            return;
        }
        if (mandatory) {
            throw new Exception(getMissingFiledMessage(doi, pubDateTag, firstOnline));
        }
        if (firstOnline) {
            LOG.warn(getMissingFiledMessage(doi, pubDateTag, firstOnline));
        }
    }

    private static String getMissingFiledMessage(String doi, String pubDateTag, boolean firstOnline) {
        return String.format("event field '%s' for %s is missing, firstPublishedOnline=%s",
                pubDateTag, doi, firstOnline);
    }

    static boolean isHWContentOnline(LiteratumSentConfirm response) {
        return response.isHW() && response.isOnline();
    }

    int getAcceptedMessagesCountAndReset() {
        int ret = msgCount;
        msgCount = 0;
        return ret;
    }

    static Date parseDate(String dateRepresentation) {
        return Date.from(OffsetDateTime.parse(dateRepresentation).toInstant());
    }

    void initContext(QueueConnectionFactory connectionFactory, Queue publishQueue) {
        this.localConnectFactory = connectionFactory;
        this.publishQueue = publishQueue;
    }

    QueueConnectionFactory getLocalConnectFactory() {
        return localConnectFactory;
    }

    Queue getLocalQueue() {
        return publishQueue;
    }
}
