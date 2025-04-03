
package com.wiley.cms.notification.service;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

/**
 * JBossWS Generated Source
 * <p/>
 * Generation Date: Fri Jul 24 13:28:27 MSD 2015
 * <p/>
 * This generated source code represents a derivative work of the input to
 * the generator that produced it. Consult the input for the copyright and
 * terms of use that apply to this source code.
 * <p/>
 * JAX-WS Version: 2.0
 *
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.09.2014
 */
@WebService(name = "INotificationWebService", targetNamespace = "http://webservice.web.ntfservice.cms.wiley.com/")
public interface INotificationWebService {

    String TARGET_NS = "http://webservice.web.ntfservice.cms.wiley.com/";

    /**
     * @param arg0
     * @return returns com.wiley.cms.notification.service.NotificationResult
     */
    @WebMethod
    @WebResult(targetNamespace = "")
    @RequestWrapper(localName = "processNotification",
            targetNamespace = "http://webservice.web.ntfservice.cms.wiley.com/",
            className = "com.wiley.cms.notification.service.ProcessNotification")
    @ResponseWrapper(localName = "processNotificationResponse",
            targetNamespace = "http://webservice.web.ntfservice.cms.wiley.com/",
            className = "com.wiley.cms.notification.service.ProcessNotificationResponse")
    NotificationResult processNotification(
            @WebParam(name = "arg0", targetNamespace = "") NewNotification arg0);
}
