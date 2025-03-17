package com.wiley.cms.cochrane.activitylog.kafka;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>OLga Soletskaya</a>
 * @version 1.0
 */
@MessageDriven(
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/cms_kafka_producer"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = "KafkaProducerQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class KafkaProducerQueue implements MessageListener {
    public static final String MAP_NAME = "java:jboss/exported/jms/queue/cms_kafka_producer";
    private static final Logger LOG = Logger.getLogger(KafkaProducerQueue.class);

    private final IKafkaMessageProducer kafkaProducer = KafkaMessageProducer.instance();

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        SFEvent event;
        try {
            kafkaProducer.produceMessage(JMSSender.getObjectMessage(message, SFEvent.class));
        } catch (Throwable tr) {
            LOG.error(tr);
        }
    }
}

