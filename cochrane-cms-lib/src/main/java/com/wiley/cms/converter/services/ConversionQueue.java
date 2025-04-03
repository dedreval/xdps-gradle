package com.wiley.cms.converter.services;

import java.util.HashSet;
import java.util.Set;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 16.07.12
 */

@MessageDriven(activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/conversion_revman"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = "ConversionQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConversionQueue implements MessageListener {
    private static final Logger LOG = Logger.getLogger(ConversionQueue.class);

    private static final int MSG_PARAM_SOURCE = 0;
    private static final int MSG_PARAM_PACK_NAME = 1;
    private static final int MSG_PARAM_PACK_ID = 2;
    private static final int MSG_PARAM_ISSUE = 3;
    private static final int MSG_PARAM_INCLUDES = 4;

    @EJB
    private IRevmanLoader loader;

    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        LOG.info("Revman conversion item has been sent");

        DeliveryPackageInfo sources = null;
        Integer deliveryFileId = null;
        String packageName = null;
        IssueVO issue = null;
        Set<String> includedNames = null;
        try {
            ObjectMessage msg = (ObjectMessage) message;
            Object[] parameters = (Object[]) msg.getObject();
            sources = (DeliveryPackageInfo) parameters[MSG_PARAM_SOURCE];
            packageName = (String) parameters[MSG_PARAM_PACK_NAME];
            deliveryFileId = (Integer) parameters[MSG_PARAM_PACK_ID];
            issue = (IssueVO) parameters[MSG_PARAM_ISSUE];

            Object[] names = (Object[]) parameters[MSG_PARAM_INCLUDES];
            if (names != null) {
                includedNames = new HashSet<String>();
                for (Object o: names) {
                    includedNames.add(o.toString());
                }
            }

        } catch (JMSException e) {
            handleException(e, message);
        }

        if (sources == null || packageName == null || deliveryFileId == null || issue == null) {
            LOG.error(String.format(
                "Revman params contain nulls, sources=%s, packageName=%s, deliveryFileId=%s, issue=%s",
                    sources, packageName, deliveryFileId, issue));
            if (deliveryFileId != null && packageName != null
                    && includedNames != null && includedNames.contains(JMSSender.LAST_WORD)) {
                loader.onConversionFailed(packageName, deliveryFileId,
                        String.format("conversion was stopped by '%s' message", JMSSender.LAST_WORD));
            }
            return;
        }

        try {
            loader.convertRevmanPackage(sources, packageName, deliveryFileId, issue, includedNames);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            loader.onConversionFailed(packageName, deliveryFileId , e.getMessage());
        }
    }

    private void handleException(JMSException e, Message message) {
        try {
            if (!message.getJMSRedelivered()) {
                LOG.debug("conversion message was not redelivered");
                throw new EJBException(e);
            } else {
                LOG.debug("conversion message was redelivered");
                LOG.error(e, e);
            }
        } catch (JMSException e1) {
            LOG.debug("jmsException was for conversion message");
            throw new EJBException();
        }
    }
}
