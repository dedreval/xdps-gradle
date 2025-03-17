package com.wiley.cms.cochrane.process;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.parser.FinishException;
import com.wiley.cms.cochrane.cmanager.parser.ResultParser;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.parser.SourceHandler;
import com.wiley.cms.cochrane.process.handler.RenderingRecordHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/17/2014
 */
public class RenderingHelper {
    private static final Logger LOG = Logger.getLogger(RenderingHelper.class);

    private static final String[][] CENTRAL_TAGS = {
        {ResultParser.CENTRAL_AUTHOR_TAG, ResultParser.CENTRAL_PAGE_TAG, ResultParser.CENTRAL_YEAR_TAG}
    };

    private RenderingHelper() {
    }

    public static boolean isEntire(ProcessHandler handler) {
        return BaseManager.LABEL_RENDERING_ENTIRE.equals(handler.getName());
    }

    public static void addIssueAndYearParams(List<String> jobParams, int issueNumber, boolean isCentral) {

        String[] params = CmsUtils.buildIssueParamString(CmsUtils.getYearByIssueNumber(issueNumber),
            CmsUtils.getIssueByIssueNumber(issueNumber), isCentral);
        jobParams.addAll(Arrays.asList(params));
    }

    static String addPlanParams(List<String> jobParams, int[] planIds, int count, String dbName, boolean fullPdfOnly) {

        String plan;
        if (planIds.length == 1) {
            plan = RenderingPlan.get(planIds[0]).planName;

        } else if (planIds.length == 2) {
            plan = ProcessHelper.buildSubParametersString(RenderingPlan.get(planIds[0]).planName,
                RenderingPlan.get(planIds[1]).planName);

        } else if (planIds.length > 2) {
            plan = ProcessHelper.buildSubParametersString(RenderingPlan.get(planIds[0]).planName,
                RenderingPlan.get(planIds[1]).planName, RenderingPlan.get(planIds[2]).planName);

        } else {
            plan = RenderingPlan.UNKNOWN.planName;
        }
        jobParams.add(ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_PLAN, plan));
        if (planIds.length > 1) {
            jobParams.add(ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_COUNT, count));
        }
        addJobParams(jobParams, dbName, fullPdfOnly);

        return plan;
    }

    public static void addJobParams(List<String> jobParams, String dbName, boolean fullPdfOnly) {

        jobParams.add(ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_DATABASE, dbName));
        if (fullPdfOnly) {
            jobParams.add(ProcessHelper.buildKeyValueParam(IRenderingProvider.PART_PARAM_FULL_PDF_ONLY, "yes"));
        }
    }

    public static int createRendering(int creatorId, List<String> jobParams, URI[] uris, boolean[] rawDataExists,
        String[] partParameters, int priority) throws Exception {

        URI callback = addJobParams(jobParams);
        return WebServiceUtils.getRenderingProvider().renderLater(creatorId, jobParams.toArray(
            new String[jobParams.size()]), uris, rawDataExists, partParameters, callback, priority);
    }

    public static int startRendering(int creatorId, List<String> jobParams, URI[] uris, boolean[] rawDataExists,
        String[] partParameters, int priority) throws Exception {

        URI callback = addJobParams(jobParams);
        return WebServiceUtils.getRenderingProvider().render(creatorId, jobParams.toArray(
            new String[jobParams.size()]), uris, rawDataExists, partParameters, callback, priority);
    }

    public static int startRendering(int creatorId, String plan, List<String> jobParams, URI[] uris,
                                     String[] partParameters) throws Exception {
        URI callback = addJobParams(jobParams);
        return WebServiceUtils.getRenderingProvider().renderPlan(creatorId, plan, jobParams.toArray(
            new String[jobParams.size()]), uris, partParameters, callback);
    }

    public static void clearRendering(int renderingJobId) throws Exception {
        WebServiceUtils.getRenderingProvider().clearRenderingResult(renderingJobId);
    }

    public static ResultParser parseCentralSource(String path, SAXParserFactory fact, IRepository rp) {

        ResultParser[] results = {new ResultParser(CENTRAL_TAGS[0])};
        InputStream is =  null;
        try {
            is = rp.getFile(path);
            SAXParser parser = fact.newSAXParser();
            parser.parse(is, new SourceHandler(null, CENTRAL_TAGS, results));

        } catch (FinishException fe) {

        } catch (Exception e) {
            LOG.error(e.getMessage());
            return null;

        } finally {
            IOUtils.closeQuietly(is);
        }

        return results[0];
    }

    public static URI createURI(int issue, String dbName, IRecord record, ContentLocation cl) {
        try {
            String path = cl != null
                    ? cl.getPathToMl3g(issue, dbName, record.getHistoryNumber(), record.getName(), false)
                    : record.getRecordPath();
            return FilePathCreator.getUri(path);

        } catch (Exception e) {
            handleRenderingPrepareError(record, "insert source URI error ", e);
        }
        return null;
    }

    public static int defineRenderingCompletedStatus(int processId, int dfId, String dfName, long countToRender,
                                                     long renderFailedCount, boolean hasRendering) {
        int completedState;
        if (renderFailedCount == 0) {
            completedState = IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS;
        } else if (renderFailedCount == countToRender) {
            LOG.info(String.format("failed %d, processId=%d, package %s[%d]", countToRender, processId, dfName, dfId));
            completedState = hasRendering ? IDeliveryFileStatus.STATUS_RND_FAILED
                    : IDeliveryFileStatus.STATUS_VALIDATION_FAILED;
        } else {
            LOG.info(String.format("failed %d from %d, processId=%d, package %s[%d]",
                    renderFailedCount, countToRender, processId, dfName, dfId));
            completedState = hasRendering ? IDeliveryFileStatus.STATUS_RND_SOME_FAILED
                    : IDeliveryFileStatus.STATUS_VALIDATION_SOME_FAILED;
        }
        return completedState;
    }

    public static ProcessVO createSelectiveRenderingProcess(String dbName, RenderingPlan plan, boolean withPrevious,
        Integer[] recordIds, String owner, ProcessVO first) throws Exception {
        RenderingRecordHandler rh = new RenderingRecordHandler(BaseManager.LABEL_RENDERING_RECORDS,
                dbName, plan.id(), withPrevious);
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_RENDER_SELECTIVE).get();

        return ProcessHelper.createIdPartsProcess(rh, pt, pt.getPriority(), owner,
                recordIds, first != null ? first.getId() : DbEntity.NOT_EXIST_ID, 0);
    }

    public static String buildLogMessage(RndParsingResult result, RenderingPlan plan) {
        return String.format("plan=%s, success=%b, completed=%b", plan, result.isSuccessful(), result.isCompleted());
    }

    public static void printLogMessage(int processId, RenderingPlan plan, String scopeName, String report) {
        LOG.debug(String.format("processId=%d, plan=%s, package: %s, %s", processId, plan, scopeName, report));
    }

    static void handleRenderingPrepareError(IRecord rec, String msg, Exception e) {
        LOG.error(msg, e);
        rec.setSuccessful(false);
        rec.addMessages(msg);
    }

    private static URI addJobParams(List<String> jobParams) throws Exception {
        jobParams.add(ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_PROFILE, "cochrane"));

        return new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
                        + "AcceptRenderingResults/AcceptRenderingProvider?wsdl");
    }
}
