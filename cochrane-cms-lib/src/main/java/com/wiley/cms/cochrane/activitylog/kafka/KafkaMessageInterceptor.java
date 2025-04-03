package com.wiley.cms.cochrane.activitylog.kafka;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 4/2/2021
 */
public class KafkaMessageInterceptor implements ProducerInterceptor<String, String> {
    private static final Logger LOG_KAFKA = Logger.getLogger("Kafka");

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        LOG_KAFKA.info("OnSENT topic=%s, key=%s, value=%s", record.topic(), record.key(), record.value());
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }
}
