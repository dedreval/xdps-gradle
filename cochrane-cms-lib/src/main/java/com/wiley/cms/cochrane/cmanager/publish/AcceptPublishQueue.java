package com.wiley.cms.cochrane.cmanager.publish;

import java.util.concurrent.TimeUnit;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseHandler;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseParser;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;
import com.wiley.cms.cochrane.process.QueueProvider;
import com.wiley.cms.cochrane.services.IWREventReceiver;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.03.19
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/accept_publish"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = QueueProvider.QUEUE_ACCEPT_PUBLISH)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptPublishQueue implements MessageListener {
    private static final Logger LOG = Logger.getLogger(AcceptPublishQueue.class);

    @EJB(beanName = "PublishService")
    private IPublishService ps;

    @EJB(beanName = "WREventReceiver")
    private IWREventReceiver eventReceiver;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        LiteratumSentConfirm response = null;
        try {
            response = JMSSender.getObjectMessage(message, LiteratumSentConfirm.class);
            if (CochraneCMSPropertyNames.isLiteratumPublishTestModeDev()) {
                LOG.debug(LiteratumResponseParser.asJsonString(response));
            }
            if (ps.acceptLiteratumDeliveryOnLoadToPublish(response, response.getResponseDate())) {
                return;
            }
            if (response.getHasPart().isEmpty()) {
                // HW CO event for CDSR, CCA, EDI, CENTRAL | HW OFFLINE events for CDSR, EDI (SPD)
                LiteratumEvent event = LiteratumResponseHandler.createLiteratumEvent(
                        response, response.getDoi(), false);
                if (event == null) {
                    return;
                }
                if (event.getBaseType().isCentral()) {
                    ps.acceptLiteratumDeliveryOnline(response, response.getResponseDate());
                } else {
                    acceptOnlineEvent(event);
                }
            } else if (!ps.acceptLiteratumDeliveryOnline(response, response.getResponseDate())) {
                // WOLLIT CO events for CDSR, CCA, EDI (when-ready)
                for (LiteratumSentConfirm.HasPart part: response.getHasPart()) {
                    if (part != null) {
                        acceptOnlineEvent(LiteratumResponseHandler.createLiteratumEvent(response, part.getDoi(), true));
                    }
                }
            }
        } catch (CmsException | CmsJTException ce) {
            if (response != null && ce.getErrorInfo().isRecordBlockedError()) {
                resendEvent(response);
            }
        } catch (Throwable tr) {
            LOG.error(tr.getMessage(), tr);
        }
    }

    private void resendEvent(LiteratumSentConfirm response) {
        try {
            LiteratumResponseChecker.Responder.instance().sendResponseToLocal(response,
                 (int) (TimeUnit.HOURS.convert(1, TimeUnit.MILLISECONDS) / 2));

            //LiteratumResponseChecker.Responder.instance().sendResponseToLocal(response,
            //        Now.calculateMillisInMinute());

        } catch (Throwable tr) {
            LOG.error(tr.getMessage(), tr);
        }
    }

    private void acceptOnlineEvent(LiteratumEvent event) throws Exception {
        if (event != null) {
            eventReceiver.handleEvent(event);
        }
    }
}

