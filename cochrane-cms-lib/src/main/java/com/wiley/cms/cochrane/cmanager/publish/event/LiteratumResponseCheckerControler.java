package com.wiley.cms.cochrane.cmanager.publish.event;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 25.10.2018
 */
public interface LiteratumResponseCheckerControler {
    void start();

    void stop();

    String checkResponses();
}
