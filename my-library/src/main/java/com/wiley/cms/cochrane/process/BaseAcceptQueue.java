package com.wiley.cms.cochrane.process;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import javax.ejb.EJB;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;

import org.apache.commons.io.IOUtils;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.df.IDfStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.parser.QaResultHandler;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.parser.RndResultHandler;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.process.handler.QaServiceHandler;
import com.wiley.cms.cochrane.process.handler.RenderFOPHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.IProcessStorage;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessPartQueue;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 03.10.13
 */
public abstract class BaseAcceptQueue extends ProcessPartQueue<ProcessHandler> implements IBeanProvider {
    protected static final Logger LOG = Logger.getLogger(BaseAcceptQueue.class);

    @EJB(beanName = "DfStorage")
    protected IDfStorage dfStorage;

    @EJB(beanName = "ResultsStorage")
    protected IResultsStorage rs;

    @EJB(beanName = "FlowLogger")
    protected IFlowLogger flowLogger;

    protected SAXParserFactory factory;
    protected IRepository rp;

    Res<Property> takeIntermediateResults = CmsResourceInitializer.getTakeIntermediateResults4FopPdf();
    ExternalProcess externalProcess;

    void initParserFactory() {

        factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);

        rp = RepositoryFactory.getRepository();
    }

    private void parseReport(String report, DefaultHandler handler) throws Exception {

        SAXParser parser = factory.newSAXParser();
        InputStream is = null;
        try {
            is = new ByteArrayInputStream(report.getBytes());
            parser.parse(is, handler);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    RndParsingResult parseRenderingResult(int jobId, String rndRep, String params, IProcessManager manager)
            throws Exception {
        return parseRenderingResult(jobId, rndRep, params, false, manager);
    }

    RndParsingResult parseRenderingResult(int jobId, String rndRep, String params, boolean toImport,
                                          IProcessManager manager) throws Exception {
        String report = manager.buildProcessReport(jobId, externalProcess.getState().isSuccessful(), rndRep, params);
        LOG.debug(String.format("report was created for process %s", externalProcess));

        initParserFactory();

        RndParsingResult result = new RndParsingResult();
        parseReport(report, new RndResultHandler(jobId, result, toImport));
        return result;
    }

    void loadRenderingContent(int processId, String dfName, RndParsingResult result, Collection<Record> records,
                              String rndRepository, String webUrl) throws Exception {

        RenderingPlan plan = result.getPlan();
        LOG.debug(String.format("loadContent started size=%d, plan=%s, processId=%d", records.size(), plan, processId));

        Integer issueId = result.getIssueId();
        boolean cdsrImport = CmsUtils.isImportIssue(issueId);
        boolean loadOnlyPdf = RenderingPlan.isPdfFOP(plan.id()) && !takeIntermediateResults.get().asBoolean();

        for (Record record : records) {
            Integer historyNumber = record.getHistoryNumber();
            ContentLocation cl = cdsrImport && historyNumber != null && historyNumber > RecordEntity.VERSION_LAST
                    ? ContentLocation.ISSUE_PREVIOUS : ContentLocation.ISSUE;
            try {
                loadRecord(result, record, plan, rndRepository, webUrl, loadOnlyPdf, cl);
            } catch (Exception e) {
                if (dfName != null) {
                    flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, dfName, record.getName(),
                        "Record issue storage error: " + e.getMessage(), true,
                            true, CmsUtils.isScheduledIssue(issueId));
                }
                throw e;
            }
        }
        LOG.debug(String.format("loadContent finished size=%d, plan=%s, processId=%d ",
                records.size(), plan, processId));
    }

    void buildMaps(RndParsingResult result, String dbName, Integer dfId, Map<Integer, Record> goodRecs,
                   Map<Integer, Record> badRecs) {
        Integer issueId = result.getIssueId();
        Map<String, Record> map = new HashMap<>();
        Collection<String> names = new HashSet<>();
        for (Record r : result.getRecords()) {
            map.put(r.getRecordSourceUri(), r);
            names.add(r.getName());
        }

        List<Record> records = rs.getRecordsByDfAndNames(dfId, names);
        for (Record record : records) {
            String cdNumber = record.getName();
            Integer historyNumber = record.getHistoryNumber();
            String uri = historyNumber == null
                    ? ContentLocation.ISSUE.getPathToMl3g(issueId, dbName, null, cdNumber, false)
                    : ContentLocation.ISSUE_PREVIOUS.getPathToMl3g(issueId, dbName, historyNumber, cdNumber, false);
            Record r = map.get(uri);
            if (r != null) {
                buildMaps(r, record, null, false, null, goodRecs, badRecs);
                r.setHistoryNumber(record.getHistoryNumber());
            }
        }
    }

    void buildMaps(Iterable<Record> results, Integer getId, boolean correctPaths,
        Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs, BiFunction<Integer, Collection<String>,
            List<Record>> getter) {
        Map<String, Record> map = new HashMap<>();
        results.forEach(r -> map.put(r.getName(), r));
        List<Record> records = getter.apply(getId, map.keySet());
        records.forEach(
                record -> buildMaps(map.get(record.getName()), record, null, correctPaths, null, goodRecs, badRecs));
    }

    void buildMaps(Iterable<Record> results, Integer dfId, String dfName, boolean correctPaths, Boolean logFlow,
                   Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs) {
        Map<String, Record> map = new HashMap<>();
        results.forEach(r -> map.put(r.getName(), r));
        List<Record> records = rs.getRecordsByDfAndNames(dfId, map.keySet());
        records.forEach(r -> buildMaps(map.get(r.getName()), r, dfName, correctPaths, logFlow, goodRecs, badRecs));
    }

    private void buildMaps(Record to, Record from, String dfName, boolean correctPaths, Boolean logFlow,
                           Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs) {
        to.setId(from.getId());
        to.setRawExist(from.isRawExist());
        to.setUnitStatusId(from.getUnitStatus());
        to.setSubTitle(from.getSubTitle());
        to.setDeliveryFileType(from.getDeliveryFileType());
        to.setDeliveryFileId(from.getDeliveryFileId());

        if (correctPaths) {
            try {
                correctPath(to, from.getRecordSourceUri());
            } catch (Exception e) {
                LOG.error(e.getMessage());
                to.setSuccessful(false, e.getMessage());
            }
        }

        if (to.isSuccessful()) {
            goodRecs.put(to.getId(), to);
            if (logFlow != null) {
                flowLogger.onProductRendered(to.getName(), logFlow);
            }
        } else {
            badRecs.put(to.getId(), to);
            to.setFilesList(null);
            if (logFlow != null) {
                flowLogger.onProductPackageError(ILogEvent.PRODUCT_RENDERED, dfName, to.getName(),
                        "Rendering error: " + to.getMessages(), true, logFlow, false);
            }
        }
    }

    private void loadRecord(RndParsingResult result, Record record, RenderingPlan plan, String rndRepository,
                            String webUrl, boolean onlyPdf, ContentLocation cl) throws Exception {

        String dbName = result.getDbName();
        Integer issueId = result.getIssueId();
        Integer historyNumber = record.getHistoryNumber();
        String cdNumber = record.getName();
        rp.deleteDir(plan.getDirPath(issueId, dbName, historyNumber, cdNumber, cl));

        List<String> list = record.getFilesList();
        List<String> newList = new ArrayList<>(list.size());
        for (String url : list) {
            if (!onlyPdf || url.endsWith(Extensions.PDF)) {
                String newFilePath = plan.getFilePath(issueId, dbName, historyNumber, cdNumber, url, cl);
                RepositoryUtils.copyFile(newFilePath, url, rndRepository, webUrl, rp);
                newList.add(newFilePath);
            }
        }
        record.setFilesList(newList);
    }

    private void correctPath(Record record, String rightPath) throws Exception {

        if (record.getRecordSourceUri().contains(FilePathCreator.ABSTRACTS_TMP_DIR)) {

            rp.deleteFile(record.getRecordSourceUri());
            String recordDir = record.getRecordSourceUri().replace(Extensions.XML, "");
            if (RepositoryUtils.getRealFile(recordDir).exists()) {
                rp.deleteDir(recordDir);
            }
            record.setRecordSourceUri(rightPath);
        }
    }

    void logAndNotifyRendering(int jobId, int fullIssueNumber, RndParsingResult result,
                               Collection<Record> failedRecords) {
        if (!failedRecords.isEmpty()) {
            StringBuilder report = new StringBuilder();
            build4Resupply(jobId, result.getDbName(), fullIssueNumber, failedRecords, report);
            String scope = ContentLocation.ISSUE.getShortString(fullIssueNumber, result.getDbName(), null);
            MessageSender.sendForDatabase(result.getDbName(), MessageSender.MSG_TITLE_RENDERING_FAILED, scope,
                    report.toString());
            RenderingHelper.printLogMessage(jobId, result.getPlan(), scope, report.toString());
        }
    }

    void logAndNotifyRendering(int jobId, RndParsingResult result, Collection<Record> badRecords,
                               DeliveryFileVO df, String logUser) {
        RenderingPlan plan = result.getPlan();
        String dfName = df.getName();
        String message = RenderingHelper.buildLogMessage(result, plan);

        getActivityLog().logDeliveryFile(ILogEvent.RND_COMPLETED, df.getId(), dfName, logUser,
                String.format("processId=%d, %s", jobId, message));

        if (!badRecords.isEmpty()) {
            notify4Resupply(jobId, result, dfName, df.getFullIssueNumber(), plan, badRecords);
        }
        if (!result.isSuccessful()) {
            MessageSender.sendRenderReport(jobId, dfName, message);
        }
    }

    private void notify4Resupply(int jobId, RndParsingResult result, String dfName, int issue, RenderingPlan plan,
        Iterable<Record> failedRecs) {

        StringBuilder resupply = new StringBuilder();
        resupply.append("Database: ").append(result.getDbName()).append(", plan: ").append(plan).append(
                ". The following records failed to render:\n");
        build4Resupply(jobId, result.getDbName(), issue, failedRecs, resupply);
        String report = resupply.toString();
        MessageSender.sendResupply(jobId, dfName, report);
        RenderingHelper.printLogMessage(jobId, plan, dfName, report);
    }

    void build4Resupply(int jobId, String dbName, int issue, Iterable<Record> failed, StringBuilder sb) {

        int baseType = BaseType.find(dbName).get().getDbId();

        for (Record rec : failed) {

            String msg = rec.getMessages();
            sb.append(rec.getName());
            if (msg != null) {
                sb.append(" (").append(msg).append(")");
            }
            sb.append("\n");
            getActivityLog().logRecordError(ILogEvent.RND_FAILED, jobId, rec.getName(), baseType, issue, msg);
        }
    }

    QaManager.QaResult parseQaResult(int jobId, String report, String dfName) throws Exception {

        LOG.debug(String.format("parse QA result started, processId=%d", jobId));

        QaManager.QaResult result = new QaManager.QaResult();
        parseReport(report, new QaResultHandler(jobId, result));

        getQaManager().parseRecords(result.getRecords(), result.getDbName(), result.getIssueId(),
                ArchiePackage.canAut(dfName));

        LOG.debug(String.format("parse QA result finished, processId=%d", jobId));
        return result;
    }

    public void processMessage(RenderFOPHandler handler, ProcessVO creator) {
    }

    public void processMessage(QaServiceHandler handler, ProcessVO creator) {
    }

    @Override
    public IProcessStorage getProcessStorage() {
        return storage;
    }

    @Override
    public IActivityLog getActivityLog() {
        return flowLogger.getActivityLog();
    }

    @Override
    public IFlowLogger getFlowLogger() {
        return flowLogger;
    }
}
