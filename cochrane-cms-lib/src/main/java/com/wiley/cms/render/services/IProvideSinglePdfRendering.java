package com.wiley.cms.render.services;

import java.net.URI;

import javax.activation.DataHandler;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlMimeType;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
@WebService(name = "IProvideSinglePdfRendering",
        targetNamespace = IProvideSinglePdfRendering.NAMESPACE)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/render-service")
public interface IProvideSinglePdfRendering {
    String NAMESPACE = "http://services.singlepdfrender.cms.wiley.com/jaws";

    @WebMethod
    @XmlMimeType("*/*")
    DataHandler renderSinglePdf(@WebParam(name = "URI", targetNamespace = NAMESPACE) URI uri,
                                @WebParam(name = "IsRawDataExists", targetNamespace = NAMESPACE)
                                boolean isRawDataExists);

    @WebMethod
    @XmlMimeType("*/*")
    DataHandler renderSinglePdfByPlan(
            @WebParam(name = "plan", targetNamespace = NAMESPACE) String plan,
            @WebParam(name = "database", targetNamespace = NAMESPACE) String database,
            @WebParam(name = "URI", targetNamespace = NAMESPACE) URI uri,
            @WebParam(name = "IsRawDataExists", targetNamespace = NAMESPACE) boolean isRawDataExists,
            @WebParam(name = "IsJatsStatsPresent", targetNamespace = NAMESPACE) boolean isJatsStatsPresent);
}
