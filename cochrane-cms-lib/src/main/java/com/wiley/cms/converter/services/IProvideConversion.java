package com.wiley.cms.converter.services;

import javax.activation.DataHandler;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
@WebService(targetNamespace = IProvideConversion.NAMESPACE)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/CochraneCMS")
public interface IProvideConversion {
    String NAMESPACE = "http://services.conversion.cms.wiley.com/jaws";

    @WebMethod
    ConversionResult convert(
            @WebParam(name = "sourceXml", targetNamespace = NAMESPACE) DataHandler source,
            @WebParam(name = "metadataXml", targetNamespace = NAMESPACE) DataHandler metadata);

    /*@WebMethod
    ConversionResult convertRevman(
            @WebParam(name = "sourceUri",    targetNamespace = NAMESPACE) URI sourcePath,
            @WebParam(name = "resultPath",   targetNamespace = NAMESPACE) String destPath,
            @WebParam(name = "issueYear",    targetNamespace = NAMESPACE) int issueYear,
            @WebParam(name = "issueNumber",  targetNamespace = NAMESPACE) int issueNumber,
            @WebParam(name = "packResult",   targetNamespace = NAMESPACE) boolean packResult,
            @WebParam(name = "saveMetadata", targetNamespace = NAMESPACE) boolean saveMetadata);*/
}
