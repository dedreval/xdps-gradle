package com.wiley.cms.cochrane.activitylog;

import java.util.List;
import java.util.UUID;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.process.ModelController;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/14/2022
 */
@Local(IUUIDManager.class)
@Stateless
public class UUIDManager extends ModelController implements IUUIDManager {

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public UUIDEntity createUUIDEntity(int event, Integer entityId) {
        UUIDEntity ret = new UUIDEntity();

        ret.setSid(UUID.randomUUID().toString());
        ret.setEvent(event);
        ret.setEntityId(entityId);

        getManager().persist(ret);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public UUIDEntity findUUIDEntity(int event, Integer entityId) {
        List<UUIDEntity> list = UUIDEntity.queryUUIDByEntityIdAndEvent(entityId, event, getManager()).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public UUIDEntity findLastUUIDEntity(int event) {
        List<UUIDEntity> list = UUIDEntity.queryUUIDByEvent(event, 1, getManager()).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<UUIDEntity> findUUIDEntities(int event) {
        return UUIDEntity.queryUUIDByEvent(event, 0, getManager()).getResultList();
    }
}
