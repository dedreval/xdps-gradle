package com.wiley.cms.cochrane.cmanager.data;

import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.ICochranePageManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.cms.process.task.UpTimes;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/8/2017
 */
public class StatsUpdater implements ITaskExecutor, IScheduledTask {

    private static final Res<Property> SCHEDULE = Property.get("cochrane.stats.update.schedule");

    private static final Map<String, DbPage> DB_NAMES_TO_STATS = new HashMap<>();

    private static final UpTimes UP_TIMES = new UpTimes();

    public static DbPage getStats(String dbName) {

        DbPage ret = DB_NAMES_TO_STATS.get(dbName);
        if (ret == null) {
            ret = new DbPage(BaseType.find(dbName).get().getDbId());
            DB_NAMES_TO_STATS.put(dbName, ret);
        }
        return ret;
    }

    public static void onUpdate(String dbName) {

        DbPage ret = getStats(dbName);
        long upTime = System.currentTimeMillis();
        ret.upTimes().setUpTime(upTime);
        UP_TIMES.setUpTime(upTime);
    }

    public boolean execute(TaskVO task) throws Exception {

        if (!UP_TIMES.wasInitialized()) {
            BaseType dbType = BaseType.getCDSR().get();
            DB_NAMES_TO_STATS.put(dbType.getId(), new DbPage(dbType.getDbId()));

            dbType = BaseType.getCentral().get();
            DB_NAMES_TO_STATS.put(dbType.getId(), new DbPage(dbType.getDbId()));

            dbType = BaseType.getEditorial().get();
            DB_NAMES_TO_STATS.put(dbType.getId(), new DbPage(dbType.getDbId()));

            dbType = BaseType.getCCA().get();
            DB_NAMES_TO_STATS.put(dbType.getId(), new DbPage(dbType.getDbId()));
        }

        long currentTime = System.currentTimeMillis();

        ICochranePageManager manager = CochraneCMSBeans.getPageManager();
        for (DbPage page: DB_NAMES_TO_STATS.values()) {
            UpTimes upTimes = page.upTimes();
            if (upTimes.wasSetDown()) {
                continue;
            }
            manager.updateDatabaseStats(page);
            upTimes.setLastControlTime(currentTime);
        }

        UP_TIMES.setLastControlTime(currentTime);

        ActivityLogFactory.getFactory().updateActivityLogCount();

        updateSchedule(task);
        return true;
    }

    @Override
    public String getScheduledTemplate() {
        return SCHEDULE.get().getValue();
    }

    @Override
    public long canDelay() {
        return UP_TIMES.wasSetDown() ? RESCHEDULE : 0;
    }
}
