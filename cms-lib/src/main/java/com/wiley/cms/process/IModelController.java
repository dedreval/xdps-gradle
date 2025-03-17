package com.wiley.cms.process;

import java.util.Collection;
import java.util.List;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 17.01.12
 */
public interface IModelController {

    <T> T execute(JpaCallback<T> action);

    void persist(Object entity);

    <T> T flush(T entity);

    <T> void delete(Class<T> clazz, Number id);

    <T> T find(Class<T> clazz, Number id);

    <T> Collection<T> find(Class<T> clazz, String selectQuery, int skip, int batchSize, Object... params);

    int executeNativeQuery(String query);

    List getNativeQueryResults(String query);
}
