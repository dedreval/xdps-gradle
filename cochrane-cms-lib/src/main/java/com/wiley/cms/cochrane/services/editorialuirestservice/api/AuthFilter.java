package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.sun.jersey.api.client.ClientResponse;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationRequestBody;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationSuccessResponse;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.ProfileData;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 09.09.19
 */

public class AuthFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class);
    private static final List<String> ALLOWED_METHODS = Arrays.asList(HttpMethod.GET, HttpMethod.POST, "PATCH");
    private final String systemId = Property.get("cms.cochrane.auth.system_id").get().getValue();
    private AuthClient authClient;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        authClient = new AuthClient();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;
        req.setAttribute(RestUtil.TRANSACTION_ID, UUID.randomUUID());

        try {
            Cookie[] cookies = req.getCookies();
            HttpSession currentSession = req.getSession(false);
            String token = null;

            if (currentSession != null) {
                token = (String) currentSession.getAttribute(HttpHeaders.AUTHORIZATION);
            }

            boolean isAuthorized = false;
            if (cookies != null && token != null && ALLOWED_METHODS.contains(req.getMethod())) {
                for (Cookie cookie : cookies) {
                    if (tokenAlreadyExists(token, cookie)) {
                        authClient.callAuthAPITokenValidation(token);
                        updateCookie(req, resp, cookie);
                        isAuthorized = true;
                        break;
                    }
                    if (sessionCookieAlreadyExists(cookie)) {
                        updateCookie(req, resp, cookie);
                    }
                }
            }
            if (isAuthorized || req.getPathInfo().endsWith(RestUtil.LOGOUT)) {
                filterChain.doFilter(req, resp);
                return;
            }

            RequestWrapper requestWrapper = new RequestWrapper(req);
            AuthenticationRequestBody authRequestBody = getValidatedRequest(requestWrapper.getBody());

            String newToken;
            AuthenticationSuccessResponse profile;
            ProfileData profileData;
            String participantId = null;
            if (authClient.isExternalUserValUsed()) {
                ClientResponse userAuthDetails = authClient.callAuthAPIUserAuthentication(authRequestBody);
                newToken = userAuthDetails.getHeaders().getFirst(RestUtil.TOKEN_NAME);

                Claims claims = decodeTokenClaims(newToken);
                participantId = (String) claims.get(RestUtil.PARTICIPANT_ID_CLAIM);

                authClient.callRolesAPI(participantId);

                ClientResponse userDetails = authClient.callPrtsAPIUserDetails(participantId);
                profile = composeProfile(userDetails);

                ClientResponse userPreference = authClient.callPrtsAPIGetUserPreference(participantId);
                if (userPreference.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode()) {
                    authClient.callPrtsAPICreateOrUpdateUserPreference(participantId, RestUtil.DEFAULT_TIME_ZONE);
                    userPreference = authClient.callPrtsAPIGetUserPreference(participantId);
                    authClient.checkAPIResponse(userPreference);
                }
                profileData = new ProfileData(getTimeZoneOffset(userPreference));
            } else {
                newToken = String.valueOf(UUID.randomUUID());
                profile = new AuthenticationSuccessResponse(RestUtil.COCHRANE_USER_DETAILS[0],
                                                            RestUtil.COCHRANE_USER_DETAILS[1],
                                                            RestUtil.COCHRANE_USER_DETAILS[2]);
                if (RestUtil.PROFILES_MAP.containsKey(profile.getEmail())) {
                    profileData = RestUtil.PROFILES_MAP.get(profile.getEmail());
                } else {
                    profileData = new ProfileData(RestUtil.DEFAULT_TIME_ZONE);
                    RestUtil.PROFILES_MAP.put(profile.getEmail(), profileData);
                }
            }

            if (currentSession != null) {
                currentSession.invalidate();
            }
            setNewSession(req, newToken, profile, profileData, participantId, authClient.isExternalUserValUsed());
            filterChain.doFilter(req, resp);
        } catch (AuthenticationException ae) {
            RestUtil.setAuthenticationException(ae, (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
            writeExceptionToResponse(req, resp, ae, HttpServletResponse.SC_UNAUTHORIZED);
        } catch (DefaultException de) {
            RestUtil.setDefaultException(de, RestUtil.getFullURL(req));
            writeExceptionToResponse(req, resp, de, HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            writeExceptionToResponse(req, resp, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String getTimeZoneOffset(ClientResponse userPreference) throws IOException {
        JsonNode jsonBody = authClient.getMapper().readTree(userPreference.getEntity(String.class));
        return jsonBody.get(RestUtil.PREFERENCE_VALUE).asText();
    }

    private AuthenticationSuccessResponse composeProfile(ClientResponse userDetails) throws Exception {
        JsonNode jsonBody = authClient.getMapper().readTree(userDetails.getEntity(String.class));
        return new AuthenticationSuccessResponse(jsonBody.get(RestUtil.GIVEN_NAME).asText(),
                                                 jsonBody.get(RestUtil.FAMILY_NAME).asText(),
                                                 jsonBody.get(RestUtil.EMAIL).asText());
    }

    private boolean tokenAlreadyExists(String token, Cookie cookie) {
        return cookie.getName().equals(RestUtil.TOKEN_NAME) && cookie.getValue().equals(token);
    }

    private boolean sessionCookieAlreadyExists(Cookie cookie) {
        return cookie.getName().equals(Constants.JSESSIONID);
    }

    private Claims decodeTokenClaims(String token) {
        String unsignedToken = StringUtils.substring(token, 0, token.lastIndexOf('.') + 1);
        return Jwts.parser().parseClaimsJwt(unsignedToken).getBody();
    }

    private void updateCookie(HttpServletRequest req, HttpServletResponse resp, Cookie cookie) {
        if (!req.getPathInfo().endsWith(RestUtil.LOGOUT)) {
            LOG.info(String.format("Updating %s cookie...", cookie.getName()));
            cookie.setMaxAge(RestUtil.COOKIE_EXPIRATION_TIME);
            cookie.setPath("/");
            resp.addCookie(cookie);
        }
    }

    private AuthenticationRequestBody getValidatedRequest(String jsonRequest) throws Exception {
        LOG.info("Validating request...");
        if (jsonRequest == null || jsonRequest.isEmpty()) {
            throw new AuthenticationException(RestUtil.AUTH_FAILED);
        }

        JsonNode jsonBody = authClient.getMapper().readTree(jsonRequest);
        if (!isEachJsonPropertyValid(jsonBody)) {
            throw new AuthenticationException(RestUtil.AUTH_FAILED);
        }

        if (!isEachJsonValueValid(jsonBody)) {
            throw new DefaultException("Incorrect value(s) in request body");
        }

        String email = jsonBody.get(RestUtil.EMAIL).asText();
        if (!isEmailValid(email)) {
            throw new AuthenticationException(String.format("'%s' is not a valid email address", email));
        }

        return getAuthenticationRequestBody(jsonRequest);
    }

    private AuthenticationRequestBody getAuthenticationRequestBody(String jsonRequest) throws IOException {
        AuthenticationRequestBody authRequestBody = authClient.getMapper()
                                                            .readValue(jsonRequest, AuthenticationRequestBody.class);
        authRequestBody.setSystemId(systemId);
        authRequestBody.setMaxAge(RestUtil.COOKIE_EXPIRATION_TIME);
        return authRequestBody;
    }

    private boolean isEachJsonPropertyValid(JsonNode jsonBody) {
        return !jsonBody.path(RestUtil.EMAIL).isMissingNode() && !jsonBody.path(RestUtil.PASSWORD).isMissingNode();
    }

    private boolean isEachJsonValueValid(JsonNode jsonBody) {
        return !jsonBody.get(RestUtil.EMAIL).asText().isEmpty() && !jsonBody.get(RestUtil.PASSWORD).asText().isEmpty();
    }

    private boolean isEmailValid(String email) {
        try {
            InternetAddress mailAddress = new InternetAddress(email);
            mailAddress.validate();
        } catch (AddressException e) {
            return false;
        }
        return true;
    }

    private void setNewSession(HttpServletRequest req, String newToken, AuthenticationSuccessResponse profile,
                               ProfileData profileData, String participantId, boolean isExternalUserValidationUsed) {
        HttpSession newSession = req.getSession(true);
        newSession.setAttribute(RestUtil.USER_INFO, profile);
        newSession.setAttribute(RestUtil.PROFILE_DATA, profileData);
        newSession.setAttribute(HttpHeaders.AUTHORIZATION, newToken);
        if (participantId != null) {
            newSession.setAttribute(RestUtil.PARTICIPANT_ID, participantId);
        }
        newSession.setAttribute(RestUtil.EXTERNAL_USER_VALIDATION_USED, isExternalUserValidationUsed);
    }

    private void writeExceptionToResponse(HttpServletRequest req, HttpServletResponse resp, Exception e, int status)
            throws IOException {
        if (e instanceof AuthenticationException || e instanceof DefaultException) {
            resp.setContentType(MediaType.APPLICATION_JSON);
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.setHeader(RestUtil.TRANSACTION_ID, req.getAttribute(RestUtil.TRANSACTION_ID).toString());
        }

        resp.setStatus(status);
        PrintWriter writer = resp.getWriter();
        writer.print(e.toString());
        writer.flush();
        RestUtil.LOG_EAPI.error(String.format(RestUtil.EXCEPTION_TEMPLATE, e.getMessage(), e));
    }

    @Override
    public void destroy() {
    }
}
