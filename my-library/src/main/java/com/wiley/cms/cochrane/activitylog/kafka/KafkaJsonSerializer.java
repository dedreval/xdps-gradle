package com.wiley.cms.cochrane.activitylog.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * @param <T> - the type of event object for sending
 * Date: 23.03.21
 */
public class KafkaJsonSerializer<T> implements Serializer<T> {

    private static Gson gson;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        gson = new GsonBuilder()
                       .setPrettyPrinting()
                       .create();
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return gson.toJson(data).getBytes();
    }
}
