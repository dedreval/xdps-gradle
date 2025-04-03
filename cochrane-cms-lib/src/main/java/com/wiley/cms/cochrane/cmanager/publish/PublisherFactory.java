package com.wiley.cms.cochrane.cmanager.publish;

import javax.ejb.EJB;
import javax.jms.JMSException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.tes.util.Logger;

/**
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @author <a href='mailto:osoletskay@wiley.com'>Olga Soletskay</a>
 * @version 1.0
 */
public abstract class PublisherFactory {
    protected static final Logger LOG = Logger.getLogger(PublisherFactory.class);

    protected static final String WOL = "wol#";
    protected static final String WOL3G = "wol3g#";
    protected static final String WOL3G_DEFAULT = "wol3g#default";
    protected static final String CCH = "cch#";
    protected static final String DS = "ds#";
    protected static final String DS_DEFAULT = "ds#default";
    protected static final String SEMANTICO = "semantico#";
    protected static final String SEMANTICO_TOPICS = "semanticoTop#";
    protected static final String SEMANTICO_DEFAULT = "semantico#default";
    protected static final String SEMANTICO_TOPICS_DEFAULT = "semanticoTop#default";
    protected static final String SEMANTICO_DEL = "semanticoDel#";
    protected static final String LITERATUM = "literatum#";
    protected static final String LITERATUM_DEFAULT = "literatum#default";
    protected static final String UDW_DEFAULT = "udw#default";
    protected static final String ARIES = "aries#";

    protected static final String DEFAULT_SUFFIX = "#default";

    @EJB(beanName = "ResultsStorage")
    protected IResultsStorage rs;

    @EJB(beanName = "PublishStorage")
    protected IPublishStorage ps;

    @EJB(beanName = "FlowLogger")
    protected IFlowLogger flowLogger;

    abstract IPublish getDeleter(PublishWrapper publish);

    abstract IPublish getGenerator(PublishWrapper publish);

    abstract IPublish getSender(PublishWrapper publish);

    abstract IPublish getUnpacker(PublishWrapper publish);

    protected abstract void proceedNext(PublishWrapper publish) throws JMSException;

    protected void proceedLast(PublishWrapper publish, ActivityLogEntity.EntityLevel lvl) {
        publish.resetPublishForWaiting(ps);
        if (publish.getPublishEntity().sent()) {
            flowLogger.getActivityLog().info(lvl, ILogEvent.PUBLISH_SUCCESSFUL, publish.getId(), publish.getFileName(),
                    publish.getType(), publish.getStatus());
            LOG.info("Package publishing has finished for %s: %s", lvl, publish.getFileName());
        }
    }
}

