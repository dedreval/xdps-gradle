package com.wiley.cms.render.services;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
@WebService(targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
public interface IAcceptRenderingResults extends java.rmi.Remote {
    @WebMethod
    void acceptRenderingResults(
            @WebParam(name = "jobId", targetNamespace = "http://services.render.cms.wiley.com/jaws") int jobId,
            @WebParam(name = "renderingResult", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            String renderingResult);
}
