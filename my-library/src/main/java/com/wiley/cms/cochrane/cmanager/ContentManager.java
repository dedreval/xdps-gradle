package com.wiley.cms.cochrane.cmanager;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

import com.wiley.cms.cochrane.cmanager.contentworker.AbstractWorker;
import com.wiley.cms.cochrane.cmanager.contentworker.WorkerFactory;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IContentManager.class)
@WebService(targetNamespace = "http://services.cochrane.cms.wiley.com/jaws", name = "IContentManager",
        endpointInterface = "com.wiley.cms.cochrane.services.IContentManager")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ContentManager implements IContentManager {
    public static final Logger LOG = Logger.getLogger(ContentManager.class);
    private static final Map<String, ExecutorService> EXECUTORS = new HashMap<>();

    @WebMethod(operationName = "NewPackageReceived")
    public void newPackageReceived(@WebParam(name = "URI_1",
        targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") final URI packageUri) {

        LOG.debug("package received, uri " + packageUri);

        String path = packageUri.getPath();
        String packageFileName = path.substring(path.lastIndexOf("/") + 1);

        AbstractWorker worker = init(packageFileName.toLowerCase());
        if (worker != null) {
            worker.setPackageUri(packageUri);
            getExecutor(worker.getLibName()).execute(worker);
        }
    }

    @WebMethod(operationName = "ResumeOldPackage")
    public void resumeOldPackage(
        @WebParam(name = "packId", targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") int packId,
        @WebParam(name = "packName", targetNamespace = "http://services.cochrane.cms.wiley.com/jaws") String packName) {

        LOG.debug("package resumed, name " + packName);

        AbstractWorker worker = init(packName.toLowerCase());
        if (worker != null) {
            worker.setPackageId(packId);
            getExecutor(worker.getLibName()).execute(worker);
        }
    }

    @WebMethod(operationName = "ResetContentManager")
    public void resetContentManager() {
        synchronized (EXECUTORS) {
            for (ExecutorService es : EXECUTORS.values()) {
                es.shutdown();
            }
            EXECUTORS.clear();
        }
    }

    private AbstractWorker init(String packageFileName) {

        AbstractWorker worker = WorkerFactory.createWorkerByPackageName(packageFileName);
        if (worker == null) {
            LOG.error("Failed to instantiate worker for " + packageFileName);
            return null;
        }
        return worker;
    }

    private ExecutorService getExecutor(String libName) {
        ExecutorService ret;
        synchronized (EXECUTORS)   {
            ret = EXECUTORS.get(libName);
            if (ret == null) {
                int threadCount = Integer.parseInt(
                    CochraneCMSProperties.getProperty("cms.cochrane.contentmanager.threadcount"));
                ret =  Executors.newFixedThreadPool(threadCount);
                EXECUTORS.put(libName, ret);
            }
        }
        return ret;
    }
}
