package com.wiley.cms.cochrane.process;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CENTRALRecordsDeleter;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.IQaService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entity.AbstractRecord;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.parser.QaParsingResult;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.handler.PackageHandler;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.qaservice.services.IQaProvider;
import com.wiley.cms.qaservice.services.WebServiceUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
@Stateless
@Local(IQaManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class QaManager extends BaseManager implements IQaManager {

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage recordStorage;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    @EJB(beanName = "QaService")
    private IQaService qa;

    @Resource(mappedName = AcceptQaQueue.QUEUE_DESTINATION)
    private Queue acceptQueue;

    private URI callbackURI;
    private DeliveryPackageInfo packinfo;

    public void startQa(DeliveryPackageInfo dpi, int dfId, String dfName, String dbName) throws Exception {
        init(dpi);

        ProcessVO mainPvo = startMainPackageProcess(dfId, dfName, dbName);
        startProcess(mainPvo.getId(), new PackageHandler("QaPackage", dfId, dfName, dbName), mainPvo.getPriority());
    }

    public long getRecordCountByDf(int id) {
        return recordStorage.getRecordCountByDf(id);
    }

    public void acceptQaResults(int jobId) {
        ExternalProcess pvo = ps.getExternalProcess(jobId);
        if (pvo != null /*&& !pvo.hasEmptyType()*/) {
            try {
                ProcessVO creator = findProcess(pvo.getCreatorId());
                if (!creator.hasEmptyType()) {
                    acceptResults(creator, pvo, CochraneCMSBeans.getQueueProvider().getQueue(
                            creator.getType().getQueueName()));
                    return;
                }
            } catch (Exception e) {
                LOG.error(e);
                return;
            }
        }
        // to support an old approach
        acceptResults(jobId, acceptQueue);
    }

    public void parseRecords(Collection<Record> records, String dbName, int issueId, boolean isWhenReady) {
        qa.parseSources(records, dbName, issueId, isWhenReady);
    }

    public OpStats updateRecords(int processId, QaParsingResult result, DeliveryFileVO df,
                                 boolean isCDSR, boolean isTranslated, boolean toMl3g, boolean setRender) {
        List<Record> records = result.getRecords();
        int size = records.size();
        LOG.debug(String.format("updateRecords started size=%d, processId=%d", size, processId));

        recordStorage.flushQAResults(records, df.getDbId(), isCDSR, isTranslated, toMl3g, setRender);

        LOG.debug(String.format("updateRecords finished size=%d, processId=%d", size, processId));
        return recordStorage.getQasCompleted(df.getId());
    }

    public int getCountQaCompletedRecords(int dfId, boolean successful) {
        return (int) recordStorage.getCountQaByDfId(dfId, successful);
    }

    @Override
    protected void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {

        if (!pvo.hasCreator()) {
            return;  // skip main process
        }

        PackageHandler qah = ProcessHandler.castProcessHandler(handler, PackageHandler.class);
        int parentId = pvo.getId();
        try {
            IQaProvider provider = WebServiceUtils.getQaProvider();

            int partSize = CochraneCMSPropertyNames.getQaPartSize();
            List<URI> files = new ArrayList<>(partSize);
            int j = 0;

            for (List<String> list : packinfo.getRecords().values()) {
                if (j >= partSize) {
                    startProvidingQA(files.toArray(new URI[files.size()]), pvo,  provider);
                    j = 0;
                    files.clear();
                }
                for (String aFList : list) {
                    files.add(FilePathCreator.getUri(aFList));
                }
                j++;
            }
            if (j != 0) {
                startProvidingQA(files.toArray(new URI[files.size()]), pvo,  provider);
            }

            rs.setDeliveryFileStatus(qah.getPackageId(), IDeliveryFileStatus.STATUS_QAS_STARTED, true);
            logProcessForDeliveryPackage(parentId, qah.getPackageId(), qah.getPackageName(), ILogEvent.QAS_STARTED);
            MessageSender.sendStartedQAS(parentId, qah.getPackageName());

            WebServiceUtils.releaseServiceProxy(provider, IQaProvider.class);

        } catch (Throwable th) {

            logProcessForDeliveryPackage(parentId, qah.getPackageId(), qah.getPackageName(), ILogEvent.QAS_FAILED);
            MessageSender.sendFailedQAS(parentId, qah.getPackageName(), qah.getDbName(), th.getMessage());
            throw new ProcessException(th);
        }
    }

    private void startProvidingQA(URI[] uris, ProcessVO pvo, IQaProvider provider) {
        try {
            provider.provideQaMultiFile(pvo.getId(), "cochrane", "simple", uris, callbackURI, pvo.getPriority());

        } catch (Exception e) {
            LOG.error(e.getMessage());
            setProcessState(pvo, ProcessState.FAILED, e.getMessage());
        }
    }

    @Override
    public void sendQa(ProcessVO pvo, Integer dfId, String dfName, Collection<IRecord> records) throws Exception {
        init(null);
        IQaProvider provider = WebServiceUtils.getQaProvider();

        int partSize = pvo.getType().batch();
        List<URI> files = new ArrayList<>(partSize);
        for (IRecord record : records) {
            if (files.size() >= partSize) {
                startProvidingQA(files.toArray(new URI[files.size()]), pvo,  provider);
                files.clear();
            }
            files.add(FilePathCreator.getUri(record.getRecordPath()));
        }
        if (!files.isEmpty()) {
            startProvidingQA(files.toArray(new URI[files.size()]), pvo,  provider);
        }

        WebServiceUtils.releaseServiceProxy(provider, IQaProvider.class);
    }

    @Override
    public void finishQa(int creatorId, Integer issueId, String dbName, DeliveryFileVO df, OpStats stats) {
        BaseType bt = BaseType.find(dbName).get();
        if (bt.isCentral()) {
            List<String> currentDeleted = CENTRALRecordsDeleter.createDeletedRecordsList(issueId,
                    df.getDbId(), df.getId());
            try {
                CENTRALRecordsDeleter.deleteRecords(issueId);
                stats.setDeleted(currentDeleted.size());
                Date date = new Date();
                currentDeleted.forEach(name -> rm.addDbRecord(new DbRecordVO(bt, name, AbstractRecord.STATUS_DELETED,
                     df.getDbId(), df.getFullIssueNumber(), df.getId()), date, DatabaseEntity.CENTRAL_KEY));
                LOG.info(String.format("%d records deleted for processId=%d, package: %s",
                        stats.getDeleted(), creatorId, df.getName()));
            } catch (Exception e) {
                LOG.error(e);
            }
            stats.setTotalSuccessful((int) recordStorage.getCountQaByDfId(df.getId(), true));
        }
        if (stats.getCurrent() == 0) {
            //int countQaSuccessful = stats.getTotalSuccessful() - stats.getDeleted();
            if (stats.getTotalSuccessful() == 0) {
                rs.setDeliveryFileStatus(df.getId(), IDeliveryFileStatus.STATUS_RND_NOT_STARTED, false);
                df.setStatus(IDeliveryFileStatus.STATUS_RND_NOT_STARTED);
            }
            LOG.info(String.format("%d records QA passed for processId=%d, package: %s",
                    stats.getTotalSuccessful(), creatorId, df.getName()));
        }
    }

    private void init(DeliveryPackageInfo dpi) throws URISyntaxException {
        callbackURI = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
                    + "AcceptQaResults/AcceptQaProvider?wsdl");
        packinfo = dpi;
    }

    /**
     * QA result
     */
    static class QaResult extends QaParsingResult {

        private Collection<String> withdrawn;

        boolean isWithdrawn(String name) {
            return withdrawn != null && withdrawn.contains(name);
        }

        void addWithdrawn(String name) {
            if (withdrawn == null) {
                withdrawn =  new HashSet<>();
            }
            withdrawn.add(name);
        }

        int getWithdrawnCount() {
            return withdrawn ==  null ? 0 : withdrawn.size();
        }

        void checkDeleted() {
            List<Record> records = getRecords();
            for (Record rec: records) {
                if (UnitStatusEntity.isWithdrawn(rec.getUnitStatus())) {
                    addWithdrawn(rec.getName());
                    rec.setDeleted();
                }
            }
        }

        String buildErrorReport() {
            StringBuilder report = new StringBuilder("\n\n");
            int count = 0;
            for (Record record : getRecords()) {
                if (!record.isSuccessful() && !record.isDeleted()) {
                    report.append(record.getName()).append("\ntitle = \"").append(record.getTitle()).append("\"\n");
                    report.append(record.getErrorMessages()).append("\n");
                    count++;
                }
            }
            return String.format("\ncount: %d %s", count, report);
        }
    }
}
