package com.wiley.cms.cochrane.cmanager.data.df;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.StatusEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IDfStorage.class)
public class DfStorage implements IDfStorage {

    @PersistenceContext
    private EntityManager manager;

    public DeliveryFileVO getDfVO(int dfId) {
        DeliveryFileEntity entity = manager.find(DeliveryFileEntity.class, dfId);
        return new DeliveryFileVO(entity);
    }

    public void changeModifyStatus(int dfId, int statusId) {
        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
        StatusEntity status = manager.find(StatusEntity.class, statusId);
        df.setModifyStatus(status);
    }

    public void remove(int dfId) {
        manager.remove(manager.find(DeliveryFileEntity.class, dfId));
    }

    public void changeStatus(int dfId, int statusId) {
        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
        StatusEntity status = manager.find(StatusEntity.class, statusId);
        df.setStatus(status);
    }

    public void changeStatus(int dfId, int statusId, int[] planIds) {

        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
        if (df == null) {
            return;
        }
        StatusEntity status = manager.find(StatusEntity.class, statusId);
        df.setStatus(status);

        for (int planId: planIds) {

            if (RenderingPlan.isHtml(planId))  {
                df.setHtmlCompleted(true);
            } else if (RenderingPlan.isPdf(planId)) {
                df.setPdfCompleted(true);
            }
        }
    }

    public void mergeDB(DeliveryFileEntity dfEntity) {
        manager.merge(dfEntity);
        manager.flush();
    }

}