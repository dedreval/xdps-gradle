package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/9/2017
 */
public interface ITibcoMessage {

    String getTextMessage();

    boolean isSuccess();

    boolean hasEmptyResponse();

    String getResponseSid();

    int getResponseId() throws Exception;

    String getResponseStatus();

    String getResponseCode();

    String asXmlString();

    default String asFormatXmlString() {
        return asXmlString();
    };

    String toShortString();
}
