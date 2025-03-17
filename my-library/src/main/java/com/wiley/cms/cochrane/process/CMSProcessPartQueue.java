package com.wiley.cms.cochrane.process;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessPartQueue;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/23/2018
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/cms_process_part"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "6")
        }, name = QueueProvider.QUEUE_PART_DEFAULT)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CMSProcessPartQueue extends ProcessPartQueue<ProcessHandler> implements MessageListener {
    private static final Logger LOG = Logger.getLogger(CMSProcessPartQueue.class);

    @EJB(beanName = "CMSProcessManager")
    private ICMSProcessManager manager;

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        onMessage(message, manager, ProcessHandler.class);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
