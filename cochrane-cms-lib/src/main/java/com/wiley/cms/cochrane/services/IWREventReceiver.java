package com.wiley.cms.cochrane.services;

import java.util.List;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12.11.12
 */
public interface IWREventReceiver {

    void onPublish(int publishId);

    void receiveEvent(WREvent event);

    void receiveEvent(LiteratumEvent event);

    /**
     * Check if an event can be handled as WR event and register it in activity log
     * @param event      Literatum event for WR processing
     * @return  TRUE if an event is registered and can be handled
     */
    boolean registerEvent(LiteratumEvent event);

    /**
     * Handle WR events
     * @param events
     */
    void handleEvents(List<? extends WREvent> events, boolean registered);

    void handleEvent(WREvent event) throws Exception;

}
