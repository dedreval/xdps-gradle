package com.wiley.cms.process.jms;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.ejb.EJBException;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;

import com.wiley.cms.process.IModelController;
import com.wiley.cms.process.JpaCallback;
import com.wiley.cms.process.ProcessStorageFactory;
import com.wiley.tes.util.Pair;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessManager;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * @version 03.07.2013
 */

public final class JMSSender {

    public static final String POOLED_NON_XA_CONNECTION_LOOKUP = "java:/JmsNonXA";
    public static final String POOLED_CONNECTION_LOOKUP = "java:/JmsXA";
    public static final String DEFAULT_CONNECTION_LOOKUP = "java:/ConnectionFactory";
    public static final String CONNECTION_LOOKUP = DEFAULT_CONNECTION_LOOKUP;
    //       Property.get("JMS_CONNECTION_LOOKUP", POOLED_NON_XA_CONNECTION_LOOKUP).get().getValue();

    public static final String LAST_WORD = "dead-letter";
    public static final String GROUP_PARAM = "JMSXGroupID";

    private static final Logger LOG = Logger.getLogger(JMSSender.class);

    private static final String REDELIVERY_LIMIT_PARAM   = "maxDeliveryAttempts";
    private static final String REDELIVERY_DELAY_PARAM   = "JMS_JBOSS_REDELIVERY_DELAY";
    private static final String REDELIVERY_COUNT_PARAM   = "JMSXDeliveryCount";
    private static final String SCHEDULED_DELIVERY_PARAM =
            Property.get("JMS_SCHEDULED", "_HQ_SCHED_DELIVERY").get().getValue();

    private static final Res<Property> XA_MODE = Property.get("JMS_XA_MODE", "false");

    private JMSSender() {
    }

    public static void createQueue(QueueConnectionFactory factory, String destination) throws JMSException {
        InitialContext ic;
        Queue queue = null;
        Connection connection = null;
        try {
            ic = new InitialContext();
            queue = (Queue) ic.lookup(destination);

            connection = factory.createConnection();
            connection.start();

        } catch (NamingException ne) {
            LOG.error(ne);
        } finally {
            close(connection, null, null, null);
        }
    }

    public static QueueConnectionFactory lookupQueue(String lookup) {
        try {
            InitialContext ctx = new InitialContext();
            return (QueueConnectionFactory) ctx.lookup(lookup);
        } catch (NamingException ne) {
            LOG.error(ne);
            return null;
        }
    }

    public static QueueConnectionFactory lookupQueue() {
        return lookupQueue(CONNECTION_LOOKUP);
    }

    public static Connection sendInTemporaryQueue(String destination, MessageCreator msgCreator,
        int priority, int delay) throws JMSException {
        QueueConnectionFactory factory = lookupQueue(DEFAULT_CONNECTION_LOOKUP);
        Pair<Connection, TemporaryQueue> pair = getTemporaryQueue(factory, destination);
        send(factory, pair.second, msgCreator, null, null, priority, delay);
        return pair.first;
    }

    public static void consumeFromTemporaryQueue(Connection connection, String destination, MessageSelector msgSelector)
        throws JMSException {

        QueueConnectionFactory factory = lookupQueue(DEFAULT_CONNECTION_LOOKUP);
        consume(connection, getTemporaryQueue(factory, destination).second, msgSelector);
    }

    private static Pair<Connection, TemporaryQueue> getTemporaryQueue(QueueConnectionFactory factory,
        String destination) throws JMSException {

        InitialContext ic = null;
        TemporaryQueue queue = null;
        Connection connection = null;
        try {
            ic = new InitialContext();
            queue = (TemporaryQueue) ic.lookup(destination);
        } catch (NamingException ne) {
            LOG.error(ne);
        }

        if (ic != null && queue == null) {

            connection = factory.createConnection();
            Session queueSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            queue = queueSession.createTemporaryQueue();
            close(null, queueSession, null, null);

            try {
                ic.bind(destination, queue);

            } catch (NamingException ne) {
                LOG.error(ne);
                throw new JMSException(ne.getMessage());
            }
        }

        return new Pair<>(connection, queue);
    }

    public static void send(final ConnectionFactory connectionFactory, final Destination destination,
        final MessageCreator msgCreator, int priority, int delay) throws JMSException {

        send(connectionFactory, destination, msgCreator, null, null, priority, delay);
    }

