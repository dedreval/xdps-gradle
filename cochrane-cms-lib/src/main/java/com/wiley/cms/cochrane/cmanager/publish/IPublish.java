package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;

/**
 * @author <a href="mailto:dkotsubo@wiley.com">Dmitry Kotsubo</a>
 * @version 11.03.2010
 */
public interface IPublish {
    int BY_ISSUE = 0;
    int BY_WHEN_READY = -1;

    void start(PublishWrapper publish, PublishOperation operation);

    default IPublish setContext(IResultsStorage rs, IPublishStorage ps, IFlowLogger flowLogger) {
        return this;
    }
}
