package com.wiley.cms.cochrane.activitylog;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ActivityLogFactory extends AbstractBeanFactory<IActivityLogService> {
    private static final int ACCURACY = 50;
    private static final ActivityLogFactory INSTANCE = new ActivityLogFactory();

    private long allCounter = 0;
    private long curCounter = 0;

    private ActivityLogFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("ActivityLogService", IActivityLogService.class));
    }

    public static ActivityLogFactory getFactory() {
        return INSTANCE;
    }

    public long updateActivityLogCount() {
        return updateActivityLogCount(getInstance());
    }

    public long updateActivityLogCount(IActivityLogService service) {
        if (allCounter == 0 || allCounter + ACCURACY < curCounter) {
            allCounter = service.getActivityLogsCount();
            curCounter = allCounter;
        }
        return allCounter;
    }

    public void increaseActivityLogCount() {
        curCounter++;
    }
}
