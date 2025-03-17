package com.wiley.cms.cochrane.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.jms.Message;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.render.IRenderManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterIssueDb;
import com.wiley.cms.cochrane.process.handler.QaServiceHandler;
import com.wiley.cms.cochrane.process.handler.RenderFOPHandler;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.render.services.IRenderingProvider;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/23/2018
 */
public abstract class BaseCMSAcceptProcessPartQueue extends BaseAcceptQueue {

    @EJB(beanName = "CMSProcessManager")
    private ICMSProcessManager manager;

    @EJB(beanName = "RenderManager")
    private IRenderManager renderManager;

    @EJB(beanName = "QaManager")
    private IQaManager qaManager;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService ds;

    @EJB(beanName = "DbStorage")
    private IDbStorage dbStorage;

    @Override
    public IQaManager getQaManager() {
        return qaManager;
    }

    @Override
    public IRenderManager getRenderManager() {
        return renderManager;
    }

    public void onMessage(Message message) {
        onMessage(message, manager, ProcessHandler.class);
    }

    @Override
    protected void acceptExternalParts(ProcessVO pvo, ProcessHandler handler, ExternalProcess extPvo,
                                       List<ProcessPartVO> parts) throws Exception {
        externalProcess = extPvo;
        handler.onExternalMessage(pvo, this);
    }

    @Override
    public void processMessage(QaServiceHandler handler, ProcessVO creator) {
        int extPrId = externalProcess.getId();
        String owner = creator.getOwner();
        int dfId = handler.getContentHandler().getDfId();
        String dfName = handler.getContentHandler().getDfName();
        LOG.debug(String.format("accepting QA result started for process %s", externalProcess));
        String report = manager.buildProcessReport(extPrId, null, externalProcess.getState().isSuccessful());
        LOG.debug(String.format("QA report was created for process %s", externalProcess));

        initParserFactory();
        DeliveryFileVO df = dfStorage.getDfVO(dfId);
        QaManager.QaResult result;
        try {
            result = parseQaResult(extPrId, report, dfName);
            if (!result.isCompleted()) {
                LOG.warn(String.format("QA was uncompleted for process %s", externalProcess));
            }

            BaseType bt = BaseType.find(result.getDbName()).get();
            boolean isCentral = bt.isCentral();
            if (isCentral) {
                result.checkDeleted();
            }
            String errorReport = result.buildErrorReport();
            LOG.debug(errorReport);

            OpStats stats;
            synchronized (creator) {
                stats = qaManager.updateRecords(extPrId, result, df, false, false, true, true);
            }
            List<Record> records = result.getRecords();
            Map<Integer, Record> passed = new HashMap<>();
            int reportBadCount = 0;
            for (Record record: records) {
                if (!record.isDeleted()) {
                    if (record.isSuccessful()) {
                        passed.put(record.getId(), record);
                    } else {
                        reportBadCount++;
                    }
                }
            }
            getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_RESULTS_ACCEPTED, extPrId, dfId, dfName, owner);

            LOG.debug(String.format("QA passed: successfully %d from %d + to withdraw %d", passed.size(),
                    records.size(), result.getWithdrawnCount()));
            if (isCentral && !passed.isEmpty()) {
                stats = convertToWml3g(extPrId, result, df, passed, stats);
            }
            ds.loadContent(df, passed.values(), false, bt.hasSFLogging(), extPrId);

            if (stats.isCompleted()) {
                LOG.debug(String.format("QA completed: failed %d from %d records", result.getBadCount(),
                        result.getBadCount() + result.getGoodCount()));

                stats.setCurrent(result.isSuccessful() && passed.isEmpty() ? 0 : result.getGoodCount());
                qaManager.finishQa(creator.getId(), result.getIssueId(), result.getDbName(), df, stats);
                if (isCentral) {
                    int deleted = stats.getDeleted();
                    int totalExisting = stats.getTotalCompleted() - deleted;
                    int totalSuccessful = stats.getTotalSuccessful();

                    LOG.debug(String.format("QA CENTRAL completed %d: withdrawn %d, successful %d / %d",
                            totalExisting + deleted, deleted, totalSuccessful, totalExisting));

                    addCentralLogging(ILogEvent.PRODUCT_VALIDATED, bt, df.getName(), df.getId(),
                        "total errors happened on a validation step", totalExisting - totalSuccessful, totalExisting);
                    addCentralLogging(ILogEvent.PRODUCT_VALIDATED, bt, df.getName(), df.getId(),
                        null, totalSuccessful, totalExisting);
                    addCentralLogging(ILogEvent.PRODUCT_DELETED, bt, df.getName(), df.getId(), null, deleted, deleted);
                    addCentralLogging(ILogEvent.PRODUCT_CONVERTED, bt, df.getName(), df.getId(),
                        null, totalSuccessful, totalSuccessful);
                    addCentralLogging(ILogEvent.PRODUCT_SAVED, bt, df.getName(), df.getId(),
                        null, totalSuccessful, totalSuccessful);
                }
            }

            if (!passed.isEmpty()) {
                // at least some records passed successfully
                getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_PASSED, extPrId, dfId, dfName, owner);
            }

