package com.wiley.tes.util.http;

import java.net.URI;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpStatus;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.multipart.impl.MultiPartWriter;
import com.wiley.cms.cochrane.cmanager.res.ConnectionType;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/12/2014
 */
public abstract class JerseyHttpClient extends BaseHttpClient {
    protected static final Logger LOG = Logger.getLogger(JerseyHttpClient.class);

    protected WebResource webResource;

    protected final int timeout;
    String auth = null;

    protected JerseyHttpClient(ConnectionType connection, String postfix, boolean imitateError, boolean debug) {
        this(connection.getUrl() + postfix, connection.getTimeout(), imitateError, debug);
        init(connection);
    }

    protected JerseyHttpClient(String webResourcePath, int timeout, boolean imitateError, boolean debug) {
        super(imitateError, debug);
        this.timeout = timeout;
        setWebResource(webResourcePath, timeout);
        LOG.info(String.format("http client is initialized for HW: %s", webResourcePath));
    }

    public URI getURI() {
        return webResource.getURI();
    }

    @Override
    protected void init(ConnectionType connection) {
        auth = connection.getAuthorization();
    }

    protected void setWebResource(String webResourcePath, int timeout) {
        webResource = getClient(timeout).resource(webResourcePath);
    }

    @Override
    public String putObject(String path, String obj, MediaType type) throws Exception {
        WebResource wr = getWebResource(path, timeout);
        logRequest(HttpMethod.PUT, wr.toString(), obj);
        ClientResponse resp = wr.type(type).put(ClientResponse.class, obj);
        String ret = getResponseBody(resp);
        logResponse(ret);
        handleError(ret, resp.getStatus(), wr.toString(), resp.toString(), OK_PUT_CODES);
        return ret;
    }

    @Override
    public String postObject(String path, Object obj, MediaType type) throws Exception {
        WebResource wr = getWebResource(path, timeout);
        logRequest(HttpMethod.POST, wr.toString(), obj);
        ClientResponse resp = prepare(wr, type).post(ClientResponse.class, obj);
        String ret = getResponseBody(resp);
        logResponse(ret);
        handleError(ret, resp.getStatus(), wr.toString(), resp.toString(), OK_POST_CODES);
        return ret;
    }

    public void deleteObject(String path, MediaType type) throws Exception {
        WebResource wr = getWebResourceDel(path, timeout);
        logRequest(HttpMethod.DELETE, wr.toString(), "");
        ClientResponse resp = prepare(wr, type).delete(ClientResponse.class);
        int status = resp.getStatus();

        if (status != HttpStatus.SC_NO_CONTENT) {
            handleError(getResponseBody(resp), status, wr.toString(), resp.toString(), OK_DEL_CODES);
        } else {
            log(resp);
        }
    }

    @Override
    public String deleteObject(String path, Object obj, MediaType type) throws Exception {

        WebResource wr = getWebResourceDel(path, timeout);
        logRequest(HttpMethod.DELETE, wr.toString(), obj);
        ClientResponse resp = prepare(wr, type).delete(ClientResponse.class, obj);
        int status = resp.getStatus();
        String ret = getResponseBody(resp);
        logResponse(ret);
        if (status != HttpStatus.SC_NO_CONTENT) {
            handleError(ret, status, wr.toString(), resp.toString(), OK_DEL_CODES);
        } else {
            log(resp);
        }
        return ret;
    }

    @Override
    public String getObject(String path, MediaType type, MultivaluedMap<String, String> map) throws Exception {
        WebResource wr = getWebResource(path, timeout);
        if (map != null && !map.isEmpty()) {
            wr = wr.queryParams(map);
        }
        logRequest(HttpMethod.GET, wr.toString(), "");
        ClientResponse resp = prepareNoCache(prepare(wr, type)).get(ClientResponse.class);
        String ret = getResponseBody(resp);
        logResponse(ret);
        handleError(ret, resp.getStatus(), wr.toString(), resp.toString(), HttpOperation.OK_CODES);
        return ret;
    }

    private WebResource.Builder prepareNoCache(WebResource.Builder wb) {
        return wb.header(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    private WebResource.Builder prepareAuth(WebResource.Builder wb) {
        return wb.header(HttpHeaders.AUTHORIZATION, auth).header(HttpHeaders.USER_AGENT, "Custom User-Agent");
    }

    private WebResource.Builder prepare(WebResource w, MediaType type) {
        return auth != null ? prepareAuth(prepareNoCache(w.type(type))) : prepareNoCache(w.type(type));
    }

    protected MultivaluedMap<String, String> buildLimit(int limit, int offset) {
        MultivaluedMap<String, String> ret = new MultivaluedMapImpl();
        ret.add("limit", "" + limit);
        if (offset > 0) {
            ret.add("offset", "" + offset);
        }
        return ret;
    }

    private String getResponseBody(Object response) {
        try {
            return ((ClientResponse) response).getEntity(String.class);

        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return response.toString();
        }
    }

    @Override
    protected String getErrorBody(Object response) {
        return response.toString();
    }

    protected WebResource getWebResourceDel(String path, int timeout) {
        return getWebResource(path, timeout);
    }

    protected WebResource getWebResource(String path, int timeout) {
        return path.startsWith("/") ? webResource.path(path) : getClient(timeout).resource(path);
    }

    protected Client getClient(int timeout) {

        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(MultiPartWriter.class);
        config.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, timeout);
        config.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, timeout);

        Client ret = Client.create(config);
        ret.setReadTimeout(timeout);
        ret.setConnectTimeout(timeout);
        return ret;
    }

    @Override
    public String toString() {
        return webResource.toString();
    }
}
