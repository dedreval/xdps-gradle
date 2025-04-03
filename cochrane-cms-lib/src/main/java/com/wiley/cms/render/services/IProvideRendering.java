/*
 * Created on Apr 20, 2006
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.wiley.cms.render.services;

import java.net.URI;
import java.rmi.RemoteException;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

//import org.apache.axis.AxisFault;
import org.jboss.ws.api.annotation.WebContext;

/**
 * @author unknown
 * @version 1.0
 */
@WebService(name = "IProvideRendering",
        targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/render-service")
public interface IProvideRendering extends java.rmi.Remote {
    /**
     * A service that takes an XML request for rendering. <br/><br/>
     * <p/>
     * <p>Here is the XML format.
     * All elements are required.
     * URLs are just examples; they may take any format desired by the calling application.</p>
     * <p/>
     * <code>
     * &lt;render><br/>
     * &lt;profile>profile_name&lt;/profile><br/>
     * &lt;renderPlan>plan_name&lt;/renderPlan><br/>
     * &lt;callbackUrl>http://cochraneserver.wiley.com/cochrane/services/AcceptRenderResults&lt;/callbackUrl><br/>
     * &lt;records><br/>
     * &lt;record><br/>
     * &lt;fileUri>http://server/getFile?fileRef=123&lt;/fileUri><br/>
     * &lt;assetUrl>http://server/getAssets?fileRef=...&lt;/assetUrl><br/>
     * &lt;/record><br/>
     * &lt;record><br/>
     * &lt;fileUri>http://server/getFile?fileRef=123&lt;/fileUri><br/>
     * &lt;assetUrl>http://server/getAssets?fileRef=...&lt;/assetUrl><br/>
     * &lt;/record><br/>
     * &lt;/records><br/>
     * &lt;/render><br/>
     * </code>
     * </p>
     * <p/>
     * &lt;profile> -- the name of the overall product the job should do rendering for<br/>
     * &lt;renderPlan> -- a name of the specific set of render tasks to do (could work for any product)<br/>
     * &lt;callbackUrl> -- URL of the webservice to call back to use for calling back to report results and such.
     * See CochraneCms specs for how to call it.<br/>
     * &lt;fileUri> -- an http URI where the file can be download. It also serves as the file reference or ID.<br/>
     * &lt;assetUrl>-- a URL that returns a zip file of all assets for this particular file<br/>
     *
     * @param profile           - the name of the overall product the job should do QA for
     * @param plan              - a name of the specific QA tasks to do
     * @param fileLocations     - an array of HTTP URIs specifying the location of the file for the service to retrieve
     * @param callbackUri       - the URI of the web service to call back to when jobs are finished
     * @param requestParameters - an array of String parameters (each parameter should be in key=value format)
     * @param priority          - priority of the job
     * @return the jobId of this QA job
     */
    @WebMethod
    int render(
            @WebParam(name = "profile", targetNamespace = "http://services.render.cms.wiley.com/jaws") String profile,
            @WebParam(name = "plan", targetNamespace = "http://services.render.cms.wiley.com/jaws") String plan,
            @WebParam(name = "fileLocations", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            URI[] fileLocations,
            @WebParam(name = "isRawDataExists", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            boolean[] isRawDataExists,
            @WebParam(name = "callbackUri", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            URI callbackUri,
            @WebParam(name = "requestParameters", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            String[] requestParameters,
            @WebParam(name = "priority", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            int priority); // throws AxisFault;

    /* Get current job result at any moment
     * @param jobId
     * @return
     */
    @WebMethod
    void sendJobResult(int jobId, URI callbackUri);

    /**
     * Clear job result from database
     *
     * @param jobId
     */
    @WebMethod
    void clearJobResult(int jobId);

    /**
     * Clear result for specified jobPartIds from database
     *
     * @param jobId,jobPartIds
     */
    @WebMethod
    void clearPartialResult(int jobId, int[] jobPartIds);

    /**
     * set up about xml files for right CDSR rendering
     *
     * @param fileLocations - an array of HTTP URIs specifying the location of the file for the service to retrieve
     * @param issueName     - specified issue name (2007_Issue_2 for example)
     * @param callbackUri   - the URI of the web service to call back to when job is finished
     */
    @WebMethod
    void setAboutResources(URI[] fileLocations, String issueName, URI callbackUri);

    @WebMethod
    String getVersion() throws RemoteException;
}
