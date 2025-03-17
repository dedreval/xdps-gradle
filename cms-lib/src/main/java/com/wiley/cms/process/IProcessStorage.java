package com.wiley.cms.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.wiley.cms.process.res.ProcessType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 */
public interface IProcessStorage extends IModelController {

    ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, int priority);

    ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, int priority, String owner);

    ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, String label,
                            int priority, ProcessState state, String owner);

    ProcessVO createProcess(ProcessHandler ph, ProcessType type, int priority);


    ProcessVO getProcess(int processId);

    ExternalProcess getExternalProcess(int processId);

    List<ExternalProcess> getExternalProcessChildren(int processId);

    List<ExternalProcess> getExternalProcessChildren(int processId, ProcessState state, int limit);

    int getProcessChildrenCount(int processId);

    /**
     * It returns a count of child processes which do not have states specified.
     * @param processId The parent process ID
     * @param states    The unacceptable process states
     * @return   a count of child processes which do not have the states specified.
     */
    int getProcessChildrenCountNotState(int processId, Collection<ProcessState> states);

    /**
     * Returns count of processes which have specified states and parent ID.
     * @param processId parent ID of the desired processes
     * @param states acceptable process states
     * @return count of the processes satisfying the specified parameters
     */
    int getProcessChildrenCount(int processId, Collection<ProcessState> states);

    void setProcessState(int processId, ProcessState state);

    void setProcessState(ProcessVO pvo, ProcessState state, String message);

    void setNextProcess(ExternalProcess pvo) throws ProcessException;

    void updateProcess(int processId, boolean freeToCompleted) throws ProcessException;

    ProcessVO findProcess(int processId) throws ProcessException;

    List<ProcessVO> findProcesses(String label);

    List<ProcessVO> findProcesses(int type);

    /**
     * Returns process list ordered by priority and creation date which label equal to specified value.
     * if no any process was found empty list will be returned.
     * @param label label of processes
     * @param states states of processes
     * @return list of processes
     */
    List<ProcessVO> findProcesses(String label, Collection<ProcessState> states);

    /**
     * Returns a list of processes which have the specified states and parent ID. The processes will be
     * ordered by creation date.
     * @param parentId parent ID of the desired processes
     * @param states acceptable process states
     * @return list of processes satisfying the specified parameters or empty list if such processes don't exist
     */
    List<ProcessVO> findProcesses(int parentId, Collection<ProcessState> states);

    void deleteProcess(int processId);


    int createProcessPart(int processId, String uri, String params, ProcessState state) throws ProcessException;

    int createProcessPart(int processId, String uri, String params) throws ProcessException;

    /**
     * Returns process part VO object created from ProcessPartEntity retrieved from DB by id.
     * @param processPartId id of process part
     * @return process part VO
     * @throws ProcessException if process part was not found
     */
    ProcessPartVO getProcessPart(int processPartId) throws ProcessException;

    ProcessPartVO startProcessPart(int processPartId) throws ProcessException;

    List<ProcessPartVO> getProcessParts(Collection<Integer> processPartIds);

    void setProcessPartState(int processPartId, ProcessState state, String msg);

    void setProcessPartsState(Collection<Integer> processPartIds, ProcessState state);

    void setProcessPartsState(Collection<Integer> processPartIds, ProcessState state, ProcessState... states);

    ArrayList<Integer> findNotStartedProcessParts(int processId, int maxCount);

    List<ProcessPartVO> getProcessParts(int processId);

    List<Integer> getProcessPartUIDs(int processId, int skip, int batchSize);

    /**
     * Returns a list of process part messages of specified process id.
     * @param processId process id
     * @param skip position of the first result
     * @param batchSize max count of returned records
     * @return list of process part messages
     */
    List<String> getProcessPartMessages(int processId, int skip, int batchSize);

    int getProcessPartCount(int processId);

    int getProcessPartCountInStates(int processId, List<ProcessState> states);

    int getUnFinishedProcessPartCount(int processId);
}
