package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.Editorial;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.EditorialsListWithPagination;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.Issue;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.IssueList;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.AuthenticationException;
import com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions.DefaultException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Stateless
@Path("/{api:(?i)api}/{issues:(?i)issues}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class IssuesRestService {

    private IEntireDBStorage edbs = EntireDBStorageFactory.getFactory().getInstance();
    private ObjectMapper mapper = new ObjectMapper();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of issues", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"issues"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of Issues",
                    content = @Content(schema = @Schema(implementation = IssueList.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = DefaultException.class)))
        })
    public Response getIssuesList(@Context HttpServletRequest req) {
        try {
            List<Integer> issuesWithEditorials = edbs.findIssuesWithEditorials();
            if (issuesWithEditorials.isEmpty()) {
                throw new DefaultException("No Issues found");
            } else {
                IssueList issueList = new IssueList(getIssues(issuesWithEditorials));
                String jsonResponse = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(issueList);
                RestUtil.LOG_EAPI.info("List of Issues " + jsonResponse);
                return Response.ok(jsonResponse)
                               .build();
            }
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    private List<Issue> getIssues(List<Integer> issuesWithEditorials) {
        List<Issue> issues = new ArrayList<>();
        issuesWithEditorials.forEach(issueId -> issues.add(new Issue(
                String.valueOf(issueId),
                getIssueName(issueId),
                Issue.ModelType.ISSUE,
                issueId.equals(issuesWithEditorials.get(0)) ? Issue.State.LAST_ISSUE : Issue.State.ACTIVE)));
        return issues;
    }

    private String getIssueName(int issueId) {
        return String.format("Issue %d, %d", issueId % RestUtil.HUNDRED, issueId / RestUtil.HUNDRED);
    }

    @GET
    @Path("/{objectId}/{hasArticleProduct:(?i)hasArticleProduct}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of editorials", security = {
            @SecurityRequirement(name = "cookieAuth")}, tags = {"editorials"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of Editorials",
                    content = @Content(schema = @Schema(implementation = EditorialsListWithPagination.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = AuthenticationException.class))),
            @ApiResponse(responseCode = "400", description = "Error",
                    content = @Content(schema = @Schema(implementation = Exception.class)))
        })
    public Response getEditorialsListByIssue(@PathParam("objectId") String issueId,
                                             @NotNull @QueryParam("size") Integer size,
                                             @NotNull @QueryParam("offset") Integer offset,
                                             @Context HttpServletRequest req) {
        try {
            int issueFullNum = parseIssueId(issueId);
            int allEditorialByIssue = edbs.getEditorialCountRecordsByIssue(issueFullNum);
            List<EntireDBEntity> editorialsByIssue = edbs.getEditorialRecordsByIssue(issueFullNum, size, offset);
            if (editorialsByIssue.isEmpty()) {
                throw new DefaultException(String.format("No Editorial articles found in Issue '%d' "
                                                                 + "or Issue does not exist", issueFullNum));
            } else {
                EditorialsListWithPagination editorialsList =
                        new EditorialsListWithPagination(getEditorials(editorialsByIssue), offset, allEditorialByIssue);
                String jsonResponse = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(editorialsList);
                RestUtil.LOG_EAPI.info("List of Editorials by Issue " + jsonResponse);
                return Response.ok(jsonResponse)
                               .build();
            }
        } catch (Exception e) {
            return RestUtil.handleAllExceptions(e, RestUtil.getFullURL(req),
                                                (UUID) req.getAttribute(RestUtil.TRANSACTION_ID));
        }
    }

    private List<Editorial> getEditorials(List<EntireDBEntity> editorialsByIssue) {
        List<Editorial> editorials = new ArrayList<>(editorialsByIssue.size());
        editorialsByIssue.forEach(record -> editorials.add(new Editorial(
                record.getName(),
                record.getUnitTitle(),
                Editorial.ModelType.REFERENCE_ARTICLE)));
        return editorials;
    }

    private Integer parseIssueId(String id) throws DefaultException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ne) {
            throw new DefaultException(String.format("Can not parse %s to integer", id));
        }
    }
}