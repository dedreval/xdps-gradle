package com.wiley.cms.cochrane.process;

 import java.util.Map;

 import javax.validation.constraints.NotNull;

 import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
public interface IRenderingManager extends IBaseRenderingManager {

    /**
     * Create rendering process for a delivery package (if it's not existed yet). Add new job to it.
     * @param previous  The process preceding the rendering process. It contains a parent process for the rendering.
     * @param df        The delivery package
     * @param records   The records to render
     * @return          The rendering process for the delivery package
     */
    ProcessVO startRendering(ExternalProcess previous, DeliveryFileVO df, Map<Integer, Record> records);

    void startNewRenderingStage(ProcessVO parent, DeliveryFileVO df, Map<Integer, Record> goodRecs, int[] planIds);

    /**
     * Add new job to existing rendering process for a delivery package
     * @param process  The existing rendering process
     * @param df       The delivery package
     * @param records  The records to render
     */
    void addRendering(ProcessVO process, DeliveryFileVO df, Map<Integer, Record> records);

    void acceptRenderingResults(int jobId, String results);

    int updateRecords(int processId, RndParsingResult result, DeliveryFileVO df,
            Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs, int[] planIds, boolean update);

    int updateFaultRecords(int processId, RndParsingResult result);

    /**
     * It checks if the whole rendering process of a delivery package is presumably completed although in some
     * cases ML3 conversion hasn't been finished yet.
     * If it is true, the delivery package status and process state in the memory cache will be updated.
     * @param process           The main rendering process - cached synchronising object
     * @param procId            The identifier of the process calling this check (can be the main process or its child).
     * @param df                The delivery package
     * @param completedCount    The count of completed record for the job (can be 0)
     * @param goodCount         The count of successfully completed record for the job (can be 0)
     * @return                  That the rendering process is presumably completed
     */
    boolean checkRenderingState(ProcessVO process, int procId, DeliveryFileVO df, int completedCount, int goodCount);

    /**
     * It checks that rendering process is really completed (in case ML3G conversion hasn't been finished yet),
     * performs some operations finalising rendering and delivery: delete a last rendering part,
     * set final statuses of a main process and a delivery packages if the package loading is completed.
     * @param creator           The main process - cached synchronising object
     * @param jobId             The identifier of the last part of the main process (can be 0)
     * @param df                The delivery package
     * @param planIds           The rendering plans
     * @param completed         The flag if the package loading is presumably completed
     */
    void finalizeRendering(ProcessVO creator, int jobId, DeliveryFileVO df, int[] planIds, boolean completed);

    void resumeRendering(int dfId) throws ProcessException;

    void convertToWml3gForFOPRendering(@NotNull String dbName, @NotNull DeliveryFileVO df,
        @NotNull Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs, boolean useExistingAssets);
}
