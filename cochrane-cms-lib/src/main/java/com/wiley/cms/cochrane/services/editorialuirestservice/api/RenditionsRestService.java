package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.Pdf;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 27.11.19
 */

@Stateless
@Path("/{api:(?i)api}/{renditions:(?i)renditions}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RenditionsRestService {

    private static final Logger LOG = Logger.getLogger(RenditionsRestService.class);

    @GET
    @Path("/{objectId}")
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    @Operation(summary = "Get rendition as an octet-stream", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"editorials"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns rendition object as a binary PDF",
                    content = @Content(schema = @Schema(implementation = Pdf.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response getRenditions(@PathParam("objectId") String editorialId,
                                  @Context HttpServletRequest req) {
        try {
            String edNumber = editorialId.toUpperCase();
            String pdfFilePath = RepositoryFactory.getRepository().getRealFilePath(
                    FilePathBuilder.PDF.getPathToEntirePdfFopRecord(CochraneCMSPropertyNames.getEditorialDbName(),
                                                                    edNumber));
            File pdfFile = new File(pdfFilePath);
            if (!pdfFile.isFile()) {
                throw new DefaultException("No link to PDF found");
            }

            FileInputStream pdfFileStream = new FileInputStream(pdfFile);
            LOG.info("Getting PDF file...");
            return Response.ok(pdfFileStream)
                           .type("application/pdf")
                           .header(RestUtil.CONTENT_DISPOSITION, "filename=" + edNumber + Extensions.PDF)
                           .build();
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }
}
