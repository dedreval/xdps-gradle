package com.wiley.cms.cochrane.services;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 17.11.12
 */
public interface IWREventVisitor {

    int onEventReceive(PublishEvent event);

    int onEventReceive(LiteratumEvent event);
}
