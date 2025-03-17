package com.wiley.cms.cochrane.cmanager.publish.event;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 06.07.2017
 */
public class JmsQueueConnectionData {

    private final ConnectionFactory connectionFactory;
    private final Queue queue;
    private final String userName;
    private final String password;

    public JmsQueueConnectionData(Properties contextProperties, String queueName) throws Exception {
        InitialContext ctx = new InitialContext(contextProperties);
        connectionFactory = (QueueConnectionFactory) ctx.lookup("QueueConnectionFactory");
        queue = (Queue) ctx.lookup(queueName);
        userName = contextProperties.getProperty(Context.SECURITY_PRINCIPAL);
        password = contextProperties.getProperty(Context.SECURITY_CREDENTIALS);
    }

    protected JmsQueueConnectionData(
            ConnectionFactory connectionFactory,
            Queue queue,
            String userName,
            String password) {
        this.connectionFactory = connectionFactory;
        this.queue = queue;
        this.userName = userName;
        this.password = password;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public Queue getQueue() {
        return queue;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
