package com.wiley.cms.cochrane.cmanager.data.df;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IDfStorage {
    DeliveryFileVO getDfVO(int dfId);

    void changeModifyStatus(int dfId, int statusId);

    void remove(int dfId);

    void changeStatus(int dfId, int statusId);

    void changeStatus(int dfId, int statusId, int[] planIds);

    void mergeDB(DeliveryFileEntity dfEntity);
}