package com.wiley.cms.cochrane.process;

import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.process.handler.Wml3gConversionHandler;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.res.ProcessType;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 01.09.2014
 * Provides ARI for performing WML21(USW) to WML3G conversion. This conversion will be performed as a
 * background process.
 */
public interface IWml3gConversionManager extends IProcessManager {

    String PROC_LABEL = "Wml3g conversion";

    /**
     * Creates a WML3G conversion process. The process will be in ProcessState.WAITED state.
     * @param parentId parent process id which one controls the process execution
     * @param dbId DatabaseEntity id
     * @param recIds EntireDBEntities ids
     * @param previous the flag specifies whether previous versions have to be processed
     * @param logName login name of the user initiated the action. If null system login name will be used.
     * @return The process created
     * @throws ProcessException if something went wrong during the process creation
     */
    ProcessVO createProcess(int parentId,
                       int dbId,
                       List<Integer> recIds,
                       boolean previous,
                       String logName) throws ProcessException;

    /**
     * Executes WML21(USW) to WML3G conversion for Issue DB. The conversion will be performed for all articles
     * related to defined Issue.
     * @param issueVO value object which represents Issue from which the conversion is executing
     * @param dbId DB id related to Issue from which the conversion is executing
     * @param logName login name of user who executed the conversion
     */
    void startConversion(IssueVO issueVO, int dbId, String logName);

    /**
     * Executes WML21(USW) to WML3G conversion for Entire DB. The conversion will be performed for all articles
     * related to defined Entire.
     * @param dbId DB id which records going to be converted
     * @param logName login name of user who executed the conversion
     */
    void startConversion(int dbId, String logName);

    /**
     * Executes WML21(USW) to WML3G conversion of specified record for Entire DB including previous version.
     * @param dbId DB id which records going to be converted
     * @param recIds ids of records from Entire Db
     * @param previous defines should the conversion be performed for previous versions
     * @param logName login name of user who executed the conversion
     */
    void startConversion(int dbId, List<Integer> recIds, boolean previous, String logName);

    /**
     * Returns a string which contains following: total amount of processed records, amount of records processed
     * successfully, list of records names excluded from processing and additional comments like error messages.
     * Each portion of the statistics data is separated by a hash. The string returned by this method is used
     * for save statistic information about sending results in ProcessPartEntity table.
     * @param procCnt total amount of converted records
     * @param procSuccessCnt amount of records converted with errors
     * @param excludRecs list of records names excluded from processing
     * @param comments comments to the processing results
     * @return a string contains statistic information about processing results
     */
    String getStatisticMessage(int procCnt, int procSuccessCnt, List<String> excludRecs, String comments);

    /**
     * Only creates WML21(USW) to WML3G conversion of specified record for Entire DB without starting.
     * @param type a process template
     * @param dbId       DB id which records going to be converted
     * @param dbName     DB name which records going to be converted
     * @param recs       ids of records from Entire Db
     * @param previous   defines should the conversion be performed for previous versions
     * @param logName    login name of user who executed the conversion
     * @param nextProcess     next process id
     * @return         Returns a newly created process
     */
    ProcessVO prepareConversion(ProcessType type, int dbId, String dbName, List<Integer> recs, boolean previous,
                                String logName, ProcessVO nextProcess);

    /**
     * it starts process in the same onStart() does
     * @param wh     The process handker
     * @param pvo    The process
     * @throws ProcessException
     */
    void startConversion(Wml3gConversionHandler wh, ProcessVO pvo) throws ProcessException;
}
