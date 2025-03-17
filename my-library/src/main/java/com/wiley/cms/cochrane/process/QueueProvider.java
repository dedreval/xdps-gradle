package com.wiley.cms.cochrane.process;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;

import com.wiley.cms.process.jms.IQueueProvider;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/21/2017
 */
@Local(IQueueProvider.class)
@Singleton
@Startup
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class QueueProvider implements IQueueProvider {

    public static final String QUEUE_PART_DEFAULT = "CMSProcessPartQueue";
    public static final String QUEUE_ACCEPT_PART_DEFAULT = "CMSAcceptProcessPartQueue";
    public static final String QUEUE_PART_BACKGROUND = "CMSProcessPartBGQueue";
    public static final String QUEUE_ACCEPT_PART_BACKGROUND = "CMSAcceptProcessPartBGQueue";
    public static final String QUEUE_CONVERSION = "Wml3gConversionQueue";
    public static final String QUEUE_ACCEPT_PUBLISH = "AcceptPublishQueue";

    @Resource(mappedName = "java:jboss/exported/jms/queue/cms_tasks")
    private Queue taskQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/cms_process_part")
    private Queue processPartQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/cms_accept_process_part")
    private Queue acceptProcessPartQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/cms_process_part_bg")
    private Queue processPartBackgroundQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/cms_accept_process_part_bg")
    private Queue acceptProcessPartBackgroundQueue;

    @Resource(mappedName = "java:jboss/exported/jms/queue/wml3g-conversion")
    private Queue conversionQueue;

    //@Resource(mappedName = "java:jboss/exported/jms/queue/accept_publish")
    //private Queue acceptPublishQueue;

    private final Map<String, Queue> queues = new HashMap<>();

    @PostConstruct
    public void start() {
        queues.put(QUEUE_PART_DEFAULT, processPartQueue);
        queues.put(QUEUE_ACCEPT_PART_DEFAULT, acceptProcessPartQueue);
        queues.put(QUEUE_PART_BACKGROUND, processPartBackgroundQueue);
        queues.put(QUEUE_ACCEPT_PART_BACKGROUND, acceptProcessPartBackgroundQueue);
        queues.put(QUEUE_CONVERSION, conversionQueue);
        //queues.put(QUEUE_ACCEPT_PUBLISH, acceptPublishQueue);
    }

    public Queue getTaskQueue() {
        return taskQueue;
    }

    public Queue getQueue(String name) {
        return queues.get(name);
    }
}
