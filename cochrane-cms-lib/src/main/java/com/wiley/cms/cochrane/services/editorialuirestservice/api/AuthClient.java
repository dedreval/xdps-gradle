package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationRequestBody;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.tes.util.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.CollectionType;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 18.10.19
 */

class AuthClient {

    private static final Logger LOG = Logger.getLogger(AuthClient.class);
    private final String userAuthServiceUrl =
            CochraneCMSProperties.getProperty("cochrane.auth.api.user_auth.service.url");
    private final String tokenValServiceUrl =
            CochraneCMSProperties.getProperty("cochrane.auth.api.token_validate.service.url");
    private final String participantsServiceUrl =
            CochraneCMSProperties.getProperty("cochrane.participants.api.service.url");
    private final String rolesServiceUrl = CochraneCMSProperties.getProperty("cochrane.roles.api.service.url");
    private final boolean isExternalUserValidationUsed = CochraneCMSPropertyNames.isExternalUserValidationUsed();

    private Client client;
    private ObjectMapper mapper;

    AuthClient() {
        client = Client.create();
        mapper = new ObjectMapper();
    }

    ClientResponse callAuthAPIUserAuthentication(AuthenticationRequestBody authRequestBody) throws Exception {
        LOG.info("Calling Authentication API for user authentication...");
        ClientResponse response = client.resource(userAuthServiceUrl)
                                          .accept(MediaType.APPLICATION_JSON)
                                          .type(MediaType.APPLICATION_JSON)
                                          .post(ClientResponse.class, authRequestBody);
        checkAPIResponse(response);
        return response;
    }

    void callAuthAPITokenValidation(String token) throws Exception {
        if (isExternalUserValidationUsed) {
            LOG.info("Calling Authentication API for token validation...");
            ClientResponse response = client.resource(tokenValServiceUrl)
                                              .header(RestUtil.TOKEN_NAME, token)
                                              .type(MediaType.APPLICATION_JSON)
                                              .get(ClientResponse.class);
            checkAPIResponse(response);
        }
    }

    void callRolesAPI(String participantId) throws Exception {
        LOG.info("Calling Roles API for user role validation...");
        ClientResponse response = client.resource(rolesServiceUrl)
                                          .path(participantId).path("/roles")
                                          .type(MediaType.APPLICATION_JSON)
                                          .get(ClientResponse.class);
        checkAPIResponse(response);
        checkUserRole(response);
    }

    ClientResponse callPrtsAPIUserDetails(String participantId) throws Exception {
        LOG.info("Calling Participants API for user details...");
        ClientResponse response = client.resource(participantsServiceUrl)
                                          .path(participantId)
                                          .type(MediaType.APPLICATION_JSON)
                                          .get(ClientResponse.class);
        checkAPIResponse(response);
        return response;
    }

    ClientResponse callPrtsAPIGetUserPreference(String participantId) {
        LOG.info("Calling Participants API to get user time zone offset...");
        return client.resource(participantsServiceUrl)
                       .path(participantId).path(RestUtil.PREFERENCES).path(RestUtil.TIME_ZONE_OFFSET)
                       .type(MediaType.APPLICATION_JSON)
                       .get(ClientResponse.class);
    }

    void callPrtsAPICreateOrUpdateUserPreference(String participantId, String timeZoneOffsetValue) throws Exception {
        LOG.info("Calling Participants API to create or update user time zone offset...");
        String requestEntity = "{\"preferenceValue\": \"" + timeZoneOffsetValue + "\"}";
        ClientResponse response = client.resource(participantsServiceUrl)
                                          .path(participantId).path(RestUtil.PREFERENCES)
                                          .path(RestUtil.TIME_ZONE_OFFSET)
                                          .accept(MediaType.APPLICATION_JSON)
                                          .type(MediaType.APPLICATION_JSON)
                                          .put(ClientResponse.class, requestEntity);
        checkAPIResponse(response);
    }

    void checkAPIResponse(ClientResponse response) throws Exception {
        if (response.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
            JsonNode jsonBody = mapper.readTree(response.getEntity(String.class));
            String message = !jsonBody.path(RestUtil.MESSAGE).isMissingNode()
                                     ? jsonBody.get(RestUtil.MESSAGE).asText()
                                     : RestUtil.AUTH_FAILED;
            throw new AuthenticationException(message);
        }
    }

    private void checkUserRole(ClientResponse response) throws Exception {
        JsonNode jsonBody = mapper.readTree(response.getEntity(String.class));
        CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
        List<Map<String, Object>> roles = mapper.readValue(jsonBody, type);
        if (roles.isEmpty()) {
            throw new AuthenticationException("User does not have permissions to login!");
        }

        boolean isUserRoleFound = false;
        for (Map<String, Object> roleDetails : roles) {
            if (roleDetails.containsKey(RestUtil.ROLE_KEY)
                        && (roleDetails.get(RestUtil.ROLE_KEY).equals(RestUtil.COCHRANE_EDITORIAL_EDITOR)
                                    || roleDetails.get(RestUtil.ROLE_KEY).equals(RestUtil.XDPS_ADMIN))) {
                isUserRoleFound = true;
                break;
            }
        }

        if (!isUserRoleFound) {
            throw new AuthenticationException("User does not have enough permissions to login!");
        }
    }

    ObjectMapper getMapper() {
        return mapper;
    }

    boolean isExternalUserValUsed() {
        return isExternalUserValidationUsed;
    }
}
