package com.wiley.cms.process.task;

import java.util.HashMap;
import java.util.Map;

import org.quartz.Job;
import org.quartz.spi.JobFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/11/2015
 */
public class TaskJobFactory implements JobFactory {
    static final TaskJobFactory INSTANCE = new TaskJobFactory();

    private final Map<String, Job> jobs = new HashMap<String, Job>();

    public void addJob(Job job) {
        jobs.put(job.getClass().getName(), job);
    }

    @Override
    public Job newJob(org.quartz.spi.TriggerFiredBundle bundle) throws org.quartz.SchedulerException {
        return jobs.get(bundle.getJobDetail().getJobClass().getName());
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("downloaders registered:\n");
        for (String cl: jobs.keySet()) {
            Job job = jobs.get(cl);
            sb.append(cl).append(" -> job: ").append(job).append("\n");
        }
        return sb.toString();
    }
}
