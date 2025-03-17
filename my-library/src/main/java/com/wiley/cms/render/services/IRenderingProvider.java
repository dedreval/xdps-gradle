package com.wiley.cms.render.services;

import java.net.URI;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 04.09.13
 */
@WebService(name = "IRenderingProvider", targetNamespace = IRenderingProvider.TARGET_NAMESPACE)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT)
@WebContext(contextRoot = "/render-service")
public interface IRenderingProvider {

    String TARGET_NAMESPACE = "http://services.render.cms.wiley.com/jaws";

    int MAX_SESSION = 6;

    String JOB_PARAM_COMMON = "common";
    String JOB_PARAM_PLAN = "plan";
    String JOB_PARAM_PROFILE = "profile";
    String JOB_PARAM_ISSUE_YEAR = "issueYear";
    String JOB_PARAM_ISSUE_NUMBER = "issueNumber";
    String JOB_PARAM_COUNT = "count";
    String JOB_PARAM_DATABASE = "database";
    String JOB_PARAM_FULL_PDF_ONLY = "full-pdf-only";
    
    String PART_PARAM_FULL_PDF_ONLY = JOB_PARAM_FULL_PDF_ONLY;
    String PART_PARAM_RAW_EXIST = "rawExist";
    String PART_PARAM_MAKE_STATS_DATA = "makeStatsData";
    String PART_PARAM_LANGUAGES = "langs";

    String PLAN_PDF = "pdf_tex";
    String PLAN_PDF_FOP = "pdf_fop";

    /**
     * A service that takes an XML request for rendering.
     * @param creatorId          The identifier of creator of this job
     * @param jobParameters      The array of String parameters (each parameter should be in key=value format)
     * @param fileLocations      The array of HTTP URIs specifying the location of the file for the service to retrieve.
     * @param isRawDataExists    The array of flags specifying the existing of raw data.
     * @param partParameters     The array of String parameters (each parameter should be in key=value format)
     * @param callbackUri        The URI of the web service to call back to when jobs are finished.
     * @param priority           The priority of the job
     * @return The processId of this Rendering job
     */
    @WebMethod
    int render(@WebParam(name = "creatorId",
                targetNamespace = TARGET_NAMESPACE) int creatorId,
                        @WebParam(name = "jobParameters",
                targetNamespace = TARGET_NAMESPACE) String[] jobParameters,
                        @WebParam(name = "fileLocations",
                targetNamespace = TARGET_NAMESPACE) URI[] fileLocations,
                        @WebParam(name = "isRawDataExists",
                targetNamespace = TARGET_NAMESPACE) boolean[] isRawDataExists,
                        @WebParam(name = "partParameters",
                targetNamespace = TARGET_NAMESPACE) String[] partParameters,
                        @WebParam(name = "callbackUri",
                targetNamespace = TARGET_NAMESPACE) URI callbackUri,
                        @WebParam(name = "priority",
                targetNamespace = TARGET_NAMESPACE) int priority);

    /**
     * It starts or resumes the rendering sub-process by its identifier.
     * @param renderId   The identifier of the rendering process
     * @return  TRUE if the rendering starts successfully
     */
    @WebMethod
    boolean resumeRendering(@WebParam(name = "renderId", targetNamespace = TARGET_NAMESPACE) int renderId);

    /**
     * A service that takes an XML request for rendering, but only creates the job without real starting.
     * It's supposed it can be run later with the resumeRendering() or render() call.
     * @param creatorId          The identifier of creator of this job
     * @param jobParameters      The array of String parameters (each parameter should be in key=value format)
     * @param fileLocations      The array of HTTP URIs specifying the location of the file for the service to retrieve.
     * @param isRawDataExists    The array of flags specifying the existing of raw data.
     * @param partParameters     The array of String parameters (each parameter should be in key=value format)
     * @param callbackUri        The URI of the web service to call back to when jobs are finished.
     * @param priority           The priority of the job
     * @return The processId of this Rendering job
     */
    @WebMethod
    int renderLater(@WebParam(name = "creatorId", targetNamespace = TARGET_NAMESPACE) int creatorId,
                    @WebParam(name = "jobParameters", targetNamespace = TARGET_NAMESPACE) String[] jobParameters,
                    @WebParam(name = "fileLocations", targetNamespace = TARGET_NAMESPACE) URI[] fileLocations,
                    @WebParam(name = "isRawDataExists", targetNamespace = TARGET_NAMESPACE) boolean[] isRawDataExists,
                    @WebParam(name = "partParameters", targetNamespace = TARGET_NAMESPACE) String[] partParameters,
                    @WebParam(name = "callbackUri", targetNamespace = TARGET_NAMESPACE) URI callbackUri,
                    @WebParam(name = "priority", targetNamespace = TARGET_NAMESPACE) int priority);

    /**
     * It clears files in rendering repository
     * @param renderId The identifier of the rendering process
     */
    @WebMethod
    void clearRenderingResult(@WebParam(name = "renderId", targetNamespace = TARGET_NAMESPACE) int renderId);

    /**
     * A service that takes an XML request for rendering.
     * @param creatorId          The identifier of creator of this job
     * @param jobParameters      The array of String parameters (each parameter should be in key=value format)
     * @param fileLocations      The array of HTTP URIs specifying the location of the file for the service to retrieve.
     * @param partParameters     The array of String parameters (each parameter should be in key=value format)
     * @param callbackUri        The URI of the web service to call back to when jobs are finished.
     * @return The processId of this Rendering job
     */
    @WebMethod
    int renderPlan(@WebParam(name = "creatorId", targetNamespace = TARGET_NAMESPACE) int creatorId,
               @WebParam(name = "jobPlan", targetNamespace = TARGET_NAMESPACE) String jobPlan,
               @WebParam(name = "jobParameters", targetNamespace = TARGET_NAMESPACE) String[] jobParameters,
               @WebParam(name = "fileLocations", targetNamespace = TARGET_NAMESPACE) URI[] fileLocations,
               @WebParam(name = "partParameters", targetNamespace = TARGET_NAMESPACE) String[] partParameters,
               @WebParam(name = "callbackUri", targetNamespace = TARGET_NAMESPACE) URI callbackUri);
}

