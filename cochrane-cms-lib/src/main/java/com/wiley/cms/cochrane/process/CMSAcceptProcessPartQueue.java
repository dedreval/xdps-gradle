package com.wiley.cms.cochrane.process;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import javax.jms.MessageListener;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/23/2018
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/cms_accept_process_part"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "6")
        }, name = QueueProvider.QUEUE_ACCEPT_PART_DEFAULT)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CMSAcceptProcessPartQueue extends BaseCMSAcceptProcessPartQueue implements MessageListener {
    private static final Logger LOG = Logger.getLogger(CMSAcceptProcessPartQueue.class);

    @Override
    protected Logger log() {
        return LOG;
    }
}
