package com.wiley.cms.process.task;

import java.util.List;

import javax.annotation.PreDestroy;
import javax.jms.Queue;

import org.quartz.StatefulJob;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.07.12
 */
public abstract class BaseTaskDownloader extends BaseDownloader implements StatefulJob {
    private static final Logger LOG = Logger.getLogger(BaseTaskDownloader.class);

    private ITaskManager tm = TaskManager.Factory.getFactory().getInstance();

    protected abstract Queue getTaskQueue();

    protected abstract String getSchedule();

    @Override
    public void update() {
        if (canDownload()) {
            startDownloader(getClass().getSimpleName(), getSchedule(), true, false);
        } else {
            stop();
        }
    }

    protected ITaskManager getManager() {
        return tm;
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    @Override
    protected void download() {
        downloadTasks();
    }

    private void downloadTasks()  {

        List<TaskVO> tasks = tm.takeTasks();

        if (tasks.isEmpty()) {
            return;
        }

        Queue taskQueue = getTaskQueue();

        LOG.info("tasks are downloaded: " + tasks.size());
        for (TaskVO task: tasks) {

            if (!tm.sendTask(task, taskQueue)) {
                tm.updateTask(task);
            }
        }
    }
}
