package com.wiley.cms.cochrane.cmanager.publish;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;

import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;

import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.01.2010
 */
@MessageDriven(
        activationConfig =
                {
                        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                        @ActivationConfigProperty(propertyName = "destination",
                                propertyValue = "queue/entire_publishing"),
                        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "4")
                },
        name = "EntirePublishQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EntirePublishQueue extends EntirePublisher implements MessageListener {
    private static final Logger LOG = Logger.getLogger(EntirePublishQueue.class);

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    protected QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/entire_publishing")
    private Queue publishQueue;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        PublishWrapper publish;
        String dbName;
        try {
            publish = PublishHelper.getPublishWrapper(message);
            dbName = PublishHelper.getDataBaseName(message);

        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            return;
        }

        start(publish, dbName);
    }

    protected void proceedNext(final PublishWrapper publish) throws JMSException {

        JMSSender.send(connectionFactory, publishQueue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return PublishHelper.createPublishMessage(publish, db.getDbName(), session);
            }
        });
    }
}