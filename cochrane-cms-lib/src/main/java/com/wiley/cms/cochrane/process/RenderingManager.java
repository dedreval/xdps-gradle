package com.wiley.cms.cochrane.process;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.df.IDfStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RenderingVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.IRenderingStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.render.IRenderManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterIssueDb;
import com.wiley.cms.cochrane.process.handler.PackageHandler;
import com.wiley.cms.cochrane.process.handler.RenderingPackageHandler;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.DbConstants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10.09.13
 */
@Stateless
@Local(IRenderingManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RenderingManager extends BaseRenderingManager implements IRenderingManager {

    @EJB(beanName = "RenderingStorage")
    private IRenderingStorage renderingStorage;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recordStorage;

    @EJB(beanName = "DbStorage")
    private IDbStorage dbStorage;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService dlvService;

    @EJB(beanName = "DfStorage")
    private IDfStorage ds;

    @EJB(beanName = "RenderManager")
    private IRenderManager renderManager;

    @Resource(mappedName = AcceptRenderQueue.QUEUE_DESTINATION)
    private Queue acceptQueue;

    private DeliveryFileVO df;
    private Map<Integer, Record> records;

    private RepeatableOperation createOp = new RepeatableOperation(2, null, null) {
        protected void perform() {
            renderingStorage.createRenderingRecords((String) params[0], (int[]) params[1]);
        }
    };

    private RepeatableOperation deleteOp = new RepeatableOperation() {
        protected void perform() {
            renderingStorage.deleteRecordsByIds(records.keySet());
        }
    };

    @Override
    public void convertToWml3gForFOPRendering(@NotNull String dbName, @NotNull DeliveryFileVO df,
            @NotNull Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs, boolean useExistingAssets) {
        if (goodRecs.isEmpty()) {
            return;
        }
        Wml3gConverterIssueDb conv = new Wml3gConverterIssueDb(df.getIssue(), df.getFullIssueNumber(),
                df.getDbId(), dbName);
        conv.setUseExistingAssets(useExistingAssets);
        conv.setRecords(goodRecs);
        Set<Integer> failedRecIds = conv.execute();

        for (Integer recId: failedRecIds) {
            Record rec = goodRecs.remove(recId);
            if (rec == null) {
                continue;
            }
            rec.setSuccessful(false);
            if (badRecs != null) {
                badRecs.put(recId, rec);
            }
        }
        if (!failedRecIds.isEmpty()) {
            recordStorage.setRenderResults(failedRecIds, false, true);
        }
    }

    public void resumeRendering(int dfId) throws ProcessException {

        DeliveryFileVO dfVO = rs.getDeliveryFileVO(dfId);
        List<Record> recs = recordStorage.getRecordsForRendering(dfId);
        LOG.info(String.format("%d records have found, df=%s[%d]", recs.size(), dfVO.getName(), dfId));
        if (recs.isEmpty()) {
            return;
        }

        List<ProcessVO> list = CochraneCMSBeans.getCMSProcessManager().findPackageProcesses(
                LABEL_RENDERING_PACKAGE, dfId);
        LOG.info(String.format("%d processes have found, df=%s[%d]", list.size(), dfVO.getName(), dfId));
        ProcessVO process = null;
        for (ProcessVO p : list) {
            if (!p.getState().isCompleted()) {
                process = p;
                break;
            }
        }

        if (process == null) {
            LOG.info(String.format("no any uncompleted processes have found, df=%s[%d]", dfVO.getName(), dfId));
        }

        boolean cdsr = CochraneCMSPropertyNames.getCDSRDbName().equals(dfVO.getDbName());
        process = null;    // never try to use an existing one
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_BEGIN, false);
        Map<Integer, Record> recordPart = new HashMap<>();
        for (Record rec : recs) {

            recordPart.put(rec.getId(), rec);
            if (recordPart.size() == DbConstants.DB_QUERY_VARIABLE_COUNT) {
                recordStorage.setRenderResults(recordPart.keySet(), false, false);
                recordPart.clear();
            }
            if (cdsr) {
                rs.setWhenReadyPublishState(RecordEntity.STATE_PROCESSING, rec.getId());
            }
        }
        if (!recordPart.isEmpty()) {
            recordStorage.setRenderResults(recordPart.keySet(), false, false);
            recordPart.clear();
        }

        int partSize = CochraneCMSPropertyNames.getQaPartSize();
        initRendering(dfVO, recordPart);

        for (Record rec : recs) {
            recordPart.put(rec.getId(), rec);
            if (recordPart.size() == partSize) {

                process = startRendering(process);
                recordPart.clear();
            }
        }

        if (!recordPart.isEmpty()) {
            startRendering(process);
        }
    }

    public void addRendering(ProcessVO process, DeliveryFileVO df, Map<Integer, Record> records) {
        initRendering(df, records);
        addRendering(process);
    }

    public ProcessVO startRendering(ExternalProcess previous, DeliveryFileVO df, Map<Integer, Record> records) {

        String dbName = initRendering(df, records);
        ProcessVO ret = null;
        String dfName = df.getName();

        boolean planHtml = database.canHtmlConvert();
        boolean pdf = !planHtml && database.canPdfFopConvert();
        if (pdf) {
            convertToWml3gForFOPRendering(dbName, df, records, null, false);
        }
        try {
            synchronized (previous) {
                if (!previous.hasNext()) {
                    // create new rendering process
                    int priority = getPackagePriority(database.isCDSR());
                    int[] planIds = RenderingPlanEntity.getPlanIds(false, planHtml, pdf, calcCountRnd(pdf, planHtml));
                    ret = startProcess(previous.getCreatorId(), new RenderingPackageHandler(LABEL_RENDERING_PACKAGE,
                            df.getId(), dfName, dbName, planIds), priority);
                    previous.setNextId(ret.getId());
                    if (previous.exists()) {
                        ps.setNextProcess(previous);
                    }
                    return ret;
                }
            }
            // add job to existing rendering process
            ret = findProcess(previous.getNextId());
            addRendering(ret);

        } catch (ProcessException e) {
            LOG.error(e.getMessage());
        }
        return ret;
    }

    @Override
    public void startNewRenderingStage(ProcessVO parent, DeliveryFileVO df, Map<Integer, Record> goodRecs,
                                       int[] planIds) {
        initRendering(df, goodRecs);
        ProcessHandler handler =
                new RenderingPackageHandler(Constants.NA, df.getId(), df.getName(), df.getDbName(), planIds);
        startProcess(handler, parent);
    }

    private String initRendering(DeliveryFileVO df, Map<Integer, Record> records) {
        this.df = df;
        this.records = records;
        String dbName = df.getDbName();
        database = BaseType.find(dbName).get();
        return dbName;
    }

    private void addRendering(ProcessVO process) {
        try {
            PackageHandler ph = ProcessHandler.castProcessHandler(process.getHandler(), PackageHandler.class);
            startProcess(ph, process);
        } catch (ProcessException e) {
            LOG.error(e.getMessage());
        }
    }

    private ProcessVO startRendering(ProcessVO process) {
        ProcessVO ret = process;
        if (ret == null) {
            ret = startRendering(new ExternalProcess(), df, records);
        } else {
            addRendering(process);
        }
        return ret;
    }

    @Override
    protected void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        LOG.debug(String.format("rendering started for process %s, records %d", pvo, records.size()));

        String dfName = df.getName();
        RenderingPackageHandler ph = ProcessHandler.castProcessHandler(handler, RenderingPackageHandler.class);
        int processId = pvo.getId();

        try {
            if (!RenderingPlan.isPdfFOP(ph.getPlanIds()[0])) {
                deleteOp.performOperation();
            }

            RenderingInput input = createParameters(pvo, ph.getPlanIds());

            List<String> jobParams = new ArrayList<>();

            String plan = RenderingHelper.addPlanParams(jobParams, ph.getPlanIds(), input.count, df.getDbName(),
                    database.isEditorial());

            startRenderingCommon(processId, jobParams, input.uris, input.rawDataExists, input.pdfFopParameters,
                                     pvo.getPriority());
            logRenderStarted(processId, plan, input.count);

        } catch (ProcessException pe) {
            logErrorProcessForDeliveryPackage(processId, ph.getPackageId(), dfName, ILogEvent.RND_NOT_STARTED,
                    pe.getMessage());
            throw pe;

        } catch (Exception e) {
            LOG.error(e);
            logErrorProcessForDeliveryPackage(processId, ph.getPackageId(), dfName, ILogEvent.RND_NOT_STARTED,
                    ErrorInfo.Type.SYSTEM.getMsg());
            throw new ProcessException(e.getMessage());
        }
    }

    public void acceptRenderingResults(int jobId, final String results) {
        ExternalProcess pvo = ps.getExternalProcess(jobId);
        if (pvo != null && !pvo.hasEmptyType()) {
            try {
                ProcessVO creator = findProcess(pvo.getCreatorId());
                acceptResults(creator, pvo, CochraneCMSBeans.getQueueProvider().getQueue(
                        creator.getType().getQueueName()));
            } catch (Exception e) {
                LOG.error(e);
            }
            return;
        }
        // to support an old approach
        acceptResults(jobId, pvo, acceptQueue, new JMSSender.MessageProcessCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                ObjectMessage msg = session.createObjectMessage();
                msg.setObject(new Object[]{getProcess(), results});
                return msg;
            }
        });
    }

    public int updateFaultRecords(int processId, RndParsingResult result) {
        List<Record> recs = result.getRecords();
        List<Integer> ids = new ArrayList<>();
        for (Record rec : recs) {
            ids.add(rec.getId());
        }
        return updateFaultRecords(processId, ids);
    }

    private int updateFaultRecords(int processId, List<Integer> ids) {
        LOG.debug(String.format("rendering update start: fault=%d, processId=%d", ids.size(), processId));
        RenderUpdater ru = new RenderUpdater();
        int completedCount = ru.updateRecords(ids, false);
        LOG.debug(String.format("rendering update finish: fault=%d, processId=%d", completedCount, processId));

        return completedCount;
    }

    public int updateRecords(int processId, RndParsingResult result, DeliveryFileVO df, Map<Integer, Record> goodRecs,
                             Map<Integer, Record> badRecs, int[] planIds, boolean update) {
        database = BaseType.find(df.getDbName()).get();
        RenderingPlan plan = result.getPlan();
        int planId = plan.id();
        int goodSize = goodRecs.size();
        int badSize = badRecs.size();
        LOG.debug(String.format("rendering update start: %d / %d, plan=%s, processId=%d",
                goodSize, badSize, plan, processId));

        renderManager.updateRendering(planId, goodRecs.keySet(), true);
        renderManager.updateRendering(planId, badRecs.keySet(), false);

        int completedCount = flushRenderResults(planIds, goodRecs, badRecs, update);

        LOG.debug(String.format("rendering update finish: %d / %d, completed=%d plan=%s df=%s[%d], processId=%d",
                goodSize, badSize, completedCount, plan, df.getName(), df.getId(), processId));

        return completedCount;
    }

    public void finalizeRendering(ProcessVO creator, int jobId, DeliveryFileVO df, int[] planIds, boolean completed) {
        int restCount = deleteProcess(creator, jobId, completed);

        if (restCount == 0) {
            ds.changeStatus(df.getId(), df.getStatus(), planIds);
            dlvService.finishUpload(BaseType.find(df.getDbName()).get(), df.getIssue(), df, jobId);
            if (creator.hasCreator()) {
                deleteProcess(creator.getCreatorId(), true);
            }
        }
    }

    public boolean checkRenderingState(ProcessVO creator, int processId, DeliveryFileVO df, int completedCount,
                                       int goodCount) {
        boolean completed;
        synchronized (creator) {
            completed = creator.getState().isCompleted();
            if (!creator.getState().isCompleted()) {
                completed = checkRenderingState(processId, df, completedCount, goodCount);
                if (completed) {
                    creator.setState(ProcessState.SUCCESSFUL);
                }
            } else {
                checkRenderingState(processId, df, completedCount, goodCount);
            }
        }
        return completed;
    }

    private boolean checkRenderingState(int processId, DeliveryFileVO dfVO, int curCompletedSize, int goodSize) {

        boolean completed = false;
        int dfId = dfVO.getId();
        // all rendering count
        long allCount = recordStorage.getCountRenderingByDfId(dfId);
        LOG.debug(String.format("allCount=%d, curCompletedSize=%d", allCount, curCompletedSize));
        int completedState = 0;

        if (curCompletedSize == allCount) {
            // all records in one part
            completed = true;
        }
        if (goodSize > 0) {
            // all successful rendering count
            long successfulCount = (goodSize == allCount) ? goodSize
                    : recordStorage.getCountRenderingSuccessfulByDfId(dfId);
            if (successfulCount == allCount) {
                completed = true;
                completedState = IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS;
            }
            dbStorage.updateRenderedRecordCount(dfVO.getDbId());
            LOG.debug(String.format("allCount=%d, successfulCount=%d", allCount, successfulCount));
        }
        if (!completed) {
            long allCompletedCount = recordStorage.getCountRenderingCompletedByDfID(dfId);
            completed = (allCompletedCount == allCount);
            LOG.info(String.format("rendering completed: %d from %d, processId=%d, package %s[%d]",
                    allCompletedCount, allCount, processId, dfVO.getName(), dfId));
        }

        if (!completed) {
            return false;
        }

        if (completedState == 0) {
            completedState = RenderingHelper.defineRenderingCompletedStatus(processId, dfId, dfVO.getName(), allCount,
                    recordStorage.getCountRenderingFailedByDfId(dfId), true);
        }
        dfVO.setStatus(completedState);
        return completed;
    }

    private int flushRenderResults(int[] planIds, Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs,
                                   boolean update) {
        int goodSize = goodRecs.size();
        int badSize = badRecs.size();
        Collection<Integer> ids;
        if (goodSize == 0) {
            ids = badRecs.keySet();
        } else if (badSize == 0) {
            ids = goodRecs.keySet();
        } else {
            ids = new ArrayList<>(goodSize + badSize);
            ids.addAll(goodRecs.keySet());
            ids.addAll(badRecs.keySet());
        }

        RenderUpdater ru = null;
        if (planIds.length > 1) {
            List<RenderingVO> completedList = renderingStorage.findRenderingsByRecords(ids, false);

            if (!completedList.isEmpty()) {
                ru = new RenderUpdater(goodRecs, badRecs);
                ru.analyseManyPlans(completedList);
            }
        } else {
            List<RenderingVO> completedList = renderingStorage.findRenderingsByRecords(ids, true);
            if (!completedList.isEmpty()) {
                ru = new RenderUpdater(goodRecs, badRecs);
                ru.analyseOnePlan(completedList);
            }
        }
        int ret = 0;
        if (update && ru != null) {
            ret += ru.updateRecords(ru.goodIds, true);
            ret += ru.updateRecords(ru.badIds, false);
        }
        return ret;
    }

    private void logRenderStarted(int prId, String plan, int count) {
        int dfId = df.getId();
        if (!df.isRenderingStarted()) {
            rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_RENDERING_STARTED, true);
        }
        logProcessForDeliveryPackage(prId, dfId, df.getName(), ILogEvent.RND_STARTED,
                "plan: " + plan + " count: " + count);
        MessageSender.sendStartedRender(prId, plan, "Delivery file name: " + df.getName());
    }

    private RenderingInput createParameters(ProcessVO pvo, int[] planIds) throws Exception {

        RenderingInput ret = new RenderingInput();
        if (database.isCDSR()) {
            createUrisForCDSR(planIds, ret);
        } else {
            ret.uris = createUris(planIds);
        }
        if (ret.uris == null || ret.uris.length == 0) {
            throw new ProcessException("Not formed list of uri for rendering", pvo.getId());
        }
        if (ret.count == 0) {
            ret.count = ret.uris.length;
        }
        return ret;
    }

    private void startRenderingCommon(int creatorId, List<String> jobParams, URI[] uris, boolean[] rawDataExist,
        Map<String, String> fopPdfParams, int priority) throws Exception {

        String[] partParameters = null;
        if (fopPdfParams != null) {
            partParameters = new String[(fopPdfParams.size())];
            int i = 0;
            for (String uri : fopPdfParams.keySet()) {
                String param = fopPdfParams.get(uri);
                partParameters[i++] = ProcessHelper.buildUriParam(uri, param);
            }
        }

        RenderingHelper.startRendering(creatorId, jobParams, uris, rawDataExist, partParameters, priority);
    }

    private URI createURI(Record record, List<URI> uris, boolean needWml3gXml) {
        URI uri = RenderingHelper.createURI(df.getIssue(), df.getDbName(), record,
                needWml3gXml ? ContentLocation.ISSUE : null);
        if (uri != null) {
            uris.add(uri);
            return uri;
        }
        handleRenderingPrepareError(record);
        return null;
    }

    private URI createHtmlURI(Record record, List<URI> uris) {
        try {
            String path = database.hasTranslationHtml()
                ? taInserter.getSourceForRecordWithInsertedAbstracts(record, record.getRecordSourceUri(), df.getIssue(),
                    df.getId(), database.getTranslationModeHtml()) : record.getRecordSourceUri();
            URI ret = FilePathCreator.getUri(path);
            uris.add(ret);
            return ret;

        } catch (Exception e) {
            RenderingHelper.handleRenderingPrepareError(record, "insert translations error", e);
            handleRenderingPrepareError(record);
        }
        return null;
    }

    private void handleRenderingPrepareError(Record rec) {
        List<Record> list = new ArrayList<>();
        list.add(rec);
        recordStorage.flushQAResults(list, df.getDbId(), database.isCDSR(),
                DeliveryPackage.isTranslatedAbstract(df.getType()), false, false);
    }

    private URI[] createURIArray(List<URI> uris) {
        return uris.toArray(new URI[uris.size()]);
    }

    private boolean needWml3gXml(int[] planIds) {
        return planIds.length == 1 && RenderingPlan.isPdfFOP(planIds[0]);
    }


    private StringBuilder createUris4CDSR(List<URI> uris, List<Boolean> rawData, boolean html, StringBuilder recIds,
                                          RenderingInput input, boolean buildIds, boolean ml3gURI) {
        int count = 0;
        StringBuilder ret = null;

        if (ml3gURI) {
            input.pdfFopParameters = new HashMap<>();
        }

        for (Record rec : records.values()) {

            if (!rec.isSuccessful()) {
                continue;
            }

            URI uri = html ? createHtmlURI(rec, uris) : createURI(rec, uris, ml3gURI);
            if (uri == null) {
                ret = onRenderingPrepareError(rec, uris, rawData, input, count, !buildIds);
                continue;
            }

            count++;

            if (!buildIds) {
                continue;
            }
            if (recIds.length() > 0) {
                recIds.append(",");
            }
            recIds.append(rec.getId());

            rawData.add(rec.isRawExist());

            if (input.pdfFopParameters != null) {
                addFopParameters4CDSR(rec, uri, input);
            }
        }

        input.count = count;
        return ret == null ? recIds : ret;
    }

    private void addFopParameters4CDSR(Record rec, URI uri, RenderingInput input) {
        StringBuilder sb = null;
        if (!rec.isStageR() || rec.isWithdrawn()) {
            sb = new StringBuilder();
            ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_FULL_PDF_ONLY, Boolean.TRUE.toString(), sb);
        }
        String ta4FopParam = TranslatedAbstractVO.getLanguages4FopAsStr(rec.getLanguages());
        if (ta4FopParam != null) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_LANGUAGES, ta4FopParam, sb);
        }
        if (sb != null) {
            input.pdfFopParameters.put(uri.toString(), sb.toString());
        }
    }

    private StringBuilder onRenderingPrepareError(Record rec, List<URI> uris, List<Boolean> rawData, RenderingInput ret,
                                                  int index, boolean rebuildIds) {

        if (ret.count == 0) {
            // there has not previous plans yet
            return rebuildIds ? rebuildIds() : null;
        }

        if (rawData != null) {
            if (rawData.size() > index) {
                rawData.remove(index);
            }
        }

        // remove from previous
        int ind = index;
        int prevPlansCount = uris.size() / ret.count;
        for (int i = 0; i < prevPlansCount; i++) {

            if (ind - i >= uris.size()) {
                LOG.error(String.format("index %d of wrong record %s, but uris size=%d, ind=%d", index, rec.getName(),
                        uris.size(), ind));
                break;
            }

            uris.remove(ind - i);
            ind += ret.count;
        }

        ret.count--;
        return rebuildIds ? rebuildIds() : null;
    }

    private URI[] createUris(int[] planIds) {
        List<URI> uris = new ArrayList<>();
        StringBuilder recIds = new StringBuilder();
        boolean needWml3gXml = needWml3gXml(planIds);
        for (Record record : records.values()) {

            if (createURI(record, uris, needWml3gXml) == null) {
                continue;
            }
            if (recIds.length() > 0) {
                recIds.append(",");
            }
            recIds.append(record.getId());
        }
        if (recIds.length() > 0) {
            createRecords(recIds.toString(), planIds);
        }
        return createURIArray(uris);
    }

    private void createUrisForCDSR(int[] planIds, RenderingInput ret) {

        List<URI> uris = new ArrayList<>();
        List<Boolean> rawData = new ArrayList<>();

        StringBuilder recIds = null;

        if (planIds.length == 1) {

            boolean html = RenderingPlan.isHtml(planIds[0]);
            boolean needWml3gXml = needWml3gXml(planIds);
            recIds = createUris4CDSR(uris, rawData, html, new StringBuilder(), ret, true, needWml3gXml);

        } else {

            for (int planId : planIds) {
                boolean html = RenderingPlan.isHtml(planId);
                if (recIds == null) {
                    recIds = new StringBuilder();
                    createUris4CDSR(uris, rawData, html, recIds, ret, false, false);

                } else {
                    recIds = createUris4CDSR(uris, rawData, html, recIds, ret, true, false);
                }
            }
        }

        if (recIds == null) {
            LOG.warn("count of plans is " + planIds.length);
            return;
        }

        if (recIds.length() > 0) {
            createRecords(recIds.toString(), planIds);
        }

        ret.uris = createURIArray(uris);

        ret.rawDataExists = new boolean[rawData.size()];
        for (int i = 0; i < rawData.size(); i++) {
            ret.rawDataExists[i] = rawData.get(i);
        }
    }

    private StringBuilder rebuildIds() {

        StringBuilder recIds = new StringBuilder();
        for (Record rec : records.values()) {

            if (!rec.isSuccessful()) {
                continue;
            }

            if (recIds.length() > 0) {
                recIds.append(",");
            }
            recIds.append(rec.getId());
        }
        return recIds;
    }

    private void createRecords(final String recIds, final int[] planIds) {
        createOp.params[0] = recIds;
        createOp.params[1] = planIds;
        createOp.performOperation();
    }

    private int calcCountRnd(boolean planPdf, boolean planHtml) {
        return planPdf && planHtml ? 2 : (planPdf || planHtml ? 1 : 0);
    }

    private static class RenderingInput {
        URI[] uris;
        boolean[] rawDataExists;
        Map<String, String> pdfFopParameters;
        int count = 0;
    }

    private class RenderUpdater {

        private Map<Integer, Record> goodRecs;
        private Map<Integer, Record> badRecs;
        private List<Integer> goodIds = new ArrayList<>();
        private List<Integer> badIds = new ArrayList<>();

        RenderUpdater() {
        }

        RenderUpdater(Map<Integer, Record> goodRecs, Map<Integer, Record> badRecs) {
            this.goodRecs = goodRecs;
            this.badRecs = badRecs;
        }

        void analyseManyPlans(List<RenderingVO> list) {

            Iterator<RenderingVO> it = list.iterator();
            int prevId = list.get(0).getRecordId();
            boolean completed = true;
            boolean successful = true;

            while (it.hasNext()) {

                RenderingVO re = it.next();
                int id = re.getRecordId();
                if (prevId != id) {
                    setRecordState(prevId, completed, successful);
                    prevId = id;
                    completed = true;
                    successful = true;
                }
                if (completed) {
                    completed = re.isCompleted();
                }
                if (successful) {
                    successful = re.isSuccessful();
                }
            }
            setRecordState(prevId, completed, successful);
        }

        void analyseOnePlan(List<RenderingVO> completedList) {
            for (RenderingVO re : completedList) {
                setRecordState(re.getRecordId(), true, re.isSuccessful());
            }
        }

        int updateRecords(final List<Integer> ids, final boolean state) {

            if (ids != null && !ids.isEmpty()) {
                final int size = ids.size();

                RepeatableOperation ro = new RepeatableOperation() {
                    public void perform() {
                        int count = recordStorage.setRenderResults(ids, state, true);
                        if (count != size) {
                            LOG.warn(String.format("expected size=%d, but really updated=%d", size, count));
                        }
                    }
                };

                if (!ro.performOperation()) {
                    LOG.error("updateRecords couldn't be performed");
                }
                return size;
            }
            return 0;
        }

        private void setRecordState(int id, boolean completed, boolean successful) {
            if (completed) {
                if (successful) {
                    goodIds.add(id);
                    setRecordState(id, goodRecs, badRecs);
                } else {
                    badIds.add(id);
                    setRecordState(id, badRecs, goodRecs);
                    goodRecs.remove(id);
                }
            }
        }

        private void setRecordState(int id, Map<Integer, Record> firstRecs, Map<Integer, Record> secondRecs) {
            Record rec = firstRecs.get(id);
            if (rec == null) {
                rec = secondRecs.get(id);
            }
            if (rec == null) {
                LOG.warn(String.format("record [%d] hasn't found in the initial set", id));
                return;
            }
            rec.setCompleted(true);
        }
    }

    /**
     * Just factory
     */
    public static class Factory extends AbstractBeanFactory<IRenderingManager> {
        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super(CochraneCMSPropertyNames.buildLookupName("RenderingManager", IRenderingManager.class));
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}
