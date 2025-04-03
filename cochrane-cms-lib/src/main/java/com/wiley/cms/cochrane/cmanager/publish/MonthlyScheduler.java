package com.wiley.cms.cochrane.cmanager.publish;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskManager;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 1/21/2022
 */
public class MonthlyScheduler implements ITaskExecutor, IScheduledTask {
    private static final Logger LOG = Logger.getLogger(MonthlyScheduler.class);

    private static final Res<Property> START = CochraneCMSPropertyNames.getCentralMonthlyScheduler();
    private static final int ID = 3;

    @Override
    public String getScheduledTemplate() {
        return START.get().getValue();
    }

    @Override
    public boolean execute(TaskVO task) throws Exception {

        BaseType bt = BaseType.getCentral().get();
        ZonedDateTime now = CmsUtils.getCochraneDownloaderDateTime();
        int dbId = findDb(bt.getId(), now.getYear(), now.getMonth().getValue());
        if (dbId != 0) {
            CochraneCMSBeans.getPublishService().publishDbSync(dbId, PublishHelper.generatePublishList(
                    bt, dbId, Collections.singletonList(PubType.TYPE_DS), false, true, null));
        }
        return true;
    }

    public static String getTemplate() {
        return START.exist() ? START.get().getValue() : null;
    }

    public static Date getActualStartDate() {
        TaskVO task = TaskManager.Factory.getFactory().getInstance().findTask(ID);
        return task != null && task.isScheduled() ? task.getStartDate() : null;
    }

    private static int findDb(String dbName, int issueYear, int issueMonth) {
        try {
            return ResultStorageFactory.getFactory().getInstance().findOpenDb(issueYear, issueMonth, dbName);
        } catch (CmsException e) {
            return 0;
        }
    }
}
