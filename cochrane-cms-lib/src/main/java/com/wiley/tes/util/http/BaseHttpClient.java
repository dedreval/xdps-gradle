package com.wiley.tes.util.http;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpStatus;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.res.ConnectionType;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.SSLChecker;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/12/2014
 */
public abstract class BaseHttpClient {
    protected static final Logger LOG = Logger.getLogger(BaseHttpClient.class);

    protected static final Set<Integer> OK_GET_CODES = new HashSet<>();
    protected static final Set<Integer> OK_PUT_CODES = new HashSet<>();
    protected static final Set<Integer> OK_POST_CODES = new HashSet<>();
    protected static final Set<Integer> OK_DEL_CODES = new HashSet<>();

    final boolean imitateError;
    final boolean debug;

    static {
        OK_GET_CODES.add(HttpStatus.SC_OK);
        OK_GET_CODES.add(HttpStatus.SC_NO_CONTENT);
    }

    static {
        OK_PUT_CODES.add(HttpStatus.SC_OK);
        OK_PUT_CODES.add(HttpStatus.SC_NO_CONTENT);
        OK_PUT_CODES.add(HttpStatus.SC_CREATED);
    }

    static {
        OK_DEL_CODES.add(HttpStatus.SC_OK);
        OK_DEL_CODES.add(HttpStatus.SC_ACCEPTED);
        OK_DEL_CODES.add(HttpStatus.SC_NO_CONTENT);
    }

    static {
        OK_POST_CODES.add(HttpStatus.SC_OK);
        OK_POST_CODES.add(HttpStatus.SC_CREATED);
        OK_POST_CODES.add(HttpStatus.SC_ACCEPTED);
        OK_POST_CODES.add(HttpStatus.SC_NO_CONTENT);
    }

    protected BaseHttpClient(boolean imitateError, boolean debug) {
        this.imitateError = imitateError;
        this.debug = debug;
    }

    protected abstract String getErrorBody(Object response);

    protected void onImitateError(int status, String title) throws CmsException {
        String msg = String.format("This is a error simulation test. A real response code was %d\n", status);
        throw new CmsException(new ErrorInfo<>(msg, ErrorInfo.Type.HTTP_500_SYSTEM , msg));
    }

    protected void onHandleError(int status, String title, String errMsg) throws CmsException {
        throw new CmsException(new ErrorInfo<>(errMsg, !is500Status(status) ? ErrorInfo.Type.SYSTEM
            : ErrorInfo.Type.HTTP_500_SYSTEM , errMsg));
    }

    protected void init(ConnectionType connection) {
    }

    public String putObject(String path, String obj, MediaType type) throws Exception {
        throw new UnsupportedOperationException("PUT method is not supported.");
    }

    public String postObject(String path, Object obj, MediaType type) throws Exception {
        throw new UnsupportedOperationException("POST method is not supported.");
    }

    public String deleteObject(String path, Object obj, MediaType type) throws Exception {
        throw new UnsupportedOperationException("DELETE method is not supported.");
    }

    public String getObject(String path, MediaType type, MultivaluedMap<String, String> map) throws Exception {
        throw new UnsupportedOperationException("GET method is not supported.");
    }

    protected void addBodyToError(CmsException e, String msgBody) {
        LOG.error(e.getMessage());
        ErrorInfo err = e.getErrorInfo();
        if (err == null) {
            return;
        }
        err.setErrorEntity(msgBody);
    }

    private void imitateError(int status, String title) throws CmsException {

        if (!imitateError) {
            return;
        }
        onImitateError(status, title);
    }

    protected void handleError(Object response, int status, String input, String logMsg, Set<Integer> okCodes)
        throws CmsException {

        log(logMsg);
        imitateError(status, input);

        if (!okCodes.contains(status)) {
            String errDetails = getErrorBody(response);
            onHandleError(status, input, errDetails.length() > 0 ? errDetails : logMsg);
        }
    }

    protected void logResponse(Object body) {
        if (body != null) {
            log(body);
        } else {
            log("empty response");
        }
    }

    protected void logRequest(String operation, String path, Object body) {
        log(String.format("[%s] %s\n%s", operation, path, body));
    }
  
    public void logRequest(Object body) {
        log(body);
    }

    protected void log(Object resp) {
        if (debug) {
            log().info(resp);
        }
    }

    protected boolean is500Status(int status) {
        return status >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
    }

    protected Logger log() {
        return LOG;
    }

    /**
     * HttpOperation
     */
    public abstract static class HttpOperation extends RepeatableOperation {
        protected static final Set<Integer> OK_CODES = new HashSet<>();

        static {
            OK_CODES.add(HttpStatus.SC_OK);
            OK_CODES.add(HttpStatus.SC_CREATED);
            OK_CODES.add(HttpStatus.SC_ACCEPTED);
        }

        protected String webResourcePath;
        protected String result;
        protected Set<Integer> okCodes;

        public HttpOperation(String webResourcePath, int count, Set<Integer> okCodes, Object... params) {
            super(count, params);
            this.webResourcePath = webResourcePath;
            this.okCodes = okCodes;
        }

        protected void onTrustStoreChanged() {
        }

        @Override
        protected Exception onNextAttempt(Exception e) {
            if (canNextAttempt(e)) {
                return super.onNextAttempt(e);
            }
            fillCounter();
            return e;
        }

        private boolean canNextAttempt(Exception e) {
            if (e instanceof CmsException) {
                return ((CmsException) e).getErrorInfo().is500ServerError();
            }
            boolean truststoreChanged = SSLChecker.checkCertificate(webResourcePath, e);
            if (truststoreChanged) {
                onTrustStoreChanged();
            }
            return truststoreChanged;
        }

        @Override
        protected Exception onLastAttempt(Exception e) {
            if (e instanceof CmsException) {
                CmsException ce = (CmsException) e;
                if (ce.getErrorInfo().is500ServerError()) {
                    return super.onLastAttempt(new Exception(ce));
                }
            }
            return super.onLastAttempt(e);
        }

        public String getResult() {
            return result;
        }
    }
}
