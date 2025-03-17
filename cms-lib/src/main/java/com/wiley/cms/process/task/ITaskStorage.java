package com.wiley.cms.process.task;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.wiley.cms.process.ProcessState;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/10/2017
 */
public interface ITaskStorage {

    List<TaskVO> findTasks(String label);

    List<TaskVO> findTasksByPrefix(String prefix);

    TaskVO getTask(int taskId);

    TaskVO createTask(TaskVO task);

    void deleteTask(int taskId);

    void updateTasksForSent(Collection<Integer> ids);

    void updateTask(int taskId, TaskVO tvo, ProcessState state);

    void updateTask(int taskId, TaskVO tvo, String schedule, ProcessState state, String msg);

    void updateTask(int taskId, TaskVO tvo, ProcessState state, Boolean sent, String msg);

    void updateTaskOnRestart(int taskId, TaskVO tvo, String schedule, Boolean sent, String msg, Date startDate,
                             int runCount);

    void updateTaskOnSent(int taskId);
}
