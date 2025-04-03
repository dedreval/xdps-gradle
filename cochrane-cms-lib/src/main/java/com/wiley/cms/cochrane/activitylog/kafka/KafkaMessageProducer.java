package com.wiley.cms.cochrane.activitylog.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.validation.constraints.NotNull;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 4/1/2021
 */
public final class KafkaMessageProducer implements IKafkaMessageProducer {
    private static final Logger LOG = Logger.getLogger(KafkaMessageProducer.class);
    private static final Logger LOG_KAFKA = Logger.getLogger("Kafka");

    private static final KafkaMessageProducer INSTANCE = new KafkaMessageProducer();

    private Producer<String, String> producer;
    private String flowTopic;

    private final List<SFEvent> toComplete = Collections.synchronizedList(new ArrayList<>());
    private final List<SFEvent> toResend = Collections.synchronizedList(new ArrayList<>());

    private volatile int status;
    private volatile int resendMode;
    private volatile boolean asyncMode;

    private KafkaMessageProducer() {
    }

    public static IKafkaMessageProducer instance() {
        return INSTANCE;
    }

    public static void reset() {
        INSTANCE.close();
    }

    public static Status status() {
        if (!INSTANCE.open()) {
            INSTANCE.createProducer();
        }
        return new Status(INSTANCE.flowTopic, INSTANCE.status, INSTANCE.resendMode, INSTANCE.open(), INSTANCE.test(),
                INSTANCE.toResend.size(), INSTANCE.toComplete.size());
    }

    @Override
    public void produceMessage(@NotNull SFEvent message) {
        if (open() || createProducer()) {
            try {
                sendMessage(createMessage(flowTopic, message.dfId.toString(), message.body),  message);

            } catch (Throwable th) {
                LOG.error(String.format("%s shall be re-sent because of %s", message, th.getMessage()));
                completeMessage(message, false);
            }
        } else if (test()) {
            LOG_KAFKA.warn(String.format("%s is not sent due to test mode enabled:\n%s", message, message.body));
            completeMessage(message, true);

        } else if (autResend()) {
            LOG.warn(String.format("%s will be sent later as kafka producer is disabled", message));
            completeMessage(message, false);

        } else {
            LOG.warn(String.format("%s shall be sent when kafka producer is enabled", message));
        }
    }

    @Override
    public void produceMessage(String topic, String key, String data, Callback callback) {
        if (open() || createProducer()) {
            sendMessage(createMessage(topic, key, data), callback);
        }
    }

    @Override
    public void completeMessage(@NotNull SFEvent message, RecordMetadata metadata, Exception e) {
        if (e != null) {
            if (metadata != null) {
                LOG_KAFKA.error("OnACK callback %s : err=%s, topic=%s, timestamp=%d, partition=%d, offset=%d\n%s",
                    message, e.getMessage(), metadata.topic(), metadata.timestamp(), metadata.partition(),
                        metadata.offset(), message.body);
            } else {
                LOG_KAFKA.error("OnACK callback %s : err=%s \n%s", message, e.getMessage(), message.body);
            }
            completeMessage(message, false);

        } else if (metadata != null) {
            LOG_KAFKA.info("OnACK callback %s : topic=%s, timestamp=%d, partition=%d, offset=%d\n%s", message,
                    metadata.topic(), metadata.timestamp(), metadata.partition(), metadata.offset(), message.body);
            completeMessage(message, true);

        } else {
            LOG_KAFKA.error("OnACK callback %s : No metadata available\n", message, message.body);
            completeMessage(message, false);
        }
    }

    @Override
    public SFEvent[] checkMessages() {
        SFEvent[] list;
        if (open() && resend()) {
            synchronized (toResend) {
                list = checkList(toResend, CochraneCMSPropertyNames.getKafkaProducerResendBatch());
            }
            if (list.length > 0) {
                LOG.info("%d message(s) are going to be reproduced", list.length);
                for (SFEvent event: list) {
                    produceMessage(event);
                }
            }
        }
        synchronized (toComplete) {
            list = checkList(toComplete, 0);
        }

        printState();
        return list;
    }

    @Override
    public boolean isAsyncMode() {
        return asyncMode;
    }

    public boolean isOpen() {
        return status().open;
    }

    public boolean isTestMode() {
        return status().testMode;
    }

    private static SFEvent[] checkList(List<SFEvent> checkList, int batch) {
        if (checkList.isEmpty()) {
            return SFEvent.EMPTY;
        }
        SFEvent[] ret;
        if (batch > 0 && checkList.size() > batch) {

            ret = new SFEvent[batch];
            Iterator<SFEvent> it = checkList.iterator();
            int i = 0;
            while (it.hasNext() && i < batch) {
                ret[i++] = it.next();
                it.remove();
            }
        } else {
            ret = checkList.toArray(SFEvent.EMPTY);
            checkList.clear();
        }
        LOG.info("%d message(s) are going to be set completed", ret.length);

        return ret;
    }

    private void sendMessage(ProducerRecord<String, String> record, Callback callback) {
        if (record != null) {
            if (callback != null) {
                producer.send(record, callback);
            } else {
                producer.send(record);
            }
            LOG_KAFKA.info("OnSENT callback %s : topic=%s, key=%s", callback, record.topic(), record.key());
        }
    }

