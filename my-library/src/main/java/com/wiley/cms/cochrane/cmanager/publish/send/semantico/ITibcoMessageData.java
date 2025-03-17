package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/6/2017
 */
public interface ITibcoMessageData {

    String getMsgId();

    String getJmsNameOrFilePath();

    ITibcoMessage createStubMessageForResponse(int imitateError);

    ITibcoMessage createStubMessageForRequest();

    String toShortString();
}
