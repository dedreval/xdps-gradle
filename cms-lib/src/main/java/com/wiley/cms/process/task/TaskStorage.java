package com.wiley.cms.process.task;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.entity.AbstractModelController;
import com.wiley.cms.process.entity.TaskEntity;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/10/2017
 */
@Stateless
@Local(ITaskStorage.class)
public class TaskStorage extends AbstractModelController implements ITaskStorage {
    private static final Logger LOG = Logger.getLogger(TaskStorage.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    protected EntityManager getManager() {
        return em;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<TaskVO> findTasks(String label) {
        return TaskEntity.queryTask(label, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<TaskVO> findTasksByPrefix(String prefix) {
        return (List<TaskVO>) TaskEntity.queryTaskByPrefix(prefix, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public TaskVO getTask(int taskId) {
        TaskEntity ee = findTaskEntity(taskId);
        return (ee != null) ? new TaskVO(ee) : null;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public TaskVO createTask(TaskVO task) {
        TaskEntity ee = new TaskEntity(task);
        em.persist(ee);
        task.setId(ee.getId());
        return task;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteTask(int taskId) {
        TaskEntity ee = findTaskEntity(taskId);
        if (ee != null) {
            em.remove(ee);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTasksForSent(Collection<Integer> ids) {
        if (!ids.isEmpty()) {
            TaskEntity.queryUpdateTaskStateOnSent(ids, ProcessState.STARTED, true, em).executeUpdate();
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTaskOnRestart(int taskId, TaskVO tvo, String schedule, Boolean sent, String msg, Date startDate,
                                    int runCount) {
        TaskEntity ee = findTaskEntity(taskId);
        if (ee != null) {
            if (!"".equals(schedule)) {
                ee.setSchedule(schedule);
            }
            ee.setStartDate(startDate);
            ee.setRunCount(runCount);
            updateTask(tvo, ee, ProcessState.WAITED, sent, msg);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTask(int taskId, TaskVO tvo, String schedule, ProcessState state, String msg) {
        TaskEntity ee = findTaskEntity(taskId);
        if (ee != null) {
            if (!"".equals(schedule)) {
                ee.setSchedule(schedule);
            }
            updateTask(tvo, ee, state, null, msg);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTask(int taskId, TaskVO tvo, ProcessState state) {
        TaskEntity ee = findTaskEntity(taskId);
        if (ee != null) {
            updateTask(tvo, ee, state, null);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTask(int taskId, TaskVO tvo, ProcessState state, Boolean sent, String msg) {
        TaskEntity ee = findTaskEntity(taskId);
        if (ee != null) {
            updateTask(tvo, ee, state, sent, msg);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateTaskOnSent(int taskId) {
        TaskEntity.queryUpdateTaskOnSent(taskId, true, em).executeUpdate();
    }

    private void updateTask(TaskVO tvo, TaskEntity ee, ProcessState state, Boolean sent, String msg) {
        ee.setLastDate(new Date());
        ee.setMessage(msg);

        updateTask(tvo, ee, state, sent);
    }

    private void updateTask(TaskVO tvo, TaskEntity ee, ProcessState state, Boolean sent) {
        ee.setState(state);
        if (sent != null) {
            ee.setSent(sent);
        }
        em.merge(ee);

        if (tvo != null) {
            tvo.updateFrom(ee);
        }
    }

    private TaskEntity findTaskEntity(int taskId) {
        TaskEntity ee = em.find(TaskEntity.class, taskId);
        if (ee == null) {
            LOG.warn(String.format("cannot find task [%d]", taskId));
        }
        return ee;
    }
}
