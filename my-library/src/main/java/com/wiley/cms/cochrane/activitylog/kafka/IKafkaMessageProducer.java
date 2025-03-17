package com.wiley.cms.cochrane.activitylog.kafka;

import javax.validation.constraints.NotNull;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 4/5/2021
 */
public interface IKafkaMessageProducer {

    int MODE_RESEND = 1;
    int MODE_AUT_RESEND = 2;

    /**
     * Send a message to Kafka for SF
     * @param message  a message for SF
     */
    void produceMessage(@NotNull SFEvent message);

    /**
     * Send a message to Kafka to specified topic
     * @param topic
     * @param key
     * @param data
     * @param callback
     */
    void produceMessage(String topic, String key, String data, Callback callback);

    /**
     * It is callback on completion of a SF message. In case of an error it is marked to be resent
     * @param message   a message for SF
     * @param metadata
     * @param e
     */
    void completeMessage(@NotNull SFEvent message, RecordMetadata metadata, Exception e);

    /**
     * Print current producer status,
     * Set all currently acknowledged SF messages as sent to SF,
     * Reproduce all SF messages marked as to be resent if 're-send' mode is enabled
     * @return SF messages currently completed
     */
    @NotNull
    SFEvent[] checkMessages();

    /**
     * @return If TRUE, messages to Kafka shall be provided via JMS Queue
     */
    boolean isAsyncMode();

    boolean isOpen();

    boolean isTestMode();
}
