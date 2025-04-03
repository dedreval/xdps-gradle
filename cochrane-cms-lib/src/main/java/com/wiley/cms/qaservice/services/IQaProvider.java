package com.wiley.cms.qaservice.services;

import java.net.URI;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.06.13
 */
@WebService(targetNamespace = IQaProvider.TARGET_NAMESPACE)
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/qas")
public interface IQaProvider extends java.rmi.Remote {

    String TARGET_NAMESPACE = "http://services.qaservice.cms.wiley.com/jaws";

    int MAX_SESSION = 6;

    @WebMethod
    int provideQaMultiFile(@WebParam(name = "creatorId",
                targetNamespace = TARGET_NAMESPACE) int creatorId,
                           @WebParam(name = "profile",
                targetNamespace = TARGET_NAMESPACE) String profile,
                           @WebParam(name = "qaPlan",
                targetNamespace = TARGET_NAMESPACE) String qaPlan,
                           @WebParam(name = "fileLocations",
                targetNamespace = TARGET_NAMESPACE) URI[] fileLocations,
                           @WebParam(name = "callbackUri",
                targetNamespace = TARGET_NAMESPACE) URI callbackUri,
                           @WebParam(name = "priority",
                targetNamespace = TARGET_NAMESPACE) int priority);
}
