package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationSuccessResponse;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

import static com.wiley.cms.cochrane.services.editorialuirestservice.api.RestUtil.LOG_EAPI;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Stateless
@Path("/{api:(?i)api}/{authenticate:(?i)authenticate}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AuthRestService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Endpoint for authentication", tags = {"authentication"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication succeeded",
                    content = @Content(schema = @Schema(implementation = AuthenticationSuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response authenticateUser(@Context HttpServletRequest req, @Context HttpServletResponse resp) {
        try {
            HttpSession session = req.getSession(false);
            String jsonResponse = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                    session.getAttribute(RestUtil.USER_INFO));

            String tokenValueFromSession = (String) session.getAttribute(HttpHeaders.AUTHORIZATION);
            setTokenCookie(resp, tokenValueFromSession);
            LOG_EAPI.info("Authentication succeeded " + jsonResponse);

            return Response.ok(jsonResponse)
                           .build();
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    private void setTokenCookie(@Context HttpServletResponse resp, String tokenValue) {
        Cookie cookie = new Cookie(RestUtil.TOKEN_NAME, tokenValue);
        cookie.setMaxAge(RestUtil.COOKIE_EXPIRATION_TIME);
        cookie.setPath("/");
        resp.addCookie(cookie);
    }
}