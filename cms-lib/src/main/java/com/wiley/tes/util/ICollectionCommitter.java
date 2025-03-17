package com.wiley.tes.util;

import java.util.Collection;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2018
 *
 * @param <T>
 */
public interface ICollectionCommitter<T> {

    void commit(Collection<T> list);

    default int commitSize() {
        return 0;
    }
}
