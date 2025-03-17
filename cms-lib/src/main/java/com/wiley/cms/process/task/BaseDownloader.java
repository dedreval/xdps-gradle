package com.wiley.cms.process.task;

import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.10.12
 */
public abstract class BaseDownloader implements IDownloader, Job {
    private static final Logger LOG = Logger.getLogger(BaseDownloader.class);

    private static final String BASE_GROUP_NAME = "base";

    private String schedule;

    public BaseDownloader() {
        TaskJobFactory.INSTANCE.addJob(this); // register downloader
    }

    protected abstract void download();

    protected abstract boolean canDownload();

    public void execute(JobExecutionContext jobExecutionContext) {
        if (canDownload()) {
            download();
        }
    }

    protected final void startDownloader(String name, String newSchedule, boolean single, boolean recover) {

        if (!needUpdate(newSchedule)) {
            return;
        }

        stopDownloader(name);

        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            if (single) {
                scheduler.setJobFactory(TaskJobFactory.INSTANCE);
            }
            JobDetail job = new JobDetail(name, BASE_GROUP_NAME, getClass(), false, false, recover);
            CronTrigger cronTrigger = new CronTrigger(name, BASE_GROUP_NAME, job.getName(), job.getGroup());
            cronTrigger.setCronExpression(newSchedule);

            scheduler.start();
            LOG.info("%s for job %s started, new value is [%s] %s",
                    scheduler.getSchedulerName(), name, newSchedule, scheduler.scheduleJob(job, cronTrigger));
            schedule = newSchedule;

        } catch (Exception e) {
            LOG.error("error starting " + name, e);
        }
    }

    protected final void stopDownloader(String name) {
        schedule = null;
        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            if (scheduler.deleteJob(name, BASE_GROUP_NAME)) {
                LOG.info("%s for job %s stopped", scheduler.getSchedulerName(), name);
            }
        } catch (Exception e) {
            LOG.error("error stopping " + name, e);
        }
    }

    private boolean needUpdate(String newSchedule) {
        return newSchedule != null && (schedule == null || !schedule.equals(newSchedule));
    }
}
