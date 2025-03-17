package com.wiley.cms.process.entity;

import java.util.Collection;
import java.util.List;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.wiley.cms.process.IModelController;
import com.wiley.cms.process.JpaCallback;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 *         Date: 27.11.13
 */
public abstract class AbstractModelController implements IModelController {

    protected abstract EntityManager getManager();

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T> T execute(JpaCallback<T> action) {
        return action.doInJpa(getManager());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void persist(Object entity) {
        getManager().persist(entity);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T> T flush(T entity) {
        T ref = getManager().merge(entity);
        getManager().flush();
        return ref;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T> T find(Class<T> clazz, Number id) {
        return DbUtils.exists(id) ? getManager().find(clazz, id) : null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public <T> Collection<T> find(Class<T> clazz, String selectQuery, int skip, int batchSize, Object... params) {

        Query q = getManager().createNamedQuery(selectQuery);

        for (int i = 0; i < params.length; i += 2) {
            q.setParameter(params[i].toString(), params[i + 1]);
        }

        DbEntity.appendBatchResults(q, skip, batchSize);

        return q.getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T> void delete(Class<T> clazz, Number id) {
        T obj = getManager().find(clazz, id);
        if (obj != null)  {
            getManager().remove(obj);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int executeNativeQuery(String query) {
        return getManager().createNativeQuery(query).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List getNativeQueryResults(String query) {
        return getManager().createNativeQuery(query).getResultList();
    }

    protected int getSingleResultIntValue(Query q) {
        return ((Number) q.getSingleResult()).intValue();
    }
}

