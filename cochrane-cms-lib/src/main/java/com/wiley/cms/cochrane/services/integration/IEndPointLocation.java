package com.wiley.cms.cochrane.services.integration;

import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.ConnectionType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.ServerType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/14/2022
 */
public interface IEndPointLocation {

    BaseType getBaseType();

    ServerType getServerType();

    PubType getPubType();

    ConnectionType getConnectionType();

    String getFolder();

    boolean isEntirePath();

    IEndPointLocation getReplication();
}
