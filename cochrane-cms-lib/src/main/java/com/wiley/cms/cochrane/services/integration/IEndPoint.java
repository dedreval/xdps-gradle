package com.wiley.cms.cochrane.services.integration;

import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public interface IEndPoint {
          
    String getName();

    String getHostName();

    boolean isEnabled();

    default String getName(BaseType baseType) {
        return getName();
    }

    default boolean isEnabled(BaseType baseType) {
        return isEnabled();
    }

    default boolean isTestMode() {
        return false;
    }

    default String getServiceName() {
        return null;
    }

    default Boolean testCall() {
        return null;
    }

    default String[] getSchedule() {
        return null;
    }

    default List<String> getSpecialOptions() {
        return Collections.emptyList();
    }
}
