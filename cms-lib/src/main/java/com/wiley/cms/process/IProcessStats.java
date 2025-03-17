package com.wiley.cms.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 1/3/2020
 */
public interface IProcessStats {
    default String asString() {
        return null;
    }

    default boolean isEmpty() {
        return true;
    }
}
