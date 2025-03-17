package com.wiley.cms.cochrane.cmanager.ebch;

import java.util.Collection;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.10.13
 */
public interface IBasketHolder {

    void clearProcessBasket();

    Collection<Integer> getProcessBasketContent();

    int getProcessBasketSize();
}
