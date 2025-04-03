package com.wiley.cms.cochrane.cmanager.export.data;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.process.ModelController;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IExportStorage.class)
public class ExportStorage extends ModelController implements IExportStorage {

    public int create(ExportVO vo) {
        EntityManager manager = getManager();

        ClDbEntity clDbEntity = (vo.getClDbId() != null
                ? manager.find(ClDbEntity.class, vo.getClDbId())
                : null);
        DatabaseEntity dbEntity = AbstractManager.getResultStorage().getDatabaseEntity(vo.getDbName());

        ExportEntity entity = new ExportEntity();
        entity.fillIn(vo);
        entity.setClDb(clDbEntity);
        entity.setDb(dbEntity);
        manager.persist(entity);

        return entity.getId();
    }

    public void setCompleted(int id, int state, String filePath) {
        ExportEntity entity = find(ExportEntity.class, id);
        entity.setState(state);
        entity.setFilePath(filePath);

        flush(entity);
    }

    public void remove(int id) {
        delete(ExportEntity.class, id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ExportVO> getExportVOList(String dbName, Integer clDbId, String user, boolean isAdmin) {
        return getExportVOList(dbName, clDbId, user, isAdmin, 0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getExportVOListSize(String dbName, Integer clDbId, String user, boolean isAdmin) {
        EntityManager manager = getManager();
        Query ret;
        if (clDbId == null) {
            ret = isAdmin ? ExportEntity.qCountByDbNameAndEmptyClDbId(dbName, manager)
                    : ExportEntity.qCountByDbNameAndUserAndEmptyClDbId(dbName, user, manager);
        } else {
            ret = isAdmin ? ExportEntity.qCountByClDbId(clDbId, manager)
                    : ExportEntity.qCountByClDbIdAndUser(clDbId, user, manager);
        }
        return getSingleResultIntValue(ret);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ExportVO> getExportVOList(String dbName, Integer clDbId, String user, boolean isAdmin, int limit) {
        List<ExportEntity> entityList;
        EntityManager manager = getManager();
        if (clDbId == null) {
            entityList = (isAdmin
                    ? ExportEntity.qEntityByDbNameAndEmptyClDbId(dbName, limit, manager).getResultList()
                    : ExportEntity.qEntityByDbNameAndUserAndEmptyClDbId(dbName, user, limit, manager).getResultList());
        } else {
            entityList = (isAdmin
                    ? ExportEntity.qEntityByClDbId(clDbId, limit, manager).getResultList()
                    : ExportEntity.qEntityByClDbIdAndUser(clDbId, user, limit, manager).getResultList());
        }
        List<ExportVO> voList = new ArrayList<ExportVO>(entityList.size());

        for (ExportEntity entity : entityList) {
            voList.add(new ExportVO(entity));
        }

        return voList;
    }
}
