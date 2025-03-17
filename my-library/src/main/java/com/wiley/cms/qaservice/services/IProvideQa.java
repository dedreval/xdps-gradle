/*
 * Created on Mar 22, 2006 To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.wiley.cms.qaservice.services;

import java.net.URI;
import java.rmi.RemoteException;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
@WebService(targetNamespace = "http://services.qaservice.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/qas")
public interface IProvideQa extends java.rmi.Remote {
    /**
     * Profile names
     */
    String REVMAN_METADATA_PROFILE = "revman_metadata";

    /**
     * Method for invoking QA for a single file.  For example:
     * <p><code>String result = check("<xml ...>...", "cdts_article");</code></p>
     *
     * @param document - xml document
     * @param profile  - the name of the overall product the job should do QA for
     * @param dbName   - specific db name to choose checker parameters
     * @return the result as String of this QA job
     */
    @WebMethod
    String check(@WebParam(name = "document",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String document,
                 @WebParam(name = "profile",
                         targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String profile,
                 @WebParam(name = "dbName",
                         targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String dbName
    );

    /**
     * Method for invoking QA for a single file.  For example:
     * <p><code>int jobId = singleFileQa("cochrane", "deliveryfile",
     * "http://depository.wiley.com/fileLocation", "http://myserver.wiley.com/service/qaResultService");</code></p>
     *
     * @param profile      - the name of the overall product the job should do QA for
     * @param qaPlan       - a name of the specific set of QA tasks to do
     * @param fileLocation - an HTTP URI specifying the location of the file for the service to retrieve
     * @param callbackUri  - the URI of the web service to call back to when jobs are finished
     * @return the jobId of this QA job
     */

    @WebMethod
    int singleFileQa(@WebParam(name = "profile",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String profile,
                     @WebParam(name = "qaPlan",
                             targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String qaPlan,
                     @WebParam(name = "fileLocation",
                             targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") URI fileLocation,
                     @WebParam(name = "callbackUri",
                             targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") URI callbackUri);

    /**
     * Method for invoking QA for multiple files.  For example:
     * <p><code>int jobId = multiFileQa("cochrane", "deliveryfile",
     * {"http://depository.wiley.com/fileLocation1",
     * "http://depository.wiley.com/fileLocation2",
     * "http://myserver.wiley.com/service/qaResultService"});</code></p>
     *
     * @param profile       - the name of the overall product the job should do QA for
     * @param qaPlan        - a name of the specific QA tasks to do
     * @param fileLocations - an array of HTTP URIs specifying the location of the file for the service to retrieve
     * @param callbackUri   - the URI of the web service to call back to when jobs are finished
     * @return the jobId of this QA job
     */
    @WebMethod
    int multiFileQa(@WebParam(name = "profile",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String profile,
                    @WebParam(name = "qaPlan",
                            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String qaPlan,
                    @WebParam(name = "fileLocations",
                            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") URI[] fileLocations,
                    @WebParam(name = "callbackUri",
                            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") URI callbackUri,
                    @WebParam(name = "priority",
                            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") int priority);

    /**
     * Get current job result at any moment
     *
     * @param jobId
     * @param callbackUri
     */
    @WebMethod
    void sendJobResult(@WebParam(name = "jobId",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") int jobId,
                       @WebParam(name = "callbackUri",
                               targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") URI callbackUri);

    /**
     * Clear job result from database
     *
     * @param jobId
     */
    @WebMethod
    void clearJobResult(int jobId);


    /**
     * Method for invoking QA for a string data.  For example:
     * <p><code>int jobId = checkXml("cochrane", "simple",
     * "&lt;?xml ... ", "sysrev");</code></p>
     *
     * @param profile - the name of the overall product the job should do QA for
     * @param qaPlan  - a name of the specific set of QA tasks to do
     * @param xmlBody - String data to check
     * @param dbName  - specific db name to choose checker parameters
     * @return the jobId of this QA job
     */
    @WebMethod
    int checkXml(@WebParam(name = "profile",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String profile,
                 @WebParam(name = "qaPlan",
                         targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String qaPlan,
                 @WebParam(name = "xmlBody",
                         targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String xmlBody,
                 @WebParam(name = "dbName",
                         targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") String dbName
    );

    /**
     * Get current job results as xml at any moment
     *
     * @param jobId - job id
     * @return the result as String
     */
    @WebMethod
    String getJobResults(@WebParam(name = "jobId",
            targetNamespace = "http://services.qaservice.cms.wiley.com/jaws") int jobId);

    @WebMethod
    String getVersion() throws RemoteException;

}
