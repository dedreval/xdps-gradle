package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.services.editorialuirestservice.model.ProfileData;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import com.wiley.tes.util.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

class RestUtil {

    static final int HUNDRED = 100;
    static final int COOKIE_EXPIRATION_TIME = 1800;
    static final int MAX_ZONE_HOURS = 14;
    static final int MIN_ZONE_HOURS = -12;
    static final int MAX_MINS = 59;
    static final String TOKEN_NAME = "X-WPP-AUTH-TOKEN";
    static final String ROLE_KEY = "key";
    static final String COCHRANE_EDITORIAL_EDITOR = "COCHRANE_EDITORIAL_EDITOR";
    static final String XDPS_ADMIN = "XDPS_ADMIN";
    static final String TRANSACTION_ID = "Transaction-ID";
    static final String USER_INFO = "User-Info";
    static final String EMAIL = "email";
    static final String PASSWORD = "password";
    static final String GIVEN_NAME = "givenName";
    static final String FAMILY_NAME = "familyName";
    static final String MESSAGE = "message";
    static final String PREFERENCE_VALUE = "preferenceValue";
    static final String PARTICIPANT_ID_CLAIM = "ptpid";
    static final String PARTICIPANT_ID = "Participant-Id";
    static final String PREFERENCES = "/preferences/";
    static final String EXTERNAL_USER_VALIDATION_USED = "External-User-Validation-Used";
    static final String PROFILE_DATA = "Profile-Data";
    static final String TIME_ZONE_OFFSET = "timeZoneOffset";
    static final String DEFAULT_TIME_ZONE = "(GMT-00:00) Africa/Abidjan";
    static final String GMT_ID = "GMT";
    static final String AUTH_FAILED = "Authentication failed";
    static final String CONTENT_DISPOSITION = "Content-Disposition";
    static final String LOGOUT = "logout";
    static final String EXCEPTION_TEMPLATE = "%s %s";

    static final Map<String, ProfileData> PROFILES_MAP = new HashMap<>();
    static final String[] COCHRANE_USER_DETAILS = new String[]{"John", "Hilton", "jhilton@cochrane.org"};

    static final Logger LOG_EAPI = Logger.getLogger("EUIApi");

    private static final String AUTHSVC_UNAUTHORIZED = "AUTHSVC_UNAUTHORIZED";

    private RestUtil() {
    }

    static Response handleAllExceptions(Exception e, String path, UUID transactionId) {
        return (e instanceof AuthenticationException)
                       ? handleAuthenticationException((AuthenticationException) e, transactionId)
                       : handleDefaultAndServerError(e, path, transactionId);
    }

    private static Response handleAuthenticationException(AuthenticationException e, UUID transactionId) {
        setAuthenticationException(e, transactionId);
        LOG_EAPI.error(String.format(EXCEPTION_TEMPLATE, e.getMessage(), e));
        return Response.status(Response.Status.UNAUTHORIZED)
                       .entity(e.toString())
                       .header(TRANSACTION_ID, transactionId)
                       .build();
    }

    static void setAuthenticationException(AuthenticationException ae, UUID transactionId) {
        ae.setErrorCode(AUTHSVC_UNAUTHORIZED);
        ae.setRefId(transactionId);
    }

    static Response handleDefaultAndServerError(Exception e, String path, UUID transactionId) {
        return (e instanceof DefaultException)
                       ? handleDefaultException((DefaultException) e, path, transactionId) : handleServerError(e);
    }

    private static Response handleDefaultException(DefaultException de, String path, UUID transactionId) {
        setDefaultException(de, path);
        LOG_EAPI.error(de.toString());
        return Response.status(Response.Status.BAD_REQUEST)
                       .entity(de.toString())
                       .header(TRANSACTION_ID, transactionId)
                       .build();
    }

    static void setDefaultException(DefaultException de, String path) {
        de.setTimestamp(new Timestamp(System.currentTimeMillis()).toInstant());
        de.setStatus(Response.Status.BAD_REQUEST.getStatusCode());
        de.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
        de.setException(de.getClass().getSimpleName());
        de.setPath(path);
    }

    private static Response handleServerError(Exception e) {
        LOG_EAPI.error(e.toString());
        return Response.serverError()
                       .entity(e.toString())
                       .build();
    }

    static String getFullURL(HttpServletRequest request) {
        StringBuilder requestURL = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        return queryString == null ? requestURL.toString() : requestURL.append('?').append(queryString).toString();
    }
}