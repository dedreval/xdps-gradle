package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.WebResource;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.res.ConnectionType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.AbstractFactory;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.http.JerseyHttpClient;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/10/2018
 */
public class HWClient extends JerseyHttpClient {
    private static final Logger LOG = Logger.getLogger(HWClient.class);

    private static final String IMPORT_URL = "/import";
    private static final String DELETE_URL = "/delete";

    private WebResource webResourceDel;
    private String basePath;

    public HWClient() {
        this(PublishProfile.getProfile().get().getPubLocation(
                PubType.TYPE_SEMANTICO, null, false, false).getConnectionType());
    }

    private HWClient(ConnectionType connection) {
        super(connection, IMPORT_URL, CochraneCMSPropertyNames.getHWClientImitateError() > 0,
                CochraneCMSPropertyNames.isSemanticoDebugMode());
    }

    @Override
    protected void init(ConnectionType connection) {
        super.init(connection);

        basePath = connection.getUrl();
        setWebResourceDel(basePath, connection.getTimeout());
    }

    @Override
    protected void onImitateError(int status, String title) throws CmsException {

        int errStatus = CochraneCMSPropertyNames.getHWClientImitateError();
        String msg = String.format("This is a error simulation test. A real response code was %d\n"
            + "Just set 'cms.cochrane.revman.publish.semantico.imitateError=0' to go through.", status);

        throw new CmsException(new ErrorInfo<>(msg, !is500Status(errStatus) ? ErrorInfo.Type.SYSTEM
                        : ErrorInfo.Type.HTTP_500_SYSTEM , msg));
    }

    @Override
    protected void onHandleError(int status, String title, String response) throws CmsException {
        throw new CmsException(new ErrorInfo<>(response, !is500Status(status) ? ErrorInfo.Type.SYSTEM
                : ErrorInfo.Type.HTTP_500_SYSTEM , response));
    }

    @Override
    protected WebResource getWebResourceDel(String path, int timeout) {
        return webResourceDel;
    }

    @Override
    protected WebResource getWebResource(String path, int timeout) {
        return webResource;
    }

    protected void setWebResourceDel(String webResourcePath, int timeout) {
        webResourceDel = getClient(timeout).resource(webResourcePath + DELETE_URL);
    }

    /*public boolean testCall() {
        HttpOperation op = getGetOperation();
        try {
            op.performOperationThrowingException();
            return true;

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }*/

    public HWResponse sendPackage(HWMsg msg) throws Exception {
        String msgBody = msg.asJSONString();
        HttpOperation op = getPostOperation(msgBody);
        try {
            op.performOperationThrowingException();
            return op.getResult() != null ? HWResponse.create(op.getResult()) : null;
        } catch (CmsException e) {
            addBodyToError(e, msgBody);
            throw e;
        }
    }

    public String deleteDois(HWMsg msg) throws Exception {
        String msgBody = msg.asJSONString();
        HttpOperation op = getDelOperation(msg.asJSONString());
        try {
            op.performOperationThrowingException();
            return op.getResult();

        } catch (CmsException e) {
            addBodyToError(e, msgBody);
            throw e;
        }
    }

    private HttpOperation getGetOperation() {
        return new HttpOperation(getPath(), 2, OK_GET_CODES, "", MediaType.APPLICATION_JSON_TYPE) {
            @Override
            protected void perform() throws Exception {
                result = getObject((String) params[0], (MediaType) params[1], null);
            }

            @Override
            protected void onTrustStoreChanged() {
                setWebResource(getPath(), timeout);
            }
        };
    }

    private HttpOperation getPostOperation(String object) {
        return new HttpOperation(getPath(), 2, OK_POST_CODES, "", object, MediaType.APPLICATION_JSON_TYPE) {
            @Override
            protected void perform() throws Exception {
                result = postObject((String) params[0], params[1], (MediaType) params[2]);
            }

            @Override
            protected void onTrustStoreChanged() {
                setWebResource(getPath(), timeout);
            }
        };
    }

    private HttpOperation getDelOperation(String object) {
        return new HttpOperation(getDeletePath(), 2, OK_DEL_CODES, "", object, MediaType.APPLICATION_JSON_TYPE) {
            @Override
            protected void perform() throws Exception {
                result = deleteObject((String) params[0], params[1], (MediaType) params[2]);
            }

            @Override
            protected void onTrustStoreChanged() {
                setWebResourceDel(getDeletePath(), timeout);
            }
        };
    }

    @Override
    public String toString() {
        return basePath;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    String getDeletePath() {
        return webResourceDel.toString();
    }

    String getPath() {
        return webResource.toString();
    }

    /**
     * Just factory
     */
    public static class Factory extends AbstractFactory<HWClient> {
        private static final Factory INSTANCE = new Factory();

        Map<String, HWClient> clients = new HashMap<>();

        private Factory() {
            init();
        }

        HWClient getHWClient(PublishProfile.PubLocationPath path) {
            return clients.computeIfAbsent(path.getParentMajorType(), f -> new HWClient(path.getConnectionType()));
        }

        @Override
        protected void init() {
            try {
                clients.clear();
                bean = getHWClient(PublishProfile.getProfile().get().getPubLocation(
                        PubType.TYPE_SEMANTICO, null, false, false));
            } catch (Throwable th) {
                LOG.error(th.getStackTrace(), th);
            }
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}
