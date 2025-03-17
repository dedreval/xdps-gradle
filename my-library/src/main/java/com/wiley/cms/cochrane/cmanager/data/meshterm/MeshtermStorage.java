package com.wiley.cms.cochrane.cmanager.data.meshterm;

import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.2009
 */

@Stateless
@Local(IMeshtermStorage.class)
public class MeshtermStorage implements IMeshtermStorage {
    private static final String RECORD_NAME_PARAM = "recordName";
    private static final String MAJOR_TOPIC_PARAM = "majorTopic";
    private static final String IDS_PARAM = "ids";
    private static final String DB_ID_PARAM = "dbId";
    @PersistenceContext
    private EntityManager manager;

    public void deleteMeshtermRecord(Integer meshtermRecordId) {
        manager.remove(manager.find(MeshtermRecordEntity.class, meshtermRecordId));
    }

    public void setIssue(String recordName, Integer issue) {
        MeshtermRecordDatesEntity meshtermRecordDatesEntity = manager.find(MeshtermRecordDatesEntity.class, recordName);
        meshtermRecordDatesEntity.setDate(issue);
        manager.merge(meshtermRecordDatesEntity);
    }

    public int createMeshtermRecord(String recordName, Integer descriptorId, Integer qualifierId) {
        MeshtermRecordEntity meshtermRecordEntity = new MeshtermRecordEntity();
        meshtermRecordEntity.setRecordName(recordName);
        meshtermRecordEntity.setDescriptorEntity(manager.find(DescriptorEntity.class, descriptorId));
        meshtermRecordEntity.setQualifierEntity(manager.find(QualifierEntity.class, qualifierId));
        manager.persist(meshtermRecordEntity);
        return meshtermRecordEntity.getId();
    }

    public int findMeshtermRecordId(String recordName, Integer descriptorId, Integer qualifierId) {
        List<Integer> result = manager.createNamedQuery("findMeshtermRecordId")
                .setParameter(RECORD_NAME_PARAM, recordName)
                .setParameter("descriptorId", descriptorId).setParameter("qualifierId", qualifierId)
                .getResultList();
        if (result.size() != 1) {
            return -1;
        }
        return result.get(0);
    }

    public int createQualifier(String qualifier, Boolean majorTopic) {
        QualifierEntity qualifierEntity = new QualifierEntity();
        qualifierEntity.setQualifier(qualifier);
        qualifierEntity.setMajorTopic(majorTopic);
        manager.persist(qualifierEntity);
        return qualifierEntity.getId();
    }

    public int createDescriptor(String descriptor, Boolean majorTopic) {
        DescriptorEntity descriptorEntity = new DescriptorEntity();
        descriptorEntity.setDescriptor(descriptor);
        descriptorEntity.setMajorTopic(majorTopic);
        manager.persist(descriptorEntity);
        return descriptorEntity.getId();
    }

    public void createRecordDate(String recordName, Integer issue) {
        MeshtermRecordDatesEntity meshtermRecordDatesEntity = new MeshtermRecordDatesEntity();
        meshtermRecordDatesEntity.setRecordName(recordName);
        meshtermRecordDatesEntity.setDate(issue);
        manager.persist(meshtermRecordDatesEntity);
    }

    public boolean recordNameExists(String recordName) {
        MeshtermRecordDatesEntity meshtermRecordDateEntity = manager.find(MeshtermRecordDatesEntity.class, recordName);
        return meshtermRecordDateEntity != null;
    }

    public int findQualifierId(String qualifier, Boolean majorTopic) {
        List<Integer> result = manager.createNamedQuery("selectQualifierId").setParameter("qualifier", qualifier)
                .setParameter(MAJOR_TOPIC_PARAM, majorTopic).getResultList();
        if (result.size() != 1) {
            return -1;
        }
        return result.get(0);
    }

    public int findDescriptorId(String descriptor, Boolean majorTopic) {
        List<Integer> result = manager.createNamedQuery("selectDescriptorId").setParameter("descriptor", descriptor)
                .setParameter(MAJOR_TOPIC_PARAM, majorTopic).getResultList();
        if (result.size() != 1) {
            return -1;
        }
        return result.get(0);
    }

