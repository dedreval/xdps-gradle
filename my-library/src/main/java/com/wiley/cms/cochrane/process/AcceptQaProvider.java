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

import com.wiley.cms.qaservice.services.IAcceptQaResults;
import com.wiley.tes.util.Logger;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 31.07.13
 */
@Stateless
@WebService(name = "AcceptQaProvider",
            serviceName = "AcceptQaResults",
            endpointInterface = "com.wiley.cms.qaservice.services.IAcceptQaResults",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptQaProvider implements IAcceptQaResults {
    protected static final Logger LOG = Logger.getLogger(AcceptQaProvider.class);

    @EJB(beanName = "QaManager")
    private IQaManager manager;

    public void acceptQaResults(
            @WebParam(name = "jobId", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") final int jobId,
            @WebParam(name = "qaResult", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
            final String qaResult) {
        manager.acceptQaResults(jobId);
    }
}