    public static void send(final ConnectionFactory connectionFactory, final Destination destination,
        final MessageCreator msgCreator, String user, String password) throws JMSException {

        send(connectionFactory, destination, msgCreator, user, password, IProcessManager.USUAL_PRIORITY, 0);
    }

    public static void consume(final ConnectionFactory connectionFactory, final Destination destination,
        final MessageSelector msgSelector, String user, String password) throws JMSException {

        Connection connection = null;
        Session session = null;
        MessageConsumer messageConsumer = null;

        try {
            connection = user == null ? connectionFactory.createConnection()
                    : connectionFactory.createConnection(user, password);
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            messageConsumer = session.createConsumer(destination);

            consume(connection, messageConsumer, msgSelector);

        } finally {
            close(connection, session, null, messageConsumer);
        }
    }

    public static void consume(Connection connection, Destination destination, MessageSelector msgSelector)
        throws JMSException {
        Session session = null;
        MessageConsumer messageConsumer = null;
        try {
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            messageConsumer = session.createConsumer(destination);

            consume(connection, messageConsumer, msgSelector);

        } finally {
            close(null, session, null, messageConsumer);
        }
    }

    private static void consume(Connection connection, MessageConsumer messageConsumer, MessageSelector msgSelector)
        throws JMSException {

        connection.start();

        Message msg = messageConsumer.receiveNoWait();

        while (msg != null) {
            if (msgSelector.selectMessage(msg)) {
                msg.acknowledge();
            }
            msg = messageConsumer.receiveNoWait();
        }
    }

    public static void send(ConnectionFactory connectionFactory, Destination destination,
        MessageCreator msgCreator, String user, String password, int priority, int delay) throws JMSException {
        try {
            sendMessage(connectionFactory, destination, msgCreator, user, password, priority, delay);
        } catch (JMSException e) {
            if (e.getMessage().contains("Timed out")) {
                LOG.error(e);
                // sendMessage(connectionFactory, destination, msgCreator, user, password, priority, delay);
            } else {
                throw e;
            }
        }  catch (Exception e1) {
            throw e1;
        }
    }

    private static void sendMessage(ConnectionFactory connectionFactory, Destination destination,
        MessageCreator msgCreator, String user, String password, int priority, int delay) throws JMSException {

        Connection connection = null;
        Session session = null;
        MessageProducer producer = null;
        try {
            connection = user == null ? connectionFactory.createConnection()
                    : connectionFactory.createConnection(user, password);
            connection.start();

            session = XA_MODE.get().asBoolean() ? createXASession(connection)
                    : connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            producer = session.createProducer(destination);

            Message message = msgCreator.createMessage(session);
            if (message != null) {
                if (delay > 0) {
                    message.setLongProperty(SCHEDULED_DELIVERY_PARAM, System.currentTimeMillis() + delay);
                }
                producer.send(message, producer.getDeliveryMode(), priority, producer.getTimeToLive());
                session.commit();
            }

        } finally {
            close(connection, session, producer, null);
        }
    }

    private static Session createXASession(Connection connection) throws JMSException {
        try {
            Session session;
            String[] err = new String[1];
            IModelController mc = ProcessStorageFactory.getFactory().getInstance();
            session = mc.execute(new JpaCallback<Session>() {
                public Session doInJpa(EntityManager em) {
                    try {
                        connection.start();
                        return connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

                    } catch (Exception e) {
                        err[0] = e.getMessage();
                    }
                    return null;
                }
            });
            if (session == null) {
                throw new Exception(err[0]);
            }
            return session;

        } catch (Exception e) {
            throw new JMSException(e.getMessage());
        }
    }

    public static void send(final ConnectionFactory connectionFactory, final Destination destination,
        final MessageCreator messageCreator, int delay) throws JMSException {
        send(connectionFactory, destination, messageCreator, IProcessManager.USUAL_PRIORITY, delay);
    }

    public static void send(final ConnectionFactory connectionFactory, final Destination destination,
        final MessageCreator messageCreator) throws JMSException {
        send(connectionFactory, destination, messageCreator, IProcessManager.USUAL_PRIORITY, 0);
    }

