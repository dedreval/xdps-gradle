package com.wiley.cms.qaservice.services;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
@WebService(targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
public interface IAcceptQaResults extends java.rmi.Remote {

    @WebMethod
    void acceptQaResults(
            @WebParam(name = "jobId", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") final int jobId,
            @WebParam(name = "qaResult", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
            final String qaResult);
}
