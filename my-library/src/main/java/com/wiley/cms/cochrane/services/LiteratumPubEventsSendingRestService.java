package com.wiley.cms.cochrane.services;

import com.wiley.cms.cochrane.cmanager.publish.event.JmsQueueConnectionData;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.jms.JMSSender;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.util.Properties;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 19.09.2017
 */
@Path("/lit-pub-events-sending")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LiteratumPubEventsSendingRestService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void send(String pubEvent) {
        JmsQueueConnectionData connectionData = establishJmsQueueConnection();
        sendPubEvent(pubEvent, connectionData);
    }

    private JmsQueueConnectionData establishJmsQueueConnection() {
        PublishProfile publishProfile = PublishProfile.PUB_PROFILE.get();
        Properties contextProperties = new Properties();
        contextProperties.put(Context.INITIAL_CONTEXT_FACTORY, "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        contextProperties.put(Context.SECURITY_PRINCIPAL, publishProfile.getLiteratumUser());
        contextProperties.put(Context.SECURITY_CREDENTIALS, publishProfile.getLiteratumPassword());
        contextProperties.put(Context.PROVIDER_URL, publishProfile.getLiteratumUrl());
        try {
            return new JmsQueueConnectionData(contextProperties, publishProfile.getLiteratumResponse());
        } catch (Exception e) {
            throw new RuntimeException("Unable to establish connection to JMS queue", e);
        }
    }

    private void sendPubEvent(final String pubEvent, JmsQueueConnectionData connectionData) {
        ConnectionFactory connectionFactory = connectionData.getConnectionFactory();
        Queue requestQueue = connectionData.getQueue();
        String userName = connectionData.getUserName();
        String password = connectionData.getPassword();
        JMSSender.MessageCreator messageCreator = new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createTextMessage(pubEvent);
            }
        };
        try {
            JMSSender.send(connectionFactory, requestQueue, messageCreator, userName, password);
        } catch (Exception e) {
            throw new RuntimeException("Unable to send message", e);
        }
    }
}
