package com.wiley.cms.cochrane.cmanager.data.rendering;

import java.util.Collection;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RenderingVO;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IRenderingStorage {
    RenderingEntity getByRecordAndPlanDescription(int recordId, String plan);

    void unapproved(int dbId);

    List<RenderingEntity> findRenderingsByRecord(RecordEntity record);

    Long getRecordsCountByDFileAndPlan(String recordNames, String plans, DeliveryFileEntity dfEntity);

    Long getRecordsCountByDFileAndPlan(String plans, DeliveryFileEntity dfEntity);

    void deleteRecordsByDFileAndPlan(String recordNames, String plans, DeliveryFileEntity dfEntity);

    void deleteRecordsByIds(Collection<Integer> ids);

    void deleteRecordsByIds(Collection<Integer> ids, Integer planId);

    void deleteRecordsByDFileAndPlan(String plans, DeliveryFileEntity dfEntity);

    int updateRenderings(int dbId, String condition, int planId, boolean success);

    int updateRenderings(int planId, Collection<Integer> ids, boolean success);

    void createRenderingRecords(String recs, RenderingPlanEntity[] planEntitys, DeliveryFileEntity dfEntity);

    void createRenderingRecords(String recIds, int[] planIds);

    void createRenderingRecords(Collection<? extends IRecord> records, int planId);

    void createStartedJob(int jobId, RenderingPlan plan, DeliveryFileEntity dfEntity);

    @Deprecated
    RenderingPlanEntity[] getPlanEntities(boolean pdfCreate, boolean htmlCreate, boolean cdCreate, int countRnd);

    List<RenderingVO> findRenderingsByRecords(Collection<Integer> recordIds, boolean onlyCompleted);
}