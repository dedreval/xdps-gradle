package com.wiley.cms.cochrane.cmanager;

import java.util.Collection;
import java.util.HashMap;

import com.wiley.cms.cochrane.cmanager.data.DelayedThread;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 12-Feb-2007
 */
public interface IRenderingService {
    int PLAN_QAS_ID = 0;

    int updateRenderings(int dbId, String records, int plan, boolean success);

    @Deprecated
    boolean startRendering(int deliveryFileId, String goodRecList) throws Exception;

    boolean startRendering(Object[] urisAndRawDataInfo, String dbName, int dfId, String message, RenderingPlan plan)
        throws Exception;

    // boolean startRendering(Record record) throws NamingException, IOException, URISyntaxException;

    @Deprecated
    int setRecordCompleted(int dbId, String s, boolean success, int numberRndPlans, int number);

    @Deprecated
    void setDeliveryFileStatusAfterRnd(Integer dfId, RenderingPlan plan) throws DeliveryPackageException;

    void setDeliveryFileModifyStatusAfterRnd(Integer dfId, int firstRecordId) throws DeliveryPackageException;

    long getRecordCountByDf(Integer id);

    boolean isQasCompleted(Integer id);

    HashMap<String, Integer> getPlanNameToId();

    void writeResultToDb(int jobId, String result);

    DelayedThread getRndNextJob(int jobId);

    void setAboutResources(int dbId);

    @Deprecated
    int getPlanId(String plan);

    void deleteRendering(Collection<Integer> ids);
}
