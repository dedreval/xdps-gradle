package com.wiley.cms.cochrane.cmanager.render;

import java.util.Collection;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/26/2019
 */
public interface IRenderManager {

    void startFOPRendering(ClDbVO dbVO, String owner);

    void startFOPRendering(ClDbVO dbVO, Collection<IRecord> records, String owner);

    void startFOPRendering(DeliveryFileVO df, Collection<IRecord> records, String owner);

    void createFOPRendering(Collection<IRecord> records);

    void resetRendering(Collection<IRecord> records);

    void resetJatsRendering(Collection<IRecord> records);

    void updateRendering(int planId, Collection<Integer> recordIds, boolean success);

    void updateRendering(int planId, Collection<Integer> successfulRecordIds, Collection<Integer> failedRecordIds,
                         boolean lastStage);
}
