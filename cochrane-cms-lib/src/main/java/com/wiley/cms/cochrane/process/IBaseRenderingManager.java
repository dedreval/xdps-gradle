package com.wiley.cms.cochrane.process;

import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
public interface IBaseRenderingManager extends IProcessManager {

    /**
     * Start (or resume) the job (sub-process) of the existing rendering process
     * @param renderingJobId   The identifier of the job
     * @return  TRUE if the start is performed successfully
     */
    boolean startRendering(int renderingJobId);

    /**
     * Check if the specific record is processing with another process
     * @param recName    The name of the record
     * @param basePath   A base path of the record in the file system
     * @param startTime  The starting time of the calling process
     * @return  TRUE if the record is processing or has been just processed with another process
     */
    boolean checkRecordOnProcessed(String recName, String basePath, long startTime);

    /**
     * It performs some operations finalising process: delete a sub-process, check if this part is the latest.
     * If so it calls endProcess() to complete the main process.
     * In another case it will try to start a one of remained not started sub-processes.
     * @param creator    The main process - cached synchronising object
     * @param jobId      The identifier of the sub-process of the main process (can be 0)
     * @return  TRUE if the main process has been completed
     */
    boolean finalizeRendering(ProcessVO creator, int jobId);
}
