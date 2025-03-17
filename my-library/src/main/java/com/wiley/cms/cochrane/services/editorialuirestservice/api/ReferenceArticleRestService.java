package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishScheduler;
import com.wiley.cms.cochrane.services.editorialuirestservice.api.annotations.PATCH;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.AuthenticationSuccessResponse;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.Editorial;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.EditorialDetailed;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.EditorialPatch;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.Pdf;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.PdfFormat;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Stateless
@Path("/{api:(?i)api}/{referenceArticle:(?i)referenceArticle}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ReferenceArticleRestService {

    private final Res<Property> clientZone = Property.get("cms.cochrane.remote-client.timezone");
    private final Res<Property> timeFormat = Property.get("cms.cochrane.remote-client.time-format");
    private ObjectMapper mapper = new ObjectMapper();

    @EJB(beanName = "PublishScheduler")
    private IPublishScheduler manager;

    @GET
    @Path("/{objectId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get detailed editorial data", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"editorials"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Editorial Detailed Data",
                    content = @Content(schema = @Schema(implementation = EditorialDetailed.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response getReferenceArticleDetails(@PathParam("objectId") String editorialId,
                                               @Context HttpServletRequest req) {
        Exception err;
        try {
            String edNumber = editorialId.toUpperCase();

            String pattern = timeFormat.exist() ? timeFormat.get().getValue() : Now.DATE_TIME_FORMAT_OUT;
            ZoneId zoneId = clientZone.exist() ? TimeZone.getTimeZone(clientZone.get().getValue()).toZoneId()
                    : Now.UTC_ZONE;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

            String[] ret = manager.findScheduledSending(edNumber, DatabaseEntity.EDITORIAL_KEY, formatter, zoneId);

            EditorialDetailed editorialDetailed = getEditorialDetailed(edNumber, ret[0]);
            editorialDetailed.setPublicationDate(ret[1]);

            String jsonResponse = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(editorialDetailed);
            RestUtil.LOG_EAPI.info("Article Details " + jsonResponse);
            return Response.ok(jsonResponse)
                           .build();
        } catch (CmsException ce) {
            err = new DefaultException(ce.getMessage());
        } catch (DefaultException de) {
            err = de;
        } catch (Throwable tr) {
            err = new Exception(tr);
        }
        return RestUtil.handleAllExceptions(err, RestUtil.getFullURL(req),
                                            (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
    }

    @PATCH
    @Path("/{editorialId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Setup the planned 'publicationDate'", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"editorials"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Editorial Detailed Data",
                    content = @Content(schema = @Schema(implementation = EditorialDetailed.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response setReferenceArticlePublishDate(@PathParam("editorialId") String editorialId,
                                                   @Parameter(required = true) String jsonRequest,
                                                   @Context HttpServletRequest req) {
        RestUtil.LOG_EAPI.info("request: " + jsonRequest);
        Exception err;
        try {
            EditorialPatch editorialPatch = mapper.readValue(jsonRequest, EditorialPatch.class);
            String edNumber = editorialId.toUpperCase();

            String scheduledDate = editorialPatch.getPublicationDate();
            if (scheduledDate != null) {
                scheduledDate = scheduledDate.trim();
            }
            boolean cancel = scheduledDate == null || scheduledDate.length() == 0;

            String pattern = timeFormat.exist() ? timeFormat.get().getValue() : Now.DATE_TIME_FORMAT_OUT;
            ZoneId zoneId = clientZone.exist() ? TimeZone.getTimeZone(clientZone.get().getValue()).toZoneId()
                    : Now.UTC_ZONE;
            String utcPattern = check4UTC(scheduledDate, pattern);
            if (utcPattern != null) {
                zoneId = Now.UTC_ZONE;
                pattern = utcPattern;
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            String userEmail = ((AuthenticationSuccessResponse) req.getSession(false)
                                                                    .getAttribute(RestUtil.USER_INFO)).getEmail();
            String[] ret = cancel ? manager.cancelSending(edNumber, DatabaseEntity.EDITORIAL_KEY, userEmail)
                    : manager.scheduleSending(edNumber, DatabaseEntity.EDITORIAL_KEY, scheduledDate,
                                              formatter, zoneId, userEmail);

            String unitTitle = ret[0];
            String publicationDate = ret[1];

            EditorialDetailed editorialDetailed = getEditorialDetailed(edNumber, unitTitle);
            editorialDetailed.setPublicationDate(publicationDate);

            String jsonResponse = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(editorialDetailed);
            RestUtil.LOG_EAPI.info("Article Details with new publish date " + jsonResponse);
            return Response.ok(jsonResponse)
                           .build();
        } catch (CmsException ce) {
            err = new DefaultException(ce.getMessage());
        } catch (DefaultException de) {
            err = de;
        } catch (Throwable tr) {
            err = new Exception(tr);
        }
        return RestUtil.handleAllExceptions(err, RestUtil.getFullURL(req),
                                            (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
    }

    private EditorialDetailed getEditorialDetailed(String editorialId, String editorialTitle) {
        List<Pdf> pdfList = Collections.singletonList(getPdf(editorialId));
        return new EditorialDetailed(editorialId, editorialTitle, Editorial.ModelType.REFERENCE_ARTICLE, pdfList);
    }

    private Pdf getPdf(String editorialId) {
        return new Pdf(editorialId, editorialId + Extensions.PDF, Pdf.ModelType.RENDITION,
                       new PdfFormat(PdfFormat.Mnemonic.PDF));
    }

    private String check4UTC(String scheduledDate, String pattern) {
        return scheduledDate != null && scheduledDate.endsWith("Z") && !pattern.endsWith("Z'") ? pattern + "'Z'" : null;
    }
}