    private static void close(Connection connection, Session session, MessageProducer messageProducer,
        MessageConsumer messageConsumer) {
        if (messageConsumer != null) {
            try {
                messageConsumer.close();
            } catch (JMSException e) {
                LOG.debug("messageConsumer close failed", e);
            }
        }
        if (messageProducer != null) {
            try {
                messageProducer.close();
            } catch (JMSException e) {
                LOG.debug("messageProducer close failed", e);
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                LOG.debug("session close failed", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                LOG.debug("connection close failed", e);
            }
        }
    }

    public static boolean exceptionLimitHandle(Message message, String msg, int limit) throws EJBException {
        try {
            if (!message.getJMSRedelivered()) {
                LOG.debug("message should be redelivered");
                throw new EJBException(msg);

            } else {
                Enumeration e = message.getPropertyNames();
                while (e.hasMoreElements()) {

                    Object el = e.nextElement();
                    LOG.debug("property name: " + el);
                    Object p = message.getObjectProperty(el.toString());
                    LOG.debug("property value: " + p);
                }

                int count = message.getIntProperty(REDELIVERY_COUNT_PARAM);
                LOG.debug(String.format("message should be redelivered, try=%d, limit=%d", count, limit));
                //int limit = message.getIntProperty(REDELIVERY_LIMIT_PARAM);

                if (count <= limit) {
                    //LOG.debug(String.format("message should be redelivered, try=%d, limit=%d", count, limit));
                    throw new EJBException(msg);
                }

                LOG.error(msg);
                LOG.debug("message can't be redelivered");
                return true;
            }
        } catch (JMSException e1) {
            LOG.debug("jmsException was " + e1.getMessage());
            throw new EJBException();
        }
    }

    public static int[] getArrayIntParam(Message message) throws Exception {

        ObjectMessage msg = getObjectParam(message, ObjectMessage.class);
        Object param = msg.getObject();
        if (!(param instanceof int[])) {
            throw new Exception("int[] expected!");
        }
        return (int[]) msg.getObject();
    }

    public static Object[] getArrayObjectParam(Message message) throws Exception {

        ObjectMessage msg = getObjectParam(message, ObjectMessage.class);
        Object param = msg.getObject();
        if (!(param instanceof Object[])) {
            throw new Exception("object[] expected!");
        }
        return (Object[]) msg.getObject();
    }

    public static List<Integer> getIntegerList(Message message) throws Exception {
        ObjectMessage msg = getObjectParam(message, ObjectMessage.class);
        return (List<Integer>) msg.getObject();
    }

    public static <T> T getObjectMessage(Message message, Class<T> cl) throws Exception {

        ObjectMessage msg = getObjectParam(message, ObjectMessage.class);
        return getObjectParam(msg.getObject(), cl);
    }

    public static <T> T getObjectParam(Object param, Class<T> cl) throws Exception {

        if (!cl.isInstance(param)) {
            throw new Exception(cl.getName() + " expected!");
        }
        return cl.cast(param);
    }

    public static ObjectMessage buildProcessMessage(int processId, String group, Session session) throws JMSException {
        return buildProcessMessage(processId, group, new ArrayList<>(), session);
    }

    public static ObjectMessage buildProcessMessage(int processId, String group, final ArrayList<Integer> ids,
                                                    Session session) throws JMSException {

        ObjectMessage msg = session.createObjectMessage();
        ids.add(processId);
        msg.setObject(ids);

        if (group != null) {
            msg.setStringProperty(GROUP_PARAM, group);
        }
        return msg;
    }

    /**
     * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
     * @version 13.11.2009
     */
    public interface MessageCreator {
        Message createMessage(Session session) throws JMSException;
    }

    /**
     * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
     *         Date: 8/4/2016
     */
    public interface MessageSelector {
        boolean selectMessage(Message msg) throws JMSException;
    }

    /**
     * Base for processing
     */
    public static class MessageProcessCreator implements MessageCreator {

        public ExternalProcess process;

        public void setProcess(ExternalProcess process) {
            this.process = process;
        }

        public ExternalProcess getProcess() {
            return process;
        }

        public Message createMessage(Session session) throws JMSException {

            ObjectMessage msg = session.createObjectMessage();
            msg.setObject(getProcess());
            return msg;
        }
    }
}
