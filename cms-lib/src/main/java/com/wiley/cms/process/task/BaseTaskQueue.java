package com.wiley.cms.process.task;

import javax.ejb.EJBException;
import javax.jms.BytesMessage;
import javax.jms.Message;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/8/2014
 */

public abstract class BaseTaskQueue {
    private static final Logger LOG = Logger.getLogger(BaseTaskQueue.class);

    protected void onMessage(Message message, ITaskManager tm) {

        int taskId = DbEntity.NOT_EXIST_ID;
        try {
            long current = System.currentTimeMillis();
            BytesMessage msg = JMSSender.getObjectParam(message, BytesMessage.class);
            taskId = msg.readInt();
            LOG.info(String.format("start executing task [%d]", taskId));

            TaskVO task = tm.findTask(taskId);
            if (task == null) {
                LOG.warn(String.format("cannot find task [%d], it might be removed for some reason", taskId));
                return;
            }
            // execute the task
            if (!task.getHandler().execute(task)) {
                throw new ProcessException(String.format("task %s is failed", task));
            }
            afterExecuting(task, tm);
            LOG.info(String.format("task %s is completed in %s", task, Now.buildTime(
                    System.currentTimeMillis() - current)));

        } catch (EJBException ee) {
            if (JMSSender.exceptionLimitHandle(message, ee.getMessage(), 2)) {
                LOG.error(ee.getMessage());
                tm.updateTask(taskId, ProcessState.FAILED, true, ee.getMessage());

            } else {
                LOG.warn(String.format("%s, a task will be redelivered", ee.getMessage()));
            }

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            tm.updateTask(taskId, ProcessState.FAILED, true, e.getMessage());
        }
    }

    private void afterExecuting(TaskVO task, ITaskManager tm) throws Exception {

        if (task.getState().isStopped()) {
            tm.stopCompletedTask(task.getId(), true);

        }  else if (task.isRepeatable()) {
            tm.restartTask(task.getId(), task.getNextScheduledDelay(), true);

        }  else {
            tm.deleteTask(task.getId());
        }
    }
}
