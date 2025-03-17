package com.wiley.cms.cochrane.process;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.process.handler.RenderingPackageHandler;
import com.wiley.cms.cochrane.process.handler.RenderingRecordHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/accept_render"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "" + IRenderingProvider.MAX_SESSION)
        }, name = "AcceptRenderQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptRenderQueue extends BaseAcceptQueue implements MessageListener {
    static final String QUEUE_DESTINATION = "java:jboss/exported/jms/queue/accept_render";

    private static final Logger LOG = Logger.getLogger(AcceptRenderQueue.class);

    @EJB(beanName = "RenderingManager")
    private IRenderingManager manager;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService dlvService;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recStorage;

    private String logUser;

    private String params;
    private IRepository rp;

    @Override
    protected Logger log() {
        return LOG;
    }

    public void processMessage(RenderingRecordHandler handler, ProcessVO creator) {
        String dbName = handler.getDbName();
        int jobId = externalProcess.getId();
        try {
            BaseType database = BaseType.find(dbName).get();
            String rndRep = CochraneCMSPropertyNames.getRenderingRepository();
            String webUrl = CochraneCMSPropertyNames.getWebPrefix();

            RndParsingResult result = parseRenderingResult(jobId, rndRep, params, manager);

            List<Record> goodRecs = new ArrayList<>();
            List<Record> badRecs = new ArrayList<>();
            buildMaps(result, goodRecs, badRecs);

            loadEntireContent(jobId, result, goodRecs, rndRep, webUrl, creator, database.isCDSR());

            if (!badRecs.isEmpty()) {

                StringBuilder sb = new StringBuilder();
                build4Resupply(jobId, dbName, DbEntity.NOT_EXIST_ID, badRecs, sb);
                MessageSender.sendForDatabase(dbName, MessageSender.MSG_TITLE_RENDERING_FAILED,
                        ContentLocation.ENTIRE.getShortString(0, dbName, null), sb.toString());
                LOG.warn("rendering of entire records has failed: " + sb);
            }

            manager.finalizeRendering(creator, jobId);

        } catch (ProcessException pe) {
            getActivityLog().logDeliveryFileError(ILogEvent.RND_FAILED, 0, dbName, logUser, pe.getMessage());

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    public void processMessage(RenderingPackageHandler handler, ProcessVO creator) {

        int jobId = externalProcess.getId();
        int dfId = handler.getPackageId();
        String dfName = handler.getPackageName();
        String dbName = handler.getDbName();
        BaseType baseType = BaseType.find(dbName).get();
        try {
            String rndRep = CochraneCMSPropertyNames.getRenderingRepository();
            String webUrl = CochraneCMSPropertyNames.getWebPrefix();
            RndParsingResult result = parseRenderingResult(jobId, rndRep, params, manager);

            Map<Integer, Record> goodRecs = new HashMap<>();
            Map<Integer, Record> badRecs = new HashMap<>();
            boolean cdsr = CochraneCMSPropertyNames.getCDSRDbName().equals(dbName);
            buildMaps(result.getRecords(), dfId, dfName, cdsr, null, goodRecs, badRecs);
            loadRenderingContent(jobId, dfName, result, goodRecs.values(), rndRep, webUrl);

            DeliveryFileVO df = dfStorage.getDfVO(dfId);
            boolean lastStage = !externalProcess.hasNext();

            int completedSize = manager.updateRecords(jobId, result, df, goodRecs, badRecs, handler.getPlanIds(),
                    lastStage);
            logAndNotifyRendering(jobId, result, badRecs.values(), df, logUser);

            boolean pdfFopResults = isPdfFopResults(result);
            boolean startPdfFop = false;
            if (lastStage && baseType.canMl3gConvert() && !pdfFopResults) {
                manager.convertToWml3gForFOPRendering(dbName, df, goodRecs, badRecs, false);
                startPdfFop = !goodRecs.isEmpty() && baseType.canPdfFopConvert();
            }
            if (pdfFopResults) {
                addPdfLinks(dbName, result.getIssueId(), goodRecs);
                manager.convertToWml3gForFOPRendering(dbName, df, goodRecs, badRecs, true);
            }

            boolean completed = false;
            if (startPdfFop) {
                int[] planIds = RenderingPlanEntity.getPlanIds(false, false, true, 1);
                manager.startNewRenderingStage(creator, df, goodRecs, planIds);
            } else if (lastStage) {
                completed = checkCompleted(jobId, cdsr, goodRecs.values(), df, creator, completedSize);

            } else if (!manager.startRendering(externalProcess.getNextId())) {

                LOG.error(String.format("can't start sub-process [%d] for process %s",
                        externalProcess.getNextId(), creator));
                manager.updateFaultRecords(jobId, result);
                completed = checkCompleted(jobId, cdsr, goodRecs.values(), df, creator, completedSize);
            }

            manager.finalizeRendering(creator, jobId, df, handler.getPlanIds(), completed);

        } catch (ProcessException pe) {
            getActivityLog().logDeliveryFileError(ILogEvent.RND_FAILED, handler.getPackageId(),
                    handler.getPackageName(), logUser, pe.getMessage());
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        logUser = CochraneCMSPropertyNames.getActivityLogSystemName();
        rp = RepositoryFactory.getRepository();
        try {
            Object[] objs = JMSSender.getArrayObjectParam(message);
            externalProcess = JMSSender.getObjectParam(objs[0], ExternalProcess.class);
            params = JMSSender.getObjectParam(objs[1], String.class);

            LOG.debug(String.format("accepting rendering result started for process %s", externalProcess));
            ProcessVO creator = manager.findProcess(externalProcess.getCreatorId());
            creator.getHandler().onExternalMessage(creator, this);

        } catch (ProcessException pe) {
            LOG.warn(pe.getMessage());

        } catch (Throwable th) {
            LOG.error(th.getMessage(), th);
        }
    }

    private boolean checkCompleted(int jobId, boolean cdsr, Collection<Record> goodRecords,
        DeliveryFileVO df, ProcessVO creator, int completedSize) {

        dlvService.loadContent(df, goodRecords, cdsr, false, jobId);
        return manager.checkRenderingState(creator, jobId, df, completedSize, goodRecords.size());
    }

    private void buildMaps(RndParsingResult result, List<Record> goodRecs, List<Record> badRecs) {
        for (Record r : result.getRecords()) {
            if (r.isSuccessful()) {
                goodRecs.add(r);
            } else {
                badRecs.add(r);
                r.setFilesList(null);
            }
        }
    }

    private String getEntireDirPath(String dbName, Record record, RenderingPlan plan) {
        String uri = record.getRecordSourceUri();
        return uri.contains(FilePathCreator.PREVIOUS_DIR)
                ? FilePathCreator.getRenderedDirPathPrevious(dbName, uri, null, plan)
                : FilePathCreator.getRenderedDirPathEntire(dbName, null, plan);
    }

    private void loadEntireRecord(Record record, String dbName, RenderingPlan plan, String webUrl, String rndRepository,
        String otherDir, boolean onlyPdf) throws IOException {

        String baseRecordPath = getEntireDirPath(dbName, record, plan);
        String recordName = record.getName();
        boolean hasOtherDir = otherDir.length() > 0;

        baseRecordPath = hasOtherDir ? otherDir + baseRecordPath : baseRecordPath;
        FileUtils.deleteDirs(new File(rp.getRealFilePath(FilePathCreator.mergeRecordNameToPath(
                baseRecordPath, recordName, dbName))), false);

        for (String url : record.getFilesList()) {
            if (!onlyPdf || url.endsWith(Extensions.PDF)) {
                String newFilePath = FilePathCreator.getRenderedFilePathEntireByPlan(baseRecordPath, dbName,
                        recordName, url);
                RepositoryUtils.copyFile(newFilePath, url, rndRepository, webUrl, rp);
            }
        }
    }

    private boolean isPdfFopResults(RndParsingResult result) {
        return result.getPlan() == RenderingPlan.PDF_FOP;
    }

    private void addPdfLinks(String dbName, Integer issueId, Map<Integer, Record> records) {
        if (!records.isEmpty()) {
            List<Object[]> pathToIdMaps = recStorage.getRecordPathMappedToId(records.keySet());
            for (Object[] pathToIdMap : pathToIdMaps) {
                records.get(pathToIdMap[0]).setRecordSourceUri((String) pathToIdMap[1]);
            }
            records.forEach((k, v) -> addPdfLinks(dbName, issueId, v.getName()));
        }
    }

    private void addPdfLinks(String dbName, Integer issueId, String cdNumber) {
        List<String> assetsUris = Ml3gAssetsManager.getAssetsUris(dbName, issueId, cdNumber,
                RecordEntity.VERSION_SHADOW, ContentLocation.ISSUE, new StringBuilder());
        if (assetsUris == null) {
            return;
        }
        try {
            assetsUris.removeIf(uri -> uri.endsWith(Extensions.PDF));
            ContentLocation.ISSUE.addPdfs(issueId, dbName, RecordEntity.VERSION_SHADOW, cdNumber, assetsUris);

            String path = FilePathBuilder.ML3G.getPathToMl3gRecordAssets(issueId, dbName, cdNumber);

            rp.putFile(path, new ByteArrayInputStream(Ml3gAssetsManager.getAssetsFileContent(assetsUris).getBytes(
                    StandardCharsets.UTF_8)));

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    private void loadEntireContent(int processId, RndParsingResult result, Collection<Record> recs,
        String rndRepository, String webUrl, ProcessVO creator, boolean checkCache) throws IOException {

        RenderingPlan plan = result.getPlan();
        LOG.debug(String.format("loadContent entire started size=%d, plan=%s, processId=%d", recs.size(), plan,
                processId));

        if (recs.isEmpty()) {
            return;
        }

        String dbName = result.getDbName();
        String repository = RepositoryUtils.getFileSystemRoot();

        String entireBasePath = repository + FilePathCreator.SEPARATOR
                        + FilePathCreator.getRenderedDirPathEntire(dbName, "", plan);
        String otherDir = CochraneCMSPropertyNames.getRenderEntireDir();
        if (otherDir.length() > 0) {
            otherDir += FilePathCreator.SEPARATOR;
        }
        boolean check = checkCache && otherDir.length() == 0;
        boolean loadOnlyPdf = RenderingPlan.isPdfFOP(plan.id()) && !takeIntermediateResults.get().asBoolean();

        for (Record record : recs) {

            String recName = record.getName();
            if (check && manager.checkRecordOnProcessed(recName, entireBasePath, creator.getStartDate().getTime())) {
                LOG.info(String.format("%s of %s has been processed with another process", recName, creator));
                continue;
            }

            loadEntireRecord(record, dbName, plan, webUrl, rndRepository, otherDir, loadOnlyPdf);
        }
        LOG.debug(String.format("loadContent entire finished size=%d, plan=%s, processId=%d", recs.size(),
                plan, processId));
    }
}
