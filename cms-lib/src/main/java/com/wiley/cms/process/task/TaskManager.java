package com.wiley.cms.process.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;

import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.handler.ParamHandler;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 08.12.13
 */
@Singleton
@Startup
@Lock(LockType.READ)
@Local(ITaskManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class TaskManager extends JMXHolder implements com.wiley.cms.process.jmx.TaskManagerMXBean, ITaskManager {
    private static final Logger LOG = Logger.getLogger(TaskManager.class);
    private static final int UNTOUCHED_LIMIT = 5000;

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private QueueConnectionFactory connectionFactory;

    @EJB(beanName = "TaskStorage")
    private ITaskStorage ts;

    private Res<Property> taskPrefix;

    private Map<Integer, TaskVO> fastTasks = new HashMap<>();
    private BlockingQueue<TaskVO> activeTasks = createActiveTasks();

    private BlockingQueue<TaskVO> createActiveTasks() {

        return new PriorityBlockingQueue<>(1, new Comparator<TaskVO>() {
            public int compare(TaskVO o1, TaskVO o2) {
                return o1.getStartTime() > o2.getStartTime() ? 1 : -1;
            }
        });
    }

    /**
     * It saves supporting classes for task execution and call update().
     * @param classes  The supporting task classes.
     */
    public void registerSupportingClasses(Class... classes) {
        for (Class cl: classes) {
            ProcessHelper.addSupportingClass(cl);
        }
        update();
    }

    public String update() {

        taskPrefix = Property.get("cms.task.prefix");

        Factory.getFactory().reload();
        return init();
    }

    public String printState() {

        StringBuilder sb = new StringBuilder("active tasks are waiting to start: ").append(
                activeTasks.size()).append("\n");
        for (TaskVO tvo: activeTasks) {
            sb.append(tvo).append("\n");
        }

        sb.append("\nfast tasks: ").append(fastTasks.size()).append("\n");
        for (TaskVO tvo: fastTasks.values()) {
            sb.append(tvo).append("\n");
        }

        sb.append("\n").append(TaskJobFactory.INSTANCE.toString());
        sb.append("\ntask prefix is: ").append(taskPrefix);
        LOG.info(sb);

        return sb.toString();
    }

    public String execute(int taskId) {
        try {
            restartTask(taskId, 0, false);
            
        } catch (Throwable e) {
            return e.getMessage();
        }
        return "done";
    }

    @SuppressWarnings("unchecked")
    private String init() {

        Map<Integer, TaskVO> fastTasksTmp = new HashMap<>();
        BlockingQueue<TaskVO> activeTasksTmp = createActiveTasks();

        List<TaskVO> tasks = ts.findTasksByPrefix(taskPrefix.get().getValue());

        for (TaskVO tvo: tasks) {

            if (tvo.isFast()) {
                fastTasksTmp.put(tvo.getId(), tvo);
            }

            if (tvo.getState().isWaited()) {
                addTaskToQueue(tvo, activeTasksTmp, false);

            } else if (tvo.getState().isStarted() && !tvo.isSent()) {
                LOG.info(tvo + " - was not sent!");
                ts.updateTask(tvo.getId(), tvo, ProcessState.WAITED);
                addTaskToQueue(tvo, activeTasksTmp, false);

            } else {
                LOG.info(tvo);
            }
        }

        activeTasks = activeTasksTmp;
        fastTasks = fastTasksTmp;

        return printState();
    }

    public boolean sendTask(final TaskVO task, Queue taskQueue) {
        try {
            JMSSender.send(connectionFactory, taskQueue, new JMSSender.MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    BytesMessage msg = session.createBytesMessage();
                    msg.writeInt(task.getId());
                    return msg;
                }
            });
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            task.setState(ProcessState.FAILED);
            task.setMessage(e.getMessage());
            return false;
        }
        ts.updateTaskOnSent(task.getId());  // just to confirm sending
        return true;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public TaskVO addTask(String label, long delay, Date creationDate, String... params) {
        ParamHandler ph = new ParamHandler(params);
        return addTask(label, delay, null, creationDate, false, ph);
    }

    public TaskVO addTask(String label, String schedule, Date creationDate, boolean fast, String... params) {
        try {
            long delay = Now.getNextValidTimeAfter(schedule, new Date()).getTime() - System.currentTimeMillis();
            ParamHandler ph = new ParamHandler(params);
            return addTask(label, delay, schedule, creationDate, fast, ph);

        } catch (Exception e) {
            LOG.error(String.format("cannot create scheduled task: %s because %s ", label, e.getMessage()));
        }
        return null;
    }

    public TaskVO addTask(String label, long delay, Date creationDate, ProcessHandler handler) {
        ProcessHelper.addSupportingClass(handler.getClass());
        return addTask(label, delay, null, creationDate, false, handler);
    }

    public TaskVO stopTask(String name) {
        List<TaskVO> list = findTasks(name);
        if (list.isEmpty()) {
            LOG.warn(String.format("cannot find a task to stop: %s", name));
            return null;
        }
        TaskVO tvo = list.get(0);
        stopTask(tvo.getId());
        return tvo;
    }

    public void stopCompletedTask(int taskId, boolean onSent) {
        if (!onSent) {
            removeTaskFromQueue(taskId);
        }
        ts.updateTask(taskId, findFastTask(taskId), ProcessState.STOPPED, onSent ? Boolean.TRUE : null, "stopped");
    }

    public TaskVO restartTask(int taskId, long delay, boolean onSent) {
        return restartOrStop(taskId, delay, "", "restarted", onSent, false);
    }

    public TaskVO restartTask(int taskId, long delay, String schedule) {
        return restartOrStop(taskId, delay, schedule, "new schedule", false, false);
    }

    public TaskVO stopTask(int taskId) {
        return restartOrStop(taskId, 0, "", "", false, true);
    }

    private TaskVO restartOrStop(int taskId, long delay, String newSchedule, String msg, boolean onSent, boolean stop) {

        TaskVO tvo = ts.getTask(taskId);
        if (tvo == null) {
            return null;
        }

        TaskVO ret = tvo.isFast() ? checkFastTask(taskId) : null;
        if (delay >= 0) {
            ret = restartOrStop(ret, tvo, delay, newSchedule, msg, onSent, stop);

        } else {
            ts.updateTask(taskId, ret, ProcessState.FAILED, onSent ? Boolean.TRUE : null,
                    "cannot restart because delay is invalid");
            LOG.warn(String.format("cannot restart task[%d] because of delay is %d", taskId, delay));
        }

        return ret;
    }

    private TaskVO restartOrStop(TaskVO fast, TaskVO tvo, long delay, String newSchedule, String message,
                                 boolean onSent, boolean stop) {
        int id =  tvo.getId();
        ProcessState state = tvo.getState();
        boolean canRestart = onSent || !state.isStarted();
        boolean scheduled = TaskVO.isScheduled(tvo.getStartDate(), state);
        long currentTime = System.currentTimeMillis();
        long startTime = tvo.getStartTime();
        long newStartTime = currentTime + delay;
        boolean mayInQueue = startTime > currentTime;
        boolean mayStartSoon = mayInQueue && (startTime - currentTime) < UNTOUCHED_LIMIT;

        if (!scheduled) {
            tvo.setCountTries(0);

        } else if (canRestart && !mayStartSoon) {

            tvo.setCountTries(tvo.getCountTries() + 1);
            if (mayInQueue) {
                removeTaskFromQueue(id);
            }

        } else {
            LOG.warn(String.format(
                    "cannot restart task [%d] because it is executing or going to be started soon ...", id));
            return null;
        }

        TaskVO ret = (fast == null) ? tvo : fast;
        if (stop) {
            stopCompletedTask(id, onSent);

        } else {
            ts.updateTaskOnRestart(id, ret, newSchedule, onSent ? Boolean.TRUE : null, message, new Date(newStartTime),
                    tvo.getCountTries());
            addTaskToQueue(ret, activeTasks, false);
        }
        return ret;
    }

    public void deleteTask(int taskId) {
        removeTaskFromQueue(taskId);
        ts.deleteTask(taskId);
        fastTasks.remove(taskId);
    }

    public void updateTask(TaskVO task) {
        ts.updateTask(task.getId(), findFastTask(task.getId()), task.getSchedule(), task.getState(), task.getMessage());
    }

    public void updateTask(int taskId, ProcessState state, Boolean sent, String message) {
        ts.updateTask(taskId, findFastTask(taskId), state, sent, message);
    }

    public TaskVO findTask(int taskId) {
        TaskVO ret = findFastTask(taskId);
        return ret != null ? ret : findTask(taskId, true);
    }

    public TaskVO findTask(int taskId, boolean persistent) {

        if (persistent) {
            return ts.getTask(taskId);
        }

        TaskVO ret = null;
        for (TaskVO event: activeTasks) {
            if (event.getId() == taskId) {
                ret = event;
                break;
            }
        }
        return ret;
    }

    private TaskVO findFastTask(int taskId) {
        return fastTasks.get(taskId);
    }

    private TaskVO checkFastTask(int taskId) {
        TaskVO ret = fastTasks.get(taskId);
        if (ret == null) {
            LOG.error(String.format("cannot find a fast task [%d]", taskId));
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public List<TaskVO> findTasks(String label) {
        String prefix = getPrefix();
        String fullName = label.startsWith(prefix) ? label : getPrefix() + label;
        return ts.findTasks(fullName);
    }

    public List<TaskVO> findActiveTasks(String label) {
        String prefix = getPrefix();
        String fullName = label.startsWith(prefix) ? label : getPrefix() + label;
        List<TaskVO> ret = new ArrayList<>();
        for (TaskVO event: activeTasks) {
            if (fullName.equals(event.getLabel())) {
                ret.add(event);
            }
        }
        return ret;
    }

    public List<TaskVO> takeTasks() {

        List<TaskVO> ret = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        long current = System.currentTimeMillis();

        for (;;) {

            TaskVO tmp = activeTasks.peek();
            if (tmp == null || tmp.getStartTime() > current) {
                break;
            }

            TaskVO tvo = activeTasks.poll();
            if (tvo == null) {
                LOG.info(String.format("task %s is not found in the the tasks queue", tmp));
                break;
            }

            if (tvo.getStartTime() > current) {
                addTaskToQueue(tvo, activeTasks, true);
                LOG.info(String.format("task %s is moved back to the queue as it hasn't been ready to start", tvo));
                continue;
            }

            long delay = tvo.delayExecution(current);
            if (delay != 0) {
                addTaskToQueue(tvo, activeTasks, true);
                if (delay != ITaskExecutor.RESCHEDULE) {
                    LOG.debug(String.format("task %s has been delayed in %d and moved back to the queue", tvo, delay));
                }
                continue;
            }

            LOG.info(String.format("task %s is going to be executed ...", tvo));
            ret.add(tvo);

            if (tvo.existsInDb()) {
                ids.add(tvo.getId());
                tvo.setState(ProcessState.STARTED);
            }
        }
        if (!ids.isEmpty()) {
            ts.updateTasksForSent(ids);
        }
        return ret;
    }

    private TaskVO addTask(String label, long delay, String schedule, Date creationDate, boolean fast,
                           ProcessHandler handler) {
        String fullName = getPrefix() + label;

        TaskVO ret;
        if (delay >= 0) {
            ret = createTask(new TaskVO(fullName, new Date(System.currentTimeMillis() + delay), ProcessState.WAITED,
                    schedule, creationDate, handler), fast);
            addTaskToQueue(ret, activeTasks, false);

        } else {
            ret = createTask(new TaskVO(fullName, null, ProcessState.NONE, null, creationDate, handler), fast);
        }

        //ret.setExecutor(executor);
        return ret;
    }

    //private ITaskExecutor getTaskExecutor(ParamHandler ph) throws Exception {
    //    ITaskExecutor executor = ph.createExecutor();
    //    ProcessHelper.addSupportingClass(executor.getClass());
    //    return executor;
    //}

    private void addTaskToQueue(TaskVO evo, BlockingQueue<TaskVO> queue, boolean silently) {
        queue.offer(evo);
        if (!silently) {
            LOG.info(String.format("task %s is added into the tasks queue", evo));
        }
    }

    private void removeTaskFromQueue(int taskId) {
        Iterator<TaskVO> it = activeTasks.iterator();
        while (it.hasNext()) {
            TaskVO tvo = it.next();
            if (tvo.getId() == taskId) {
                LOG.info(String.format("task %s is removed from the tasks queue", tvo));
                it.remove();
            }
        }
    }

    private TaskVO createTask(TaskVO task, boolean fast) {

        task.setFast(fast);
        TaskVO ret = ts.createTask(task);
        if (fast) {
            fastTasks.put(ret.getId(), ret);
        }
        return ret;
    }

    private String getPrefix() {
        return Res.valid(taskPrefix) ? taskPrefix.get().getValue() : "";
    }

    @Override
    public String toString() {
        return printState();
    }

    /**
     * Just factory
     */
    public static class Factory extends AbstractBeanFactory<ITaskManager> {
        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super("CMS", "TaskManager", ITaskManager.class);
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}
