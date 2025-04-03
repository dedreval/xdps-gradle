package com.wiley.cms.cochrane.process.task;

import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.process.task.BaseTaskDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.07.12
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class TaskDownloader extends BaseTaskDownloader implements IDownloader {

    private final Res<Property> canDownload = TaskVO.getCanDownloadProperty();
    private final Res<Property> schedule = TaskVO.getScheduleProperty();

    @Override
    protected boolean canDownload() {
        return canDownload.get().asBoolean();
    }

    @Override
    protected String getSchedule() {
        return schedule.get().getValue();
    }

    @Override
    protected Queue getTaskQueue() {
        return CochraneCMSBeans.getQueueProvider().getTaskQueue();
    }
}
