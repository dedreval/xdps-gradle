package com.wiley.cms.cochrane.test;

import java.util.Collection;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/6/2020
 */
public interface IRecordHook {

    default void capture(Integer clDbId, String cdNumber, String operation) {
    }

    default void capture(Integer clDbId, Collection<String> cdNumbers, String operation) {
        cdNumbers.forEach(r -> capture(clDbId, r, operation));
    }

    default void capture(Integer dfId, String operation) {
    }
}
