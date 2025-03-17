package com.wiley.tes.util.res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 *
 * @param <R> Resource
 */
public interface IResourceContainer<R extends Resource> {

    boolean validate();

    void publish(R res);

    int size();
}

