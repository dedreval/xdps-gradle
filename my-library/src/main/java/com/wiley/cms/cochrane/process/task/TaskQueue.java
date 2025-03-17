package com.wiley.cms.cochrane.process.task;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertiesMBean;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.jms.IQueueProvider;
import com.wiley.cms.process.task.BaseTaskQueue;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/8/2014
 */
@MessageDriven(
       activationConfig =
           {
               @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
               @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/cms_tasks"),
               @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "24")
           }, name = IQueueProvider.TASK_QUEUE_DEFAULT)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class TaskQueue extends BaseTaskQueue implements MessageListener {
    private static final Logger LOG = Logger.getLogger(TaskQueue.class);

    @EJB(beanName = "CochraneCMSProperties")
    private CochraneCMSPropertiesMBean props;

    @EJB(lookup = ProcessHelper.LOOKUP_TASK_MANAGER)
    private ITaskManager tm;

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        onMessage(message, tm);
    }
}
