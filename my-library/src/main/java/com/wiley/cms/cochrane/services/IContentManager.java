package com.wiley.cms.cochrane.services;

import java.net.URI;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * Cochrane CMS Web Service interface.
 * Used by Depository.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
@WebService(targetNamespace = "http://services.cochrane.cms.wiley.com/jaws",
        name = "IContentManager")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/CochraneCMS")
public interface IContentManager {
    /**
     * After Depository received the new package
     * it call this method and send URI to file on the FTP (with login information)
     *
     * @param packageUri URI to package file
     */
    @WebMethod(operationName = "NewPackageReceived")
    void newPackageReceived(
        @WebParam(name = "URI_1", targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") URI packageUri);

    /**
     * Resume the loading package process by the old delivery file Id
     * @param packId  The delivery file Id
     * @param packName  The delivery file name
     */
    @WebMethod(operationName = "ResumeOldPackage")
    void resumeOldPackage(
        @WebParam(name = "packId", targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") int packId,
        @WebParam(name = "packName", targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") String packName);

    @WebMethod(operationName = "ResetContentManager")
    void resetContentManager();
}
