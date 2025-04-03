package com.wiley.cms.cochrane.process;

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
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.process.handler.PackageHandler;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.qaservice.services.IQaProvider;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
@MessageDriven(activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/accept_qa"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "" + IQaProvider.MAX_SESSION)
        }, name = "AcceptQaQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptQaQueue extends BaseAcceptQueue implements MessageListener {
    static final String QUEUE_DESTINATION = "java:jboss/exported/jms/queue/accept_qa";

    @EJB(beanName = "QaManager")
    private IQaManager manager;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    @EJB(beanName = "RenderingManager")
    private IRenderingManager rndManager;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    public IQaManager getQaManager() {
        return manager;
    }

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        String logUser = CochraneCMSPropertyNames.getActivityLogSystemName();
        PackageHandler handler = null;
        try {
            ExternalProcess cp = JMSSender.getObjectMessage(message, ExternalProcess.class);
            int jobId = cp.getId();
            ProcessVO creator = manager.findProcess(cp.getCreatorId());
            handler = ProcessHandler.castProcessHandler(creator.getHandler(), PackageHandler.class);

            LOG.debug(String.format("accepting QA result started for process %s", cp));

            String report = manager.buildProcessReport(jobId, null, cp.getState().isSuccessful());
            LOG.debug(String.format("report was created for process %s", cp));

            String dfName = handler.getPackageName();
            initParserFactory();
            QaManager.QaResult result = parseQaResult(jobId, report, dfName);
            if (!result.isCompleted()) {
                LOG.warn(String.format("render was uncompleted for process %s", cp));
            }

            int dfId = handler.getPackageId();
            DeliveryFileVO df = dfStorage.getDfVO(dfId);
            int dbId = df.getDbId();

            boolean isCentral = result.getDbName().equals(CochraneCMSPropertyNames.getCentralDbName());
            boolean isTA = false;
            boolean isCDSR = false;
            if (isCentral) {
                result.checkDeleted();

            } else if (result.getDbName().equals(CochraneCMSPropertyNames.getCDSRDbName())) {
                isCDSR = true;
                isTA = DeliveryPackage.isTranslatedAbstract(df.getType());
            }

            String errorReport = result.buildErrorReport();
            LOG.debug(errorReport);

            boolean completed;
            OpStats stats;
            synchronized (creator) {
                stats = manager.updateRecords(jobId, result, df, isCDSR, isTA, false, false);
                completed = stats.isCompleted();
            }
            getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_RESULTS_ACCEPTED, jobId, dfId, dfName, logUser);

            if (result.isSuccessful()) {
                onPass(jobId, result, errorReport, df, logUser, creator, stats);
            } else {
                onFail(jobId, result, errorReport, df, logUser, creator, stats);
            }

            manager.deleteProcess(creator, jobId, completed);

            if (completed && df.isRenderingNotStarted() && creator.hasCreator()) {
                manager.deleteProcess(creator.getCreatorId(), true);
            }

        } catch (ProcessException pe) {
            if (handler != null) {
                getActivityLog().logDeliveryFileError(ILogEvent.QAS_FAILED, handler.getPackageId(),
                        handler.getPackageName(), logUser, pe.getMessage());
            }
            LOG.error(pe.getMessage());

        } catch (Throwable th) {
            LOG.error(th.getMessage(), th);
        }
    }

    private void onPass(int jobId, QaManager.QaResult result, String report, DeliveryFileVO df, String logUser,
        ProcessVO creator, OpStats stats) {

        int dfId = df.getId();
        String dfName = df.getName();
        List<Record> records = result.getRecords();
        Map<Integer, Record> goodRecList = new HashMap<>();

        int reportBadCount = 0;
        for (Record record : records) {

            if (record.isDeleted()) {
                continue;
            }

            if (record.isSuccessful()) {
                goodRecList.put(record.getId(), record);
            } else {
                reportBadCount++;
            }
        }

        LOG.debug(String.format("passed %d from %d, deleted %d", goodRecList.size(), records.size(),
                result.getWithdrawnCount()));

        getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_PASSED, jobId, dfId, dfName, logUser);

        if (stats.isCompleted()) {
            stats.setCurrent(goodRecList.size());
            manager.finishQa(creator.getId(), result.getIssueId(), result.getDbName(), df, stats);
        }

        if (!goodRecList.isEmpty()) {
            startRendering(goodRecList, df, creator, stats.isCompleted());
        }

        MessageSender.sendSuccessfulQAS(jobId, dfName);
        if (reportBadCount > 0) {
            MessageSender.sendResupply(jobId, dfName, report);
        }
    }

    private void onFail(int jobId, QaManager.QaResult result, String report, DeliveryFileVO df, String logUser,
        ProcessVO creator, OpStats opStats) {

        int dfId = df.getId();
        String dfName = df.getName();

        LOG.debug(String.format("failed %d from %d files", result.getBadCount(),
            result.getBadCount() + result.getGoodCount()));
        getActivityLog().logDeliveryFileProcess(ILogEvent.QAS_FAILED, jobId, dfId, dfName, logUser);

        if (opStats.isCompleted()) {
            opStats.setCurrent(result.getGoodCount());
            manager.finishQa(creator.getId(), result.getIssueId(), result.getDbName(), df, opStats);
            rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_QAS_ACCEPTED, false);
        }

        MessageSender.sendFailedQAS(jobId, dfName, result.getDbName(), report);
    }

    private void startRendering(Map<Integer, Record> records, DeliveryFileVO df, ProcessVO creator, boolean completed) {

        ProcessVO pvo = rndManager.startRendering(creator, df, records);
        if (pvo == null || pvo.getState().isFailed()) {

            if (completed && !df.isRenderingStarted()) {
                rs.setDeliveryFileStatus(df.getId(), IDeliveryFileStatus.STATUS_RND_NOT_STARTED, false);
            }
        }
    }

    private void updateBadRecord(Record record,
                                 Collection<String> failedRecords,
                                 Collection<RecordValidationResult> invalidRecords,
                                 RecordValidationResult recordValidationResult) {

        failedRecords.add(record.getRecordSourceUri());
        invalidRecords.add(recordValidationResult);
        record.setSuccessful(false);
        record.setQasErrorCause(recordValidationResult.buildQasErrorMessage());
        record.addMessages(recordValidationResult.buildQasErrorMessageXml());
    }

    private enum ValidationStatus {
        SUCCESS, WARNING, FAIL
    }

    private static final class RecordValidationResult {
        private final String recordName;
        private String errorMessage;
        private String unitStatus;
        private ValidationStatus status;
        private List<String> files;
        private String sortTitle;

        private RecordValidationResult(IRecord record) {
            recordName = record.getName();
        }

        private String buildQasErrorMessage() {
            return String.format("record id = [%s], unitStatus = [%s], sortTitle = [%s]: %s",
                    recordName, unitStatus, sortTitle, errorMessage);
        }

        private String buildQasErrorMessageXml() {
            return "Record has failed status validation: " + errorMessage;
        }
    }
}
