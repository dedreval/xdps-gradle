/*
 * Created on Apr 19, 2006
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.wiley.cms.cochrane.process;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

import com.wiley.cms.render.services.IAcceptRenderingResults;
import com.wiley.tes.util.Logger;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 31.07.13
 */
@Stateless
@WebService(name = "AcceptRenderingProvider",
            serviceName = "AcceptRenderingResults",
            endpointInterface = "com.wiley.cms.render.services.IAcceptRenderingResults",
            targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptRenderingProvider implements IAcceptRenderingResults {
    protected static final Logger LOG = Logger.getLogger(AcceptRenderingProvider.class);

    @EJB(beanName = "RenderingManager")
    private IRenderingManager manager;

    public void acceptRenderingResults(
               @WebParam(name = "jobId", targetNamespace = "http://services.render.cms.wiley.com/jaws") int jobId,
               @WebParam(name = "renderingResult", targetNamespace = "http://services.render.cms.wiley.com/jaws")
               String renderingResult) {
        manager.acceptRenderingResults(jobId, renderingResult);
    }
}