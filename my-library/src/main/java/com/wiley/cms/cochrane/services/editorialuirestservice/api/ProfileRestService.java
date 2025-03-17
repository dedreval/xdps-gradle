package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationSuccessResponse;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.ProfileData;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Stateless
@Path("/{api:(?i)api}/{profile:(?i)profile}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ProfileRestService {

    private AuthClient authClient = new AuthClient();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get user profile data", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"profile"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile Data Response",
                    content = @Content(schema = @Schema(implementation = ProfileData.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response getUserProfileData(@Context HttpServletRequest req) {
        try {
            boolean isExternalValidationUsed = (boolean) req.getSession(false)
                                                                 .getAttribute(RestUtil.EXTERNAL_USER_VALIDATION_USED);
            ProfileData profileData;
            if (isExternalValidationUsed) {
                profileData = (ProfileData) req.getSession(false).getAttribute(RestUtil.PROFILE_DATA);
            } else {
                String email = ((AuthenticationSuccessResponse) req.getSession(false)
                                                                        .getAttribute(RestUtil.USER_INFO)).getEmail();
                profileData = RestUtil.PROFILES_MAP.get(email);
            }
            String jsonResponse = authClient.getMapper().writerWithDefaultPrettyPrinter()
                                          .writeValueAsString(profileData);
            RestUtil.LOG_EAPI.info("Current ProfileData " + jsonResponse);
            return Response.ok(jsonResponse)
                           .build();
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set user profile data", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"profile"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile Data Response",
                    content = @Content(schema = @Schema(implementation = ProfileData.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response setUserProfileData(@Parameter(required = true) String jsonRequest,
                                       @Context HttpServletRequest req) {
        try {
            boolean isExternalValidationUsed = (boolean) req.getSession(false)
                                                                 .getAttribute(RestUtil.EXTERNAL_USER_VALIDATION_USED);
            ProfileData requestData = authClient.getMapper().readValue(jsonRequest, ProfileData.class);
            String timeZoneOffset = requestData.getTimeZoneOffset();
            validateTimeZoneOffset(timeZoneOffset);

            ProfileData profileData;
            if (isExternalValidationUsed) {
                profileData = (ProfileData) req.getSession(false).getAttribute(RestUtil.PROFILE_DATA);
                profileData.setTimeZoneOffset(timeZoneOffset);
                req.getSession(false).setAttribute(RestUtil.PROFILE_DATA, profileData);

                String participantId = (String) req.getSession(false).getAttribute(RestUtil.PARTICIPANT_ID);
                authClient.callPrtsAPICreateOrUpdateUserPreference(participantId, timeZoneOffset);
            } else {
                String userEmail = ((AuthenticationSuccessResponse)
                                            req.getSession(false).getAttribute(RestUtil.USER_INFO)).getEmail();
                profileData = RestUtil.PROFILES_MAP.get(userEmail);
                profileData.setTimeZoneOffset(timeZoneOffset);
            }
            String jsonResponse = authClient.getMapper().writerWithDefaultPrettyPrinter()
                                          .writeValueAsString(profileData);
            RestUtil.LOG_EAPI.info("Updated ProfileData " + jsonResponse);
            return Response.ok(jsonResponse)
                           .build();
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    private void validateTimeZoneOffset(String tZOffset) throws Exception {
        String[] tZParts = tZOffset.replaceAll("[()]", "").split(" ");
        if (tZParts.length != 2) {
            throw new DefaultException(String.format("The timezone '%s' is incorrect", tZOffset));
        }

        TimeZone offset = TimeZone.getTimeZone(tZParts[0]);
        String tzId = offset.getID();
        if (RestUtil.GMT_ID.equals(tzId) && !tzId.equals(tZParts[0])) {
            throw new DefaultException(String.format("The timezone '%s' has incorrect offset Id", tZOffset));
        }

        long hours = TimeUnit.MILLISECONDS.toHours(offset.getRawOffset());
        long minutes = Math.abs(TimeUnit.MILLISECONDS.toMinutes(offset.getRawOffset())
                                        - TimeUnit.HOURS.toMinutes(hours));
        if (hours > RestUtil.MAX_ZONE_HOURS || hours < RestUtil.MIN_ZONE_HOURS || minutes > RestUtil.MAX_MINS
                    || (hours == RestUtil.MAX_ZONE_HOURS && minutes != 0)
                    || (hours == RestUtil.MIN_ZONE_HOURS && minutes != 0)) {
            throw new DefaultException(String.format("The timezone '%s' is out of range (UTC-12:00 to UTC+14:00)",
                                                     tZOffset));
        }

        boolean isLocationValid = Arrays.asList(TimeZone.getAvailableIDs()).contains(tZParts[1]);
        if (!isLocationValid) {
            throw new DefaultException(String.format("The timezone '%s' has incorrect location Id", tZOffset));
        }
    }
}