package com.wiley.cms.cochrane.activitylog.kafka;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.tes.util.Logger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * @param <T> - the type of event object for sending
 * Date: 23.03.21
 */
public class KafkaEventProducer<T> implements IKafkaEventProducer<T> {
    private static final Logger LOG = Logger.getLogger(KafkaEventProducer.class);

    private final Producer<String, T> producer;
    private final String topic;

    private KafkaEventProducer(Producer<String, T> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    public static <T> IKafkaEventProducer<T> getInstance() {
        final Properties properties = setProperties(new Properties());
        return new KafkaEventProducer<>(new KafkaProducer<>(properties), CochraneCMSPropertyNames.getKafkaTopic());
    }

    public void produceEvent(T data) {
        if (!CochraneCMSPropertyNames.isKafkaProducerEnabled()) {
            return;
        }

        try {
            final ProducerRecord<String, T> record = new ProducerRecord<>(topic, null, System.currentTimeMillis(),
                                                                          null, data);
            producer.send(record);
            LOG.info("record with timestamp '%d' is sent to topic '%s'", record.timestamp(), record.topic());

        } catch (Throwable tr) {
            LOG.error(tr);
        }
    }

    private static Properties setProperties(Properties props) {
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CochraneCMSPropertyNames.getKafkaBootstrapServers());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, KafkaEventProducer.class.getSimpleName());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class.getName());
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, KafkaProducerInterceptor.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    public void close() {
        try {
            producer.flush();
            producer.close();

        } catch (Throwable tr) {
            LOG.error(tr);
        }
    }
}
