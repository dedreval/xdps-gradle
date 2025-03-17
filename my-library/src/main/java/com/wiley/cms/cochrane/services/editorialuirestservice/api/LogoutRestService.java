package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import com.wiley.cms.cochrane.utils.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.UUID;

import static com.wiley.cms.cochrane.services.editorialuirestservice.api.RestUtil.LOG_EAPI;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Stateless
@Path("/{api:(?i)api}/{logout:(?i)logout}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LogoutRestService {

    @POST
    @Operation(summary = "Endpoint for authentication", tags = {"authentication"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout succeeded (set header with empty cookies)"),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response logoutUser(@Context HttpServletRequest req, @Context HttpServletResponse resp) {
        try {
            removeCookies(req, resp);

            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }

            LOG_EAPI.info("Logout succeeded");
            return Response.ok()
                           .build();
        } catch (Exception e) {
            return RestUtil.handleDefaultAndServerError(e, RestUtil.getFullURL(req),
                                                        (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    private void removeCookies(@Context HttpServletRequest request, @Context HttpServletResponse resp) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookie.setMaxAge(0);
                cookie.setValue(null);
                cookie.setPath("/");
                if (!cookie.getName().equals(Constants.JSESSIONID) && !cookie.getName().equals(RestUtil.TOKEN_NAME)) {
                    cookie.setDomain(Constants.WILEY_DOMAIN);
                }
                resp.addCookie(cookie);
            }
        }
    }
}