    private static ProducerRecord<String, String> createMessage(String topic, String key, String body) {
        try {
            return new ProducerRecord<>(topic, null, key, body);
        } catch (Throwable th) {
            LOG.error("unchecked error during producing message to kafka", th);
            return null;
        }
    }

    private void completeMessage(SFEvent message, boolean success) {
        if (success) {
            synchronized (toComplete) {
                toComplete.add(message);
            }
        } else  {
            synchronized (toResend) {
                toResend.add(message);
            }
        }
    }

    private void printState() {
        Status.printState(flowTopic, open(), resendMode, toResend.size(), toComplete.size());
    }

    private synchronized boolean createProducer() {
        printState();
        
        flowTopic = CochraneCMSPropertyNames.getKafkaTopic();
        resendMode = CochraneCMSPropertyNames.getKafkaProducerResendMode();
        asyncMode = CochraneCMSPropertyNames.isKafkaProducerAsyncMode();

        if (CochraneCMSPropertyNames.isKafkaProducerEnabled()) {
            if (open()) {
                LOG.info("kafka producer is opened", flowTopic);
                return true;
            }
            try {
                producer = new KafkaProducer<>(getProperties());
                status = 1;
                LOG.info("kafka producer for %s is created", flowTopic);

            } catch (Exception e) {
                LOG.error(e);
            }

        } else if (CochraneCMSPropertyNames.isKafkaProducerTestMode()) {
            LOG.info("kafka producer in a testing mode", flowTopic);
            status = -1;

        } else {
            LOG.info("kafka producer is disabled", flowTopic);
            status = 0;
        }

        printState();
        return open();
    }

    private boolean open() {
        return status == MODE_RESEND;
    }

    private boolean test() {
        return status == -1;
    }

    private boolean autResend() {
        return resendMode == MODE_AUT_RESEND;
    }
    
    private boolean resend() {
        return resendMode >= MODE_RESEND;
    }

    private static Properties getProperties() {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CochraneCMSPropertyNames.getKafkaBootstrapServers());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, CochraneCMSProperties.getProperty(
            "cms.cochrane.kafka.producer.client.id", "XDPS-" + PublishProfile.getProfile().get().getEnvironment()));

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        String interceptor = CochraneCMSProperties.getProperty(
                "cms.cochrane.kafka.producer.interceptor.classes", false);
        if (interceptor != null) {
            props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptor);
        }

        setCustomProperty("cms.cochrane.kafka.producer.buffer.memory",
                ProducerConfig.BUFFER_MEMORY_CONFIG, props);

        setCustomProperty("cms.cochrane.kafka.producer.retries",
                ProducerConfig.RETRIES_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.reconnect.backoff.max.ms",
                ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, props);

        setCustomProperty("cms.cochrane.kafka.producer.delivery.timeout.ms",
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.linger.ms",
                ProducerConfig.LINGER_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.request.timeout.ms",
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.batch.size",
                ProducerConfig.BATCH_SIZE_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.max.request.size",
                ProducerConfig.MAX_REQUEST_SIZE_CONFIG, props);

        setCustomProperty("cms.cochrane.kafka.producer.max.block.ms",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, props);

        setCustomProperty("cms.cochrane.kafka.producer.connections.max.idle.ms",
                ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.socket.connection.setup.timeout.max.ms",
                ProducerConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.socket.connection.setup.timeout.ms",
                ProducerConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, props);

        setCustomProperty("cms.cochrane.kafka.producer.retry.backoff.ms",
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG, props);
        setCustomProperty("cms.cochrane.kafka.producer.reconnect.backoff.ms",
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG, props);

        setCustomProperty("max.in.flight.requests.per.connection",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, props);

        return props;
    }

    private static void setCustomProperty(String customProperty, String kafkaProperty, Properties props) {
        long value = CochraneCMSProperties.getLongProperty(customProperty, -1);
        if (value >= 0) {
            props.put(kafkaProperty, "" + value);
        }
    }

    private synchronized void close() {
        if (producer != null) {
            try {
                producer.flush();
                producer.close();
                LOG.info("kafka producer is closed");

            } catch (Throwable tr) {
                LOG.error(tr);
            }
        }
        status = -1;
    }

    /**
     * Kafka producer status
     */
    public static final class Status {
        public final String topic;
        public final int status;
        public final int resendMode;
        public final boolean open;
        public final boolean testMode;
        public final int toResendCount;
        public final int toCompleteCount;

        private Status(String topic, int status, int resend, boolean open, boolean testMode, int toResendCount,
                       int toCompleteCount) {
            this.topic = topic;
            this.status = status;
            this.resendMode = resend;
            this.open = open;
            this.testMode = testMode;
            this.toResendCount = toResendCount;
            this.toCompleteCount = toCompleteCount;
        }

        public void printState() {
            printState(topic, open, resendMode, toResendCount, toCompleteCount);
        }

        private static void printState(String topic, boolean open, int resend, int toResendSize, int toCompleteSize) {
            LOG.info(String.format(
                    "kafka producer status: %s (%d), topic: %s, messages to reproduce: %d, messages to complete: %d",
                           open ? "enabled" : "disabled", resend, topic, toResendSize, toCompleteSize));
        }
    }
}
