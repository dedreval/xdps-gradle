package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(ISpecRenderingStorage.class)
public class SpecRenderingStorage implements ISpecRenderingStorage {
    private static final Logger LOG = Logger.getLogger(SpecRenderingStorage.class);

    @PersistenceContext
    private EntityManager manager;

    public int create(SpecRenderingVO vo) {
        SpecRenderingEntity entity = new SpecRenderingEntity();
        entity.fillIn(vo);
        ClDbEntity db = manager.find(ClDbEntity.class, vo.getDbId());
        entity.setDb(db);
        manager.persist(entity);
        return entity.getId();
    }

    public SpecRenderingEntity findSpecRendering(int id) {
        return manager.find(SpecRenderingEntity.class, id);
    }

    public SpecRenderingVO findSpecRenderingVO(int id) {
        SpecRenderingEntity entity = findSpecRendering(id);
        return entity == null ? null : new SpecRenderingVO(findSpecRendering(id));
    }

    public void merge(SpecRenderingEntity entity) {
        manager.merge(entity);
        manager.flush();
    }

    public void mergeVO(SpecRenderingVO vo) {
        SpecRenderingEntity entity = manager.find(SpecRenderingEntity.class, vo.getId());

        ClDbEntity db = manager.find(ClDbEntity.class, vo.getDbId());
        if (db == null) {
            return;
        }
        entity.setDb(db);

        entity.setDate(vo.getDate());
        entity.setCompleted(vo.isCompleted());
        entity.setSuccessful(vo.isSuccessful());

        List<SpecRenderingFileVO> files = vo.getFiles();
        if (files != null && files.size() > 0) {
            for (SpecRenderingFileVO fileVO : files) {
                entity.addFile(fileVO);
            }
        }

        //merge(entity);
    }

    public void setCompleted(int id, boolean completed, boolean successful) {
        SpecRenderingEntity entity = manager.find(SpecRenderingEntity.class, id);
        entity.setCompleted(completed);
        entity.setSuccessful(successful);
        manager.merge(entity);
        manager.flush();
    }


    public void remove(int id) {
        SpecRenderingEntity entity = manager.find(SpecRenderingEntity.class, id);
        manager.remove(entity);
    }

    @SuppressWarnings("unchecked")
    public List<SpecRenderingEntity> getSpecRenderingList(int dbId) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        return manager
                .createQuery("select e from SpecRenderingEntity e where e.db=:db order by e.date desc")
                .setParameter("db", db)
                .getResultList();
    }

    public List<SpecRenderingVO> getSpecRenderingVOList(int dbId) {
        List<SpecRenderingEntity> entityList = getSpecRenderingList(dbId);
        List<SpecRenderingVO> voList = new ArrayList<SpecRenderingVO>();
        for (SpecRenderingEntity entity : entityList) {
            voList.add(new SpecRenderingVO(entity));
        }
        return voList;
    }

    public String getSpecRndFilePathLocalByDbIdAndFileName(int dbId, String fileName) {
        String queryStr = " SELECT sf.filePathLocal "
                + " FROM SpecRenderingFileEntity sf "
                + " WHERE sf.filePathLocal LIKE '%crglist.html' "
                + "AND specRendering_id = (SELECT id "
                + " FROM SpecRenderingEntity"
                + " WHERE db_id = :dbId)";


        return (String) manager.createQuery(queryStr)
                .setParameter("dbId", dbId).getSingleResult();
    }
}
