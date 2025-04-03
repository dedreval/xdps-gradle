package com.wiley.cms.cochrane.cmanager.ebch.process;

import java.util.Collection;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.ebch.IBasketHolder;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.04.13
 *
 * @param <T> Parameters for a specified tasks
 */
public abstract class GenProcessor<T extends GenProcessorParameters> implements Runnable {

    protected T params;
    protected Map<String, String[]> requestParametersMap;
    private Integer[] recordIds = null;

    public GenProcessor(T params) {
        this.params = params;
    }

    public GenProcessor(T params, Map<String, String[]> requestParametersMap) throws CmsException {
        this(params);
        this.requestParametersMap = requestParametersMap;
    }

    public String getUserName() {
        return params == null ? null : params.getUserName();
    }

    protected boolean hasRecordsIds() {
        return recordIds != null && recordIds.length > 0;
    }

    protected Integer[] getRecordsIds() {
        return recordIds;
    }

    protected  String getDbTitle() {
        return requestParametersMap.get("dbname")[0];
    }

    protected boolean isIncludePrevious() {
        return requestParametersMap.containsKey("includePrevious");
    }

    public void setRecordIds(IBasketHolder holder) {
        Collection<Integer> ids = holder.getProcessBasketContent();
        if (ids != null) {
            recordIds = holder.getProcessBasketContent().toArray(new Integer[ids.size()]);
        }
    }

    protected void logActivity(LogEntity.EntityLevel lvl, int event, int entityId, String entityName) {
        ActivityLogFactory.getFactory().getInstance().info(lvl, event, entityId, entityName, params.getUserName(), "");
    }

    protected void logActivity(LogEntity.EntityLevel lvl, int event, int entityId, String entityName, String comments) {
        ActivityLogFactory.getFactory().getInstance().info(lvl, event, entityId, entityName, params.getUserName(),
                                                           comments);
    }
}
