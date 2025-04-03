package com.wiley.cms.cochrane.cmanager.modifying;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.IQaService;
import com.wiley.cms.cochrane.cmanager.IRenderingService;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.df.DfStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class ModifyRecordProcess implements IQASResults, Runnable {
    private static final Logger LOG = Logger.getLogger(ModifyRecordProcess.class);

    private static final long DELAY = 1000;
    private static final int MAX_PASSED_QUALITY = 100;
    private static final int MILLISECONDS_IN_SECOND = 1000;
    private static final String MESSAGE_QUALITY = "quality";
    private static final String SUCCESS_VALUE = "true";
    private static final String FILES = "files";
    private static final String EDIT = "edit";

    private String profile;
    private String qaPlan;
    private String xmlBody;
    private String dbName;
    private int recordId;
    private String recordName;
    private IVisit visit;

    private boolean renderAutoStart = true;

    private int jobId;

    private String qasResults;
    private String qasResultMessages;
    private boolean qasCompleted;
    private boolean qasSuccessful;
    private Map<String, String> messageMapPassed = new TreeMap<>();
    private Map<String, String> messageMapFailed = new TreeMap<>();

    private ScheduledExecutorService executor;

    private SAXBuilder builder;

    private IResultsStorage rs;
    private IQaService qaservice;

    public ModifyRecordProcess(String profile, String qaPlan,
                               String xmlBody, String dbName, int recordId,
                               String recordName, IVisit visit) throws Exception {
        this.profile = profile;
        this.qaPlan = qaPlan;
        this.xmlBody = xmlBody;
        this.dbName = dbName;

        this.recordId = recordId;
        this.recordName = recordName;

        this.visit = visit;

        executor = Executors.newSingleThreadScheduledExecutor();
        builder = new SAXBuilder();

        rs = ResultStorageFactory.getFactory().getInstance();
        qaservice = CochraneCMSPropertyNames.lookup("QaService", IQaService.class);
    }

    public void setRenderAutoStart(boolean value) {
        renderAutoStart = value;
    }

    public boolean isRenderAutoStart() {
        return renderAutoStart;
    }

    public String getXmlBody() {
        return xmlBody;
    }

    public void startQas() throws Exception {
        if (isRenderAutoStart()) {
            AbstractManager.getActivityLogService().info(ActivityLogEntity.EntityLevel.RECORD,
                    ILogEvent.EDIT, recordId, recordName, visit.getLogin(),
                    null);
        }
        setRecordDisabled();

        IProvideQa qaClient = null;
        try {
            qaClient = WebServiceUtils.getProvideQa();
            jobId = qaClient.checkXml(profile, qaPlan, xmlBody, dbName);
        } finally {
            WebServiceUtils.releaseServiceProxy(qaClient, IProvideQa.class);
        }
        if (jobId == -1) {
            throw new Exception("Qas failed, jobId=-1");
        }
    }

    private void setRecordDisabled() {
        RecordEntity record = rs.getRecord(recordId);
        record.setDisabled(true);
        rs.mergeRecord(record);
    }

    private void setRecordEnabled() {
        RecordEntity record = rs.getRecord(recordId);
        record.setDisabled(false);
        rs.mergeRecord(record);
    }

    public void suspend(int seconds) throws Exception {
        Thread.sleep(seconds * MILLISECONDS_IN_SECOND);
    }

    public void requestResults() throws Exception {
        LOG.debug("Request results started ...");
        IProvideQa qaClient = null;
        try {
            qaClient = WebServiceUtils.getProvideQa();

            qasResults = qaClient.getJobResults(jobId);
            Document doc = processResults();
            if (isQasCompleted()) {
                if (isQasSuccessful()) {
                    doOnPassed(doc, qaClient);
                } else {
                    doOnFailed();
                }
            } else {
                executor.schedule(this, DELAY, TimeUnit.MILLISECONDS);
            }
        } finally {
            WebServiceUtils.releaseServiceProxy(qaClient, IProvideQa.class);
        }
    }

    public boolean isQasCompleted() {
        return qasCompleted;
    }

    public void setQasCompleted(boolean qasCompleted) {
        this.qasCompleted = qasCompleted;
    }

    public boolean isQasSuccessful() {
        return qasSuccessful;
    }

    public void setQasSuccessful(boolean qasSuccessful) {
        this.qasSuccessful = qasSuccessful;
    }

    public int getRecordId() {
        return recordId;
    }

    public String getQasResults() {
        return qasResults;
    }

    public IVisit getVisit() {
        return visit;
    }

    public void setVisit(IVisit visit) {
        this.visit = visit;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    public Map<String, String> getMessagesPassed() {
        return messageMapPassed;
    }

    public Map<String, String> getMessagesFailed() {
        return messageMapFailed;
    }

    public void run() {
        try {
            requestResults();
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    private Document processResults() {
        Document doc = null;
        boolean completed;
        boolean passed = false;

        try {
            doc = builder.build(new StringReader(qasResults));
            Element commonInfo = doc.getRootElement().getChild("job");
            completed = commonInfo.getAttributeValue("completed").equals(SUCCESS_VALUE);
            passed = commonInfo.getAttributeValue("successful").equals(SUCCESS_VALUE);

            if (completed) {
                Element files = doc.getRootElement().getChild(FILES);
                List children = files.getChildren();
                Element file = (Element) children.get(0);

                String messagesTag = "messages";

                Element messages = file.getChild(messagesTag);
                fillMessages(messagesTag, messages);
            }
        } catch (Exception e) {
            LOG.error("Result parsing failed.", e);
            completed = true;
        }

        setQasCompleted(completed);
        setQasSuccessful(passed);

        return doc;
    }

    private void fillMessages(String messagesTag, Element messages) {
        if (messages == null) {
            return;
        }

        qasResultMessages = qasResults.substring(
                qasResults.indexOf("<" + messagesTag),
                qasResults.indexOf("</" + messagesTag + ">") + ("</" + messagesTag + ">").length()
        );

        List messageList = messages.getChildren();
        for (Object o : messageList) {
            Element message = (Element) o;
            if (Integer.parseInt(message.getAttributeValue(MESSAGE_QUALITY)) >= MAX_PASSED_QUALITY) {
                messageMapPassed.put(message.getValue(), message.getAttributeValue(MESSAGE_QUALITY));
            } else {
                messageMapFailed.put(message.getValue(), message.getAttributeValue(MESSAGE_QUALITY));
            }
        }
    }

    private void doOnPassed(Document doc, IProvideQa qaClient) {
        try {
            AbstractManager.getActivityLogService().info(ActivityLogEntity.EntityLevel.RECORD,
                    ILogEvent.QAS_PASSED, recordId, recordName,
                    visit.getLogin(), EDIT);
        } catch (Exception e) {
            LOG.debug(e);
        }

        clearQAJob(qaClient);

        RecordEntity record = rs.getRecord(recordId);

        if (!renderAutoStart) {
            record.setDisabled(false);
            rs.mergeRecord(record);
            return;
        }

        try {
            IRepository rps = RepositoryFactory.getRepository();

            ByteArrayInputStream bais = new ByteArrayInputStream(xmlBody.getBytes());
            rps.putFile(record.getRecordPath(), bais);
            bais.close();

            Element files = doc.getRootElement().getChild(FILES);
            if (files == null) {
                throw new Exception("Results must contain 'files' tag");
            }
            List children = files.getChildren();

            if (children.size() < 1) {
                throw new Exception("Files count must be exactly 1");
            }

            qaservice.updateQaResults(record.getId(), qasResultMessages);

            setUnitTitle(record);
            startRendering(record);

        } catch (Exception e) {
            LOG.debug(e, e);
        }
    }

    private void clearQAJob(IProvideQa qaClient) {
        qaClient.clearJobResult(jobId);
    }

    private void setUnitTitle(RecordEntity record) {
        String correctXml = CmsUtils.correctDtdPath(xmlBody);
        try {
            String[] tags = CmsUtils.fillTagsArray(dbName);

            Document doc = builder.build(new StringReader(correctXml));
            Element el = doc.getRootElement();
            String rootElementName = el.getName();
            for (int i = 0; i < tags.length; i++) {
                if (i == 0) {
                    if (!tags[i].equals(rootElementName)) {
                        el = el.getChild(tags[i]);
                    }
                } else {
                    el = el.getChild(tags[i]);
                }
            }
            rs.mergeRecord(record, el.getValue());

        } catch (Exception e) {
            LOG.debug(e);
        }
    }

    public void startRendering() throws Exception {
        setRecordDisabled();
        startRendering(rs.getRecord(recordId));
    }

    private void startRendering(RecordEntity record) throws Exception {
        URI uri = FilePathCreator.getUri(record.getRecordPath());
        clearRendering(record);
        URI[] uris = new URI[]{uri};
        URI[] htmlUris = null;

        if (dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())) {
            ITranslatedAbstractsInserter taInserter =
                    CochraneCMSPropertyNames.lookup("TranslatedAbstractsInserter", ITranslatedAbstractsInserter.class);
            BaseType bt = BaseType.find(dbName).get();
            DeliveryFileEntity df = record.getDeliveryFile();
            String path = bt.hasTranslationHtml()
                ? taInserter.getSourceForRecordWithInsertedAbstracts(record, record.getRecordPath(),
                    df.getIssue().getId(), df.getId(), bt.getTranslationModeHtml()) : record.getRecordPath();

            htmlUris = new URI[]{FilePathCreator.getUri(path)};
        }

        IRenderingService rndService = CochraneCMSPropertyNames.lookup("RenderingService", IRenderingService.class);
        rndService.startRendering(new Object[]{uris, new boolean[]{record.isRawDataExists()}, htmlUris},
                record.getDb().getTitle(), -1, " for record " + record.getName(), null);
        record.setEdited(true);

        record.setApproved(false);
        record.setRejected(false);
        record.setStateDescription(null);

        rs.mergeRecord(record);
        DfStorageFactory.getFactory().getInstance()
                .changeModifyStatus(record.getDeliveryFile().getId(), IDeliveryFileStatus.STATUS_EDITING_STARTED);
    }

    private void clearRendering(RecordEntity record) {
        try {
            ClDbEntity clDb = record.getDb();
            RecordHelper.removeRecordRenderedFolders(new RecordManifest(clDb.getIssue().getId(), clDb.getTitle(),
                    record.getName(), record.getRecordPath()));
        } catch (Exception e) {
            LOG.error(e, e);
        }
        clearDbRendering(record);
    }

    private void clearDbRendering(RecordEntity record) {

        rs.clearRecordsRenderings(record);
        record.setRenderingCompleted(false);
        record.setRenderingSuccessful(false);
        rs.mergeRecord(record);
    }

    private void doOnFailed() {
        try {
            AbstractManager.getActivityLogService().info(ActivityLogEntity.EntityLevel.RECORD,
                    ILogEvent.QAS_FAILED, recordId, recordName,
                    visit.getLogin(), EDIT);
        } catch (Exception e) {
            LOG.debug(e);
        }

        LOG.debug("Modifying record " + recordId + " interrupted for qas failed");
        setRecordEnabled();
    }
}
