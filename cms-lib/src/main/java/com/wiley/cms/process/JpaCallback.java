package com.wiley.cms.process;

import javax.persistence.EntityManager;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 17.01.12
 * @param <T>
 */
public interface JpaCallback<T> {
    T doInJpa(EntityManager em);
}
