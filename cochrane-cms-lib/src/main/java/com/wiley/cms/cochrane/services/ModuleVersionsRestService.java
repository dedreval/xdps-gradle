package com.wiley.cms.cochrane.services;

import com.wiley.cms.cochrane.cmanager.VersionInfo;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 06.09.19
 */

@Path("/")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ModuleVersionsRestService {

    private static final Logger LOG = Logger.getLogger(ModuleVersionsRestService.class);

    private static final String CMS = "CMS Module";
    private static final String QAS = "QAS Module";
    private static final String RND = "RND Module";
    private static final String EDITORIAL_API = "Editorial API";
    private static final String NO_VERSION_FOUND = "No version found";

    @GET
    @Path("/qas-version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getQASVersion() {
        String ret = NO_VERSION_FOUND;
        try {
            ret = getString(QAS, WebServiceUtils.getProvideQa().getVersion());
        } catch (IOException e) {
            LOG.error(e, e);
        }
        return ret;
    }

    @GET
    @Path("/rnd-version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getRenderingVersion() {
        String ret = NO_VERSION_FOUND;
        try {
            ret = getString(RND, WebServiceUtils.getProvideRendering().getVersion());
        } catch (IOException e) {
            LOG.error(e, e);
        }
        return ret;
    }

    @GET
    @Path("/cms-version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getCMSVersion() {
        String ret = NO_VERSION_FOUND;
        try {
            ret = getString(CMS, VersionInfo.getVersion(false));
        } catch (IOException e) {
            LOG.error(e, e);
        }
        return ret;
    }

    @GET
    @Path("/editorial-api-version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getEditorialAPIVersion() {
        String ret = NO_VERSION_FOUND;
        try {
            ret = getString(EDITORIAL_API, VersionInfo.getVersion(true));
        } catch (IOException e) {
            LOG.error(e, e);
        }
        return ret;
    }

    private String getString(String module, String version) {
        return String.format("{\n  \"version\": \"%s: %s\"\n}", module, version);
    }
}