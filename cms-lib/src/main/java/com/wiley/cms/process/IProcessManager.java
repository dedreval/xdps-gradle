package com.wiley.cms.process;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.jms.Queue;

import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.07.13
 */
public interface IProcessManager {

    int HIGHEST_PRIORITY = 9;
    int HIGH_PRIORITY    = 7;
    int USUAL_PRIORITY   = 5;
    int LOW_PRIORITY     = 3;
    int LOWEST_PRIORITY  = 0;

    /**
     * Start a new process
     * @param processHandler  The processHandler
     * @param processType     The processType
     * @param owner           The process owner
     * @return
     */
    ProcessVO startProcess(ProcessHandler processHandler, ProcessType processType, String owner);

    /**
     * Start a new process
     * @param processHandler
     * @return the ID of the new process
     */
    ProcessVO startProcess(ProcessHandler processHandler, int priority);

    /**
     * Start a new process
     * @param creatorId The identifier of the process creator (it can be other process or some external entity)
     * @param processHandler
     * @return the ID of the new process
     */
    ProcessVO startProcess(int creatorId, ProcessHandler processHandler, int priority);

    ProcessVO startProcess(int creatorId, ProcessHandler processHandler, ProcessType processType, String owner,
                           List<ProcessPartVO> previousResults);

    /**
     * Start an existing process
     * @param handler
     * @param pvo
     */
    void startProcess(ProcessHandler handler, ProcessVO pvo);

    /**
     * Start an existing process
     * @param pvo
     */
    void startProcess(ProcessVO pvo);

    /**
     * Start an existing process
     * @param processId
     * @return    The process vo
     * @throws ProcessException if the process has been not found
     */
    ProcessVO startProcess(int processId) throws ProcessException;

    void endProcess(ProcessVO pvo, Throwable exception);

    void endProcess(ProcessVO pvo, ProcessState state);

    /**
     * @param  pvo      A partible process
     * @param  results  Process parts that have been processed before this call
     * @return ProcessPart ids are ready to be processed
     * @throws ProcessException
     */
    ArrayList<Integer> processNextPart(ProcessVO pvo, List<ProcessPartVO> results) throws ProcessException;

    // to keep an old approach
    void processNextPart(ProcessVO pvo, int nextPartSize) throws ProcessException;

    /**
     * Find existing process in the cache or database.
     * @param id  The process id
     * @return    The process vo
     * @throws ProcessException if the process is not found.
     */
    ProcessVO findProcess(int id) throws ProcessException;

    boolean existProcess(String label);

    String buildProcessReport(int processId, String message, boolean isOk);
    String buildProcessReport(int processId, boolean isOk, String repository, String... params) throws Exception;

    void deleteProcess(int processId);

    void deleteProcess(int processId, boolean withChildren);

    /**
     * Delete the process and finish its creator if need and if it's possible.
     * @param creator       The process creator.
     * @param processId     The identifier of the process to be deleted.
     * @param stopCreator   If TRUE, the creator should be stopped.
     * @return              The number of other child creator's processes. If it's 0, the creator process is stopped.
     *                                                                     If it' -1 the children haven't been counted.
     */
    int deleteProcess(ProcessVO creator, int processId, boolean stopCreator);

    /**
     * Starts a process asynchronously with adding into the task queue
     * @param pvo     The process
     * @param name    The name of task
     * @param lookup  The lookup name of process manager
     * @throws ProcessException
     */
    void asynchronousStart(ProcessVO pvo, String name, String lookup, Queue taskQueue) throws ProcessException;

    /**
     * Start a process linked to a task asynchronously
     * @param taskProcessId      The linked task identifier
     * @return  TRUE if the process is going to be started
     */
    boolean asynchronousStart(int taskProcessId, Queue taskQueue);

    /**
     * Creates a task linked to the process to run at once asynchronously
     * @param processId The process id
     * @param name      The name of task
     * @param lookup    The lookup name of process manager
     * @return          The created task relating to the process
     * @throws ProcessException
     */
    TaskVO createProcessTask(int processId, String name, String lookup) throws ProcessException;

    /**
     * Creates a delayed task linked to the process and add it to the tasks queue
     * @param processId    The process id
     * @param name         The name of task
     * @param lookup       The lookup name of process manager
     * @param delay        The delay (ms)
     * @param date         The date to mark the new created delayed task
     * @return             The created task relating to the process
     * @throws ProcessException
     */
    TaskVO createDelayedProcessTask(int processId, String name, String lookup, long delay, Date date)
        throws ProcessException;

    // to support an old approach
    void sendProcessPart(ProcessVO pvo, final ArrayList<Integer> ids, Queue partsQueue) throws ProcessException;

}
