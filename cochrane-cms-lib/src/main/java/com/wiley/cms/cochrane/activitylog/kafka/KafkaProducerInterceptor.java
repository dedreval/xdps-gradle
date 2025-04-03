package com.wiley.cms.cochrane.activitylog.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wiley.tes.util.Logger;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * @param <T> - the type of event object for sending
 * Date: 23.03.21
 */
public class KafkaProducerInterceptor<T> implements ProducerInterceptor<String, T> {
    private static final Logger LOG_KAFKA = Logger.getLogger("Kafka");
    private static Gson gson;

    @Override
    public void configure(Map<String, ?> configs) {
        gson = new GsonBuilder()
                       .setPrettyPrinting()
                       .create();
    }

    @Override
    public ProducerRecord<String, T> onSend(ProducerRecord<String, T> record) {
        LOG_KAFKA.debug("onSend topic=%s, timestamp=%d, value=%s", record.topic(), record.timestamp(),
                        gson.toJson(record.value()));
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (metadata != null) {
            LOG_KAFKA.debug("onAck topic=%s, timestamp=%d, partition=%d, offset=%d", metadata.topic(),
                            metadata.timestamp(), metadata.partition(), metadata.offset());
        } else if (exception != null) {
            LOG_KAFKA.error(String.format("onAck recordMetadata=null, error=%s", exception));
        } else {
            LOG_KAFKA.error("No acknowledgement info available");
        }
    }

    @Override
    public void close() {
    }
}
