package com.wiley.cms.cochrane.process;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.render.IRenderManager;
import com.wiley.cms.process.IProcessStorage;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/31/2019
 */
public interface IBeanProvider {
    IActivityLog getActivityLog();

    IFlowLogger getFlowLogger();

    default IDeliveringService getDeliveringService() {
        return null;
    }

    default IProcessStorage getProcessStorage() {
        return null;
    }

    default IRenderManager getRenderManager() {
        return null;
    }

    default IQaManager getQaManager() {
        return null;
    }
}
