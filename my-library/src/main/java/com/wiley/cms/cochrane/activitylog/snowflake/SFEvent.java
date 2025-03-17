package com.wiley.cms.cochrane.activitylog.snowflake;

import java.io.Serializable;

import javax.validation.constraints.NotNull;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import com.wiley.cms.cochrane.activitylog.kafka.KafkaMessageProducer;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 4/5/2021
 */
public class SFEvent implements Callback, Serializable {
    public static final SFEvent[] EMPTY = new SFEvent[0];

    private static final long serialVersionUID = 1L;
      
    public final Long eventId;
    public final Integer dfId;
    public final String body;
    public final SFType type;

    SFEvent(@NotNull Long eventId, Integer dfId, @NotNull String body, @NotNull String type) {
        this(eventId, dfId, body, SFType.valueOf(type));
    }

    SFEvent(@NotNull Long eventId, Integer dfId, @NotNull String body, SFType type) {
        this.eventId = eventId;
        this.dfId = dfId;
        this.body = body;
        this.type = type;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception e) {
        KafkaMessageProducer.instance().completeMessage(this, metadata, e);
    }

    @Override
    public String toString() {
        return String.format("%d[%d] '%s'", dfId, eventId, type.label());
    }
}
