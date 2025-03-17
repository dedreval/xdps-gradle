package com.wiley.cms.process.task;

import java.util.Date;
import java.util.List;

import javax.jms.Queue;

import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessState;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/8/2014
 */
public interface ITaskManager {

    /**
     * Try to start a task instantly and asynchronously via sending it to TaskQueue.
     * @param task
     * @return If FALSE, check Task status and message attributes for the reason of failure
     */
    boolean sendTask(TaskVO task, Queue taskQueue);

    List<TaskVO> takeTasks();

    /**
     * Add a new task and put it into the queue of scheduled tasks.
     * @param label         The task name.
     * @param delay         If -1, the task will be created, but won't be scheduled as well as put to the task queue.
     * @param creationDate  The date of task creation. If it's NULL, the current date will be set.
     * @param params  The parameters for ParamHandler. The first parameter must be the class name to execute this task.
     * @return        The created task or NULL if the task couldn't be created.
     */
    TaskVO addTask(String label, long delay, Date creationDate, String... params);

    /**
     * Add a new task and put it into the queue of scheduled tasks.
     * @param label         The task name.
     * @param schedule      The schedule of the task meaning that the task can be restarted next time.
     * @param creationDate  The date of task creation. If it's NULL, the current date will be set.
     * @param params  The parameters for ParamHandler. The first parameter must be the class name to execute this task.
     * @return        The created task or NULL if the task couldn't be created.
     */
    TaskVO addTask(String label, String schedule, Date creationDate, boolean fast, String... params);

    /**
     * Add a new task and put it into the queue of scheduled tasks.
     * @param label          The task name.
     * @param delay          If -1 the task will be created, but won't be scheduled as well as put to the task queue.
     * @param creationDate   The date of task creation. If it's NULL, the current date will be set.
     * @param ph             The task handler. It can be any ProcessHandler with realized execute(TaskVO task).
     * @return               The created task or NULL if the task couldn't be created.
     */
    TaskVO addTask(String label, long delay, Date creationDate, ProcessHandler ph);

    TaskVO restartTask(int taskId, long delay, String schedule);

    TaskVO restartTask(int taskId, long delay, boolean onSent);

    TaskVO stopTask(int taskId);

    TaskVO stopTask(String name);

    void stopCompletedTask(int taskId, boolean onSent);

    TaskVO findTask(int taskId);

    TaskVO findTask(int taskId, boolean persistent);

    List<TaskVO> findTasks(String label);

    List<TaskVO> findActiveTasks(String label);

    void updateTask(int taskId, ProcessState state, Boolean sent, String message);

    void updateTask(TaskVO task);

    void deleteTask(int taskId);

    /**
     * Reload all the tasks for supported modules.
     */
    String update();

    String printState();

    /**
     * It saves supporting classes for task execution and call update().
     * @param classes  The supporting task classes.
     */
    void registerSupportingClasses(Class... classes);
}
