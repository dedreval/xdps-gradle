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
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@MessageDriven(
        activationConfig =
            {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/publishing"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "4")
            }, name = "PublishQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PublishQueue extends IssuePublisher implements MessageListener {

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    protected QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/publishing")
    private Queue publishQueue;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        PublishWrapper publish;
        int dbId;
        try {
            dbId = PublishHelper.getDataBaseId(message);
            publish = PublishHelper.getPublishWrapper(message);
            
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            return;
        }

        start(publish, dbId);
    }

    @Override
    protected void proceedNext(final PublishWrapper publish) throws JMSException {

        JMSSender.send(connectionFactory, publishQueue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return PublishHelper.createPublishMessage(publish, db.getId(), session);
            }
        });
    }
}