    public List<Integer> getMeshtermRecordIds(String recordName) {
        return manager.createNamedQuery("selectMeshterms").setParameter(RECORD_NAME_PARAM, recordName).getResultList();
    }

    public List<DescriptorEntity> getDescriptors(String recordName) {
        return manager.createNamedQuery("findDistinctDescriptors").setParameter(RECORD_NAME_PARAM, recordName)
                .getResultList();
    }

    public List<QualifierEntity> getQualifiers(String recordName, DescriptorEntity descriptorEntity) {
        return manager.createNamedQuery("findQualifiersByRecordNameAndDescriptor")
                .setParameter(RECORD_NAME_PARAM, recordName).setParameter("descriptorEntity", descriptorEntity)
                .getResultList();
    }

    public List<String> findUpdatedRecords(Integer issue) {
        return manager.createNamedQuery("findUpdatedRecords").setParameter("date", issue).getResultList();
    }

    public List<String> findRecords(Set<String> recordNames) {
        return manager.createNamedQuery("findRecords").setParameter("recordNames", recordNames).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getRecords4CheckCountByDbId(int dbId) {
        return ((Number) manager.createNamedQuery("meshtermRecord4CheckCountByDbId")
                .setParameter(DB_ID_PARAM, dbId).getSingleResult()).intValue();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void saveRecords4Check(List<MeshtermRecord4CheckEntity> entities, String dbName) {
        DatabaseEntity dbEntity = AbstractManager.getResultStorage().getDatabaseEntity(dbName);
        for (MeshtermRecord4CheckEntity entity : entities) {
            entity.setDatabase(dbEntity);
            manager.persist(entity);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<MeshtermRecord4CheckEntity> getRecords4CheckNotCheckedByDbId(int dbId, int limit) {
        Query query = manager.createNamedQuery("meshtermRecord4CheckNotCheckedDbId").setParameter(DB_ID_PARAM, dbId);
        if (limit > 0) {
            query = query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<MeshtermRecord4CheckEntity> getOutdatedRecords4Check(int dbId) {
        return manager.createNamedQuery("meshtermRecord4CheckOutdatedByDbId")
                .setParameter(DB_ID_PARAM, dbId).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateRecords4CheckStatus(List<Integer> ids, boolean checked) {
        manager.createNamedQuery("updateMeshtermRecord4CheckStatusByIds")
                .setParameter("status", checked).setParameter(IDS_PARAM, ids).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecords4Check() {
        MeshtermRecord4CheckEntity.qDel(manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecords4Check(int dbId) {
        MeshtermRecord4CheckEntity.qDel(dbId, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getRecordDescriptorsCountByRecordId(int id) {
        return (Long) manager.createNamedQuery("meshtermRecordDescriptorCountByRecordId")
                .setParameter(IDS_PARAM, id).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createRecordDescriptors(List<String> descs, MeshtermRecord4CheckEntity rEntity) {
        for (String desc : descs) {
            MeshtermRecordDescriptorEntity rdEntity = new MeshtermRecordDescriptorEntity();
            rdEntity.setDescriptor(desc);
            rdEntity.setRecord(rEntity);
            manager.persist(rdEntity);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecordDescriptors() {
        MeshtermRecordDescriptorEntity.qDel(manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecordDescriptors(int dbId) {
        MeshtermRecordDescriptorEntity.qDel(dbId, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void addChangedDescriptors(List<String> descs) {
        for (String desc : descs) {
            MeshtermChangedDescriptorEntity entity = new MeshtermChangedDescriptorEntity();
            entity.setDescriptor(desc);
            manager.persist(entity);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getChangedDescriptorsCount() {
        return (Long) manager.createNamedQuery("meshtermChangedDescriptorCount").getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteChangedDescriptors() {
        manager.createNamedQuery("deleteMeshtermChangedDescriptor").executeUpdate();
    }
}
