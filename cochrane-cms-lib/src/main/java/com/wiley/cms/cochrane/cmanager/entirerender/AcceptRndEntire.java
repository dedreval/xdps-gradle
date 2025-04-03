package com.wiley.cms.cochrane.cmanager.entirerender;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

import com.wiley.cms.cochrane.cmanager.AcceptRenderingResults;
import com.wiley.cms.render.services.IAcceptRenderingResults;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 15.03.11
 */
@Deprecated
@Stateless
@WebService(name = "AcceptRndEntire",
            serviceName = "AcceptRenderingResults",
            endpointInterface = "com.wiley.cms.render.services.IAcceptRenderingResults",
            targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptRndEntire extends AcceptRenderingResults implements IAcceptRenderingResults {
    {
        log = Logger.getLogger(AcceptRndEntire.class);
        queueName = "java:jboss/exported/jms/queue/entire_accept_rendering";
    }
}
