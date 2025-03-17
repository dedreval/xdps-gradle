package com.wiley.cms.cochrane.cmanager.publish;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/12/2018
 *
 * @param <T> An item for checking (a record number | cdNumber.pubNumber | a package (TBD))
 */
public interface IPublishChecker<T> {
    boolean isDelivered(T item);

    T getPublishCheckerItem(int recordNumber, String pubName);

    default void initialize() {
    }
}