            if (reportBadCount > 0) {
                // at least some records failed
                MessageSender.sendResupply(extPrId, dfName, errorReport);
                getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_FAILED, extPrId, dfId, dfName, owner);
            }

        } catch (Throwable th) {
            LOG.error(th.getMessage(), th);
        }
    }

    private void addCentralLogging(int eventId, BaseType bt, String fileName, Integer dfId, String errMsg,
                                   Integer completed, Integer total) {
        if (completed > 0) {
            if (errMsg != null) {
                flowLogger.onPackageFlowEventError(eventId, bt, fileName, dfId,
                        PackageChecker.METAXIS, null, errMsg, completed, total);
            } else {
                flowLogger.onPackageFlowEvent(eventId, bt, fileName, dfId, PackageChecker.METAXIS, null,
                        completed, total);
            }
        }
    }

    private OpStats convertToWml3g(int extPrId, QaManager.QaResult result, DeliveryFileVO df,
                                   Map<Integer, Record> passed, OpStats stats) {
        OpStats ret = stats;
        Wml3gConverterIssueDb conv = new Wml3gConverterIssueDb(df.getIssue(), df.getFullIssueNumber(),
                df.getDbId(), df.getDbName());
        conv.setRecords(passed);
        Set<Integer> failedIds = conv.execute();
        for (Integer recordId: failedIds) {
            Record record = passed.remove(recordId);
            if (record != null) {
                record.setSuccessful(false);
            }
        }
        if (!failedIds.isEmpty()) {
            LOG.warn(String.format("conversion to Wiley ML3G failed %d", failedIds.size()));
            ret = qaManager.updateRecords(extPrId, result, df, false, false, true, true);
        }
        if (!passed.isEmpty()) {
            dbStorage.updateRenderedRecordCount(df.getDbId());
        }
        return ret;
    }

    @Override
    public void processMessage(RenderFOPHandler handler, ProcessVO creator) {

        int extPrId = externalProcess.getId();

        DeliveryFileVO df = handler.hasDeliveryFile() ? dfStorage.getDfVO(handler.getContentHandler().getDfId()) : null;
        String dbName = handler.getContentHandler().getDbName();
        BaseType bt = BaseType.find(dbName).get();
        String owner = creator.getOwner();
        boolean cdsr = CochraneCMSPropertyNames.getCDSRDbName().equals(dbName);
        boolean cdsrImport = cdsr && CmsUtils.isImportIssue(handler.getContentHandler().getIssueId());
        boolean useDashboard = df != null && !cdsrImport && bt.hasSFLogging()
                && !DeliveryPackage.isPropertyUpdate(df.getName());
        String dfDashboardName = useDashboard ? df.getName() : null;

        String rndRep = CochraneCMSPropertyNames.getRenderingRepository();
        String webUrl = CochraneCMSPropertyNames.getWebPrefix();
        try {
            RndParsingResult result = parseRenderingResult(extPrId, rndRep, ProcessHelper.buildKeyValueParam(
                    IRenderingProvider.JOB_PARAM_PLAN, RenderingPlan.PDF_FOP.planName, true), cdsrImport, manager);
            Map<Integer, Record> passed = new HashMap<>();
            Map<Integer, Record> failed = new HashMap<>();
            if (df == null) {
                buildMaps(result.getRecords(), handler.getContentHandler().getDbId(), cdsr, passed, failed,
                        this::getRecords);
            } else if (!cdsrImport) {
                boolean correctPaths = cdsr && DeliveryFileEntity.isRevman(df.getType());
                buildMaps(result.getRecords(), df.getId(), df.getName(), correctPaths, useDashboard, passed, failed);
            } else {
                buildMaps(result, dbName, df.getId(), passed, failed);
            }
            log().info(String.format("accepting %s, %s for records: %d/%d",
                    externalProcess, creator, passed.size(), failed.size()));

            loadRenderingContent(extPrId, dfDashboardName, result, passed.values(), rndRep, webUrl);

            renderManager.updateRendering(RenderingPlan.PDF_FOP.id(), passed.keySet(), failed.keySet(), true);

            if (!cdsrImport && df != null) {
                logAndNotifyRendering(extPrId, result, failed.values(), df, owner);
                ds.loadContent(df, passed.values(), cdsr, useDashboard, extPrId);
            } else {
                logAndNotifyRendering(extPrId, handler.getContentHandler().getIssue(), result, failed.values());
            }

        } catch (ProcessException pe) {
            if (df != null) {
                getActivityLog().logDeliveryFileError(
                        ILogEvent.RND_FAILED, df.getId(), df.getName(), owner, pe.getMessage());
            }
            LOG.error(pe);

        } catch (Throwable th) {
            LOG.error(th.getMessage(), th);
        }
    }

    private List<Record> getRecords(Integer dbId, Collection<String> cdNumbers) {
        if (cdNumbers.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecordEntity> records = rs.getRecordsByDb(dbId, cdNumbers);
        List<Record> ret = new ArrayList<>(records.size());
        records.forEach(r -> ret.add(new Record(r)));
        return ret;
    }
}
