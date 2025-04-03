/*
 * Created on Apr 19, 2006
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.wiley.cms.cochrane.cmanager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.naming.NamingException;

import org.jboss.ws.api.annotation.WebContext;

import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.qaservice.services.IAcceptQaResults;
import com.wiley.tes.util.Logger;

/**
 * Web service implementation to accept QA results in XML format
 *
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 */

@Deprecated
@Stateless
@WebService(endpointInterface = "com.wiley.cms.qaservice.services.IAcceptQaResults",
            serviceName = "AcceptQaResults",
            name = "AcceptQaResults",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptQaResults implements IAcceptQaResults {
    protected static final Logger LOG = Logger.getLogger(AcceptQaResults.class);
    protected static final long DELAY_BECAUSE_OF_LOCK = 10000;

    protected static ExecutorService executor;

    protected IRepository rps;
    protected int queueCount;
    protected String logUser;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void acceptQaResults(
            @WebParam(name = "jobId", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") final int jobId,
            @WebParam(name = "qaResult", targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
            final String qaResult) {

        doJob(jobId, qaResult, AcceptQaWorker.class);
    }

    protected void doJob(final int jobId, final String qaResult, Class workerClass) {
        LOG.debug("Results accepted jobId=" + jobId);

        synchronized (AcceptQaResults.class) {
            rps = RepositoryFactory.getRepository();
            int threadCount = Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.acceptqa.threadcount"));
            queueCount = Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.acceptqa.queuecount"));
            logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

            if (executor == null) {
                executor = Executors.newFixedThreadPool(threadCount);
            }
            if (((ThreadPoolExecutor) executor).getQueue().size() > queueCount) {
                IQaService qaService = null;
                try {
                    qaService = CochraneCMSPropertyNames.lookup("QaService", IQaService.class);
                    qaService.writeResultToDb(jobId, qaResult);
                } catch (NamingException e) {
                    LOG.error(e, e);
                }
            } else {
                AcceptQaWorker newWorker = null;
                try {
                    newWorker = (AcceptQaWorker) workerClass.newInstance();
                    newWorker.setAcceptQaResults(this);
                    newWorker.setJobId(jobId);
                    newWorker.setQaResult(qaResult);
                    executor.execute(newWorker);
                } catch (InstantiationException e) {
                    ;
                } catch (IllegalAccessException e) {
                    ;
                }

            }
        }
    }
}