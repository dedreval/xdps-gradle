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

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>OLga Soletskaya</a>
 * @version 1.0
 */
@MessageDriven(
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/hp_publishing"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = "HPPublishQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class HPPublishQueue extends WRPublishQueue implements MessageListener {
    static final String MAP_NAME = "java:jboss/exported/jms/queue/hp_publishing";

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    protected QueueConnectionFactory connectionFactory;

    @Resource(mappedName = HPPublishQueue.MAP_NAME)
    private Queue publishQueue;

    @Override
    protected void proceedNext(final PublishWrapper publish) throws JMSException {

        JMSSender.send(connectionFactory, publishQueue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return PublishHelper.createPublishMessage(publish, db.getId(), session);
            }
        });
    }
}

