package com.wiley.cms.cochrane.activitylog.kafka;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * @param <T> - the type of event object for sending
 * Date: 23.03.21
 */
public interface IKafkaEventProducer<T> {
    IKafkaEventProducer<Object> IDLE_PRODUCER = new IKafkaEventProducer<Object>() {};

    default void produceEvent(T data) {
    }

    default void close() {
    }
}