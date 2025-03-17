package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/5/2019
 */
public class QueuelessEntirePublisher extends EntirePublisher {

    public QueuelessEntirePublisher(IResultsStorage rs, IPublishStorage ps, IFlowLogger logService) {
        this.rs = rs;
        this.ps = ps;
        this.flowLogger = logService;
    }

    @Override
    protected void proceedNext(PublishWrapper publish) {
        start(publish, db.getDbName());
    }
}