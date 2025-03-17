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
 *         Date: 3/24/2020
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/cms_process_part_bg"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "8")
        }, name = QueueProvider.QUEUE_PART_BACKGROUND)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CMSProcessPartBGQueue extends ProcessPartQueue<ProcessHandler> implements MessageListener {
    private static final Logger LOG = Logger.getLogger(CMSProcessPartBGQueue.class);

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
