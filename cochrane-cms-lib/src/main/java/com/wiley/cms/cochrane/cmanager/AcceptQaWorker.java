package com.wiley.cms.cochrane.cmanager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import javax.ejb.EJBException;
import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.rpc.ServiceException;

import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DelayedThread;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.parser.QaParsingResult;
import com.wiley.cms.cochrane.cmanager.parser.QaResultHandler;
import com.wiley.cms.cochrane.cmanager.parser.ResultParser;
import com.wiley.cms.cochrane.process.RenderingHelper;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;

/**
 * @author Galina Kristich
 */
@Deprecated
public class AcceptQaWorker implements Runnable {
    private static final Logger LOG = Logger.getLogger(AcceptQaWorker.class);

    private static final int ATTEMPT_CREATING = 3;

    private static final String JOB_ID = "jobId=";
    private static final String DELIVERY_FILE_NAME = "delivery file name";
    private static final String FROM = " from ";
    private static final String ERROR_IN_UPDATED = "updated record already exists in entire database";

    protected boolean isTranslatedAbstracts;
    protected IActivityLogService logService;
    protected IResultsStorage rs;

    protected List<Record> records;
    protected DeliveryFileVO deliveryFileVO;
    protected QaParsingResult result;

    protected AcceptQaResults acceptQaResults;

    private IRepository rps;
    private IEntireDBStorage edbs;

    private int jobId;
    private String qaResult;

    private SAXParserFactory factory;

    private enum ValidationStatus {
        SUCCESS, WARNING, FAIL
    }

    protected AcceptQaWorker(AcceptQaResults acceptQaResults, final int jobId, final String qaResult) {

        this();

        this.acceptQaResults = acceptQaResults;
        this.jobId = jobId;
        this.qaResult = qaResult;
    }

    protected AcceptQaWorker() {

        factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);
    }

    public void setQaResult(String qaResult) {
        this.qaResult = qaResult;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public void setAcceptQaResults(AcceptQaResults acceptQaResults) {
        this.acceptQaResults = acceptQaResults;
    }

    public void run() {
        work(jobId);
        checkNextJob();
    }

    private void checkNextJob() {
        synchronized (AcceptQaResults.class) {
            if (((ThreadPoolExecutor) AcceptQaResults.executor).getQueue().size() > acceptQaResults.queueCount) {
                acceptQaResults.LOG.debug("Thread queue in executor is full");
                return;
            }
            try {
                IQaService qaService = initQasService();
                DelayedThread job = qaService.getQaNextJob();
                if (job != null) {
                    AcceptQaWorker newWorker = this.getClass().newInstance();
                    newWorker.setAcceptQaResults(acceptQaResults);
                    newWorker.setJobId(job.getJobId());
                    newWorker.setQaResult(job.getResult());
                    AcceptQaResults.executor.execute(newWorker);
                }
            } catch (NamingException e) {
                AcceptQaResults.LOG.error(e, e);
            } catch (InstantiationException e) {
                ;
            } catch (IllegalAccessException e) {
                ;
            }
        }
    }

    protected DeliveryFileVO getDeliveryFile(int issueId, String dbName, String recordName) {
        return rs.getDeliveryFileVO(issueId, dbName, recordName);
    }

    private void work(int jobId) {
        AcceptQaResults.LOG.debug("accepting qa result started jobId=" + jobId);
        try {
            init();

            parse();
            records = result.getRecords();
            if (records == null || records.size() == 0) {
                throw new Exception("List of parsing files = null ");
            }

            String errorReport = buildErrorReport();
            LOG.debug(errorReport);

            deliveryFileVO = getDeliveryFile(result.getIssueId(), result.getDbName(), records.get(0).getName());
            if (deliveryFileVO == null) {
                LOG.debug(String.format("NULL deliveryFileVO, db=%s, issue=%d", result.getDbName(),
                        result.getIssueId()));
            }
            String deliveryFileName = deliveryFileVO.getName();

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.QAS_RESULTS_ACCEPTED,
                    deliveryFileVO.getId(), deliveryFileVO.getName(),
                    acceptQaResults.logUser, JOB_ID + jobId + ", " + DELIVERY_FILE_NAME + ": "
                    + deliveryFileName);

            updateRecords(!isTranslatedAbstracts && DeliveryPackage.isMeshterm(deliveryFileName),
                    RevmanPackage.isAut(deliveryFileName));

            ValidationResult centralValidationResult = validateCentralRecords();
            validateAndUpdateStatuses(centralValidationResult);

            //boolean passed = checkRecordListPassed(DeliveryPackage.isSystemReview(deliveryFileName) ? 1 : 2);

            boolean passed = result.isSuccessful();

            Map<String, String> map = initNotifyMessage(deliveryFileName, jobId);

            if (passed) {
                onPassed(rs);
                MessageSender.sendMessage("qas_job_successful", map);
            } else {
                onFailed(rs);
            }

            sendValidationNotifications(map, centralValidationResult, errorReport);

            clearJob(jobId);
        } catch (Exception e) {
            AcceptQaResults.LOG.error(e, e);

            logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.QAS_ACCEPT_FAILED,
                    deliveryFileVO.getId(), deliveryFileVO.getName(), acceptQaResults.logUser, e.getMessage());
        }
    }

    private void init() {

        logService = AbstractManager.getActivityLogService();
        rs = ResultStorageFactory.getFactory().getInstance();
        edbs = EntireDBStorageFactory.getFactory().getInstance();
        rps = RepositoryFactory.getRepository();
    }

    private String buildErrorReport() {
        StringBuilder report = new StringBuilder();
        for (Record record : result.getRecords()) {
            if (!record.isSuccessful()) {
                String title = StringUtils.isEmpty(record.getTitle())
                        ? CochraneCMSPropertyNames.getNotAvailableMsg()
                        : record.getTitle();
                report.append(record.getName())
                        .append("\ntitle = \"").append(title).append("\"\n")
                        .append(ProcessHelper.parseErrorReportMessageXml(record.getMessages(), true)).append("\n");
            }
        }

        return report.toString();
    }

    private void validateAndUpdateStatuses(ValidationResult validationResult) throws NamingException {
        if ((validationResult == null) || (validationResult.failedResultRecords == null)) {
            return;
        }

        Set<String> badRecordNames = getBadRecordNames(validationResult.failedResultRecords);

        updateRecordStatusesForCentral(badRecordNames);
        updateJobQasResults(validationResult.failedResultRecords);
    }

    private ValidationResult validateCentralRecords() {
        if (!result.getDbName().equals(CochraneCMSPropertyNames.getCentralDbName())
                || !CochraneCMSPropertyNames.isCentralStatusValidation()) {
            return null;
        }

        List<String> badRecords = new ArrayList<String>();
        List<RecordValidationResult> failedRecords = new ArrayList<RecordValidationResult>();
        List<RecordValidationResult> successRecordsWithWarnings = new ArrayList<RecordValidationResult>();
        DatabaseEntity database = rs.getDatabaseEntity(CochraneCMSPropertyNames.getCentralDbName());

        Map<Record, RecordValidationResult> duplicateRecords =  new HashMap<Record, RecordValidationResult>();

        for (Record record : records) {

            if (!record.isSuccessful()) {
                continue;
            }

            RecordValidationResult recordValidationResult = validateCentralRecord(database, record);

            if (recordValidationResult != null) {

                if (ValidationStatus.FAIL.equals(recordValidationResult.status)) {
                    badRecords.add(record.getRecordSourceUri());
                    failedRecords.add(recordValidationResult);

                    record.setSuccessful(false);
                    record.setQasErrorCause(recordValidationResult.buildQasErrorMessage());
                } else if (recordValidationResult.hasDuplicate()) {
                    duplicateRecords.put(record, recordValidationResult);
                } else if (ValidationStatus.WARNING.equals(recordValidationResult.status)) {
                    successRecordsWithWarnings.add(recordValidationResult);
                }
            }
        }

        if (!duplicateRecords.isEmpty()) {

            LOG.warn("Suspicious duplicates: " + duplicateRecords.size());

            for (Record record: duplicateRecords.keySet()) {

                RecordValidationResult recordValidationResult = duplicateRecords.get(record);

                validateCentralRecordByDuplicate(record, recordValidationResult);

                if (ValidationStatus.FAIL.equals(recordValidationResult.status)) {

                    badRecords.add(record.getRecordSourceUri());
                    failedRecords.add(recordValidationResult);
                    record.setSuccessful(false);
                    record.setQasErrorCause(recordValidationResult.buildQasErrorMessage());

                } else if (ValidationStatus.WARNING.equals(recordValidationResult.status)) {
                    successRecordsWithWarnings.add(recordValidationResult);
                }
            }
        }

        result.getBadFiles().addAll(badRecords);
        result.setBadCount(result.getBadCount() + badRecords.size());
        result.setGoodCount(result.getGoodCount() - result.getBadCount());

        return new ValidationResult(failedRecords, successRecordsWithWarnings);
    }

    private RecordValidationResult validateCentralRecord(DatabaseEntity database, Record record) {
        if (record.getUnitStatus() == null) {
            LOG.warn("Record " + record.getName() + " does not contains status information");
            return null;
        }

        RecordValidationResult validationResult = new RecordValidationResult(record);
        EntireDBEntity entireEntity = edbs.findRecordByName(database, record.getName());

        switch (record.getUnitStatus()) {
            case UnitStatusEntity.UnitStatus.NEW:
                validationResult.unitStatus = "NEW";
                if (entireEntity != null) {
                    validationResult.status = ValidationStatus.FAIL;
                    validationResult.files = record.getFilesList();
                    validationResult.errorMessage = "record already exists in entire database";
                } else {
                    //validateCentralRecordByTile(database, record, validationResult);
                    validationResult.status = ValidationStatus.SUCCESS;
                }
                break;
            case UnitStatusEntity.UnitStatus.UPDATED:
                validationResult.unitStatus = "UPDATED";
                validateRecordTitles(record, entireEntity, validationResult);
                break;
            case UnitStatusEntity.UnitStatus.DELETED:
                validationResult.unitStatus = "DELETED";
                validateRecordTitles(record, entireEntity, validationResult);
                break;
            default:
                break;
        }

        return validationResult;
    }

    private void validateRecordTitles(Record record, EntireDBEntity entireEntity,
                                      RecordValidationResult validationResult) {
        if (entireEntity == null) {
            validationResult.status = ValidationStatus.FAIL;
            validationResult.files = record.getFilesList();
            validationResult.errorMessage = "updated record does not exists in entire database";
        }
    }

    private void validateCentralRecordByDuplicate(Record record, RecordValidationResult validationResult) {

        String dupName = validationResult.duplicate.getName();
        String path = FilePathCreator.getFilePathToSourceEntire(CochraneCMSPropertyNames.getCentralDbName(), dupName);

        ResultParser rpDup = RenderingHelper.parseCentralSource(path, factory, rps);
        if (rpDup == null) {
            // nothing to do here
            return;
        }

        ResultParser rp = RenderingHelper.parseCentralSource(record.getRecordSourceUri(), factory, rps);
        if (rp == null) {
            // nothing to do here
            return;
        }

        if (!record.getName().equals(dupName)) {
            // it's a new record
            if (!rp.equalsResult(ResultParser.CENTRAL_YEAR_TAG, rpDup.getResult(ResultParser.CENTRAL_YEAR_TAG))
                || !rp.equalsResult(ResultParser.CENTRAL_AUTHOR_TAG, rpDup.getResult(ResultParser.CENTRAL_AUTHOR_TAG))
                || !rp.equalsResult(ResultParser.CENTRAL_PAGE_TAG, rpDup.getResult(ResultParser.CENTRAL_PAGE_TAG))) {

                validationResult.status = ValidationStatus.SUCCESS;
            }

        } else {
            // it's an updated record
            String year = rpDup.getResult(ResultParser.CENTRAL_YEAR_TAG);
            String authors = rpDup.getResult(ResultParser.CENTRAL_AUTHOR_TAG);
            String page = rpDup.getResult(ResultParser.CENTRAL_PAGE_TAG);

            if (!rp.equalsResult(ResultParser.CENTRAL_YEAR_TAG, year)) {
                validationResult.errorMessage = ERROR_IN_UPDATED + " with different year '" + year + "'";
            } else if (!rp.equalsResult(ResultParser.CENTRAL_AUTHOR_TAG, authors)) {
                validationResult.errorMessage = ERROR_IN_UPDATED + " with different authors '" + authors + "'";
            } else if (!rp.equalsResult(ResultParser.CENTRAL_PAGE_TAG, page)) {
                validationResult.errorMessage = ERROR_IN_UPDATED + " with different page '" + page + "'";
            } else {
                validationResult.status = ValidationStatus.SUCCESS;
            }
        }
    }


    private Set<String> getBadRecordNames(List<RecordValidationResult> validationResult) {
        if ((validationResult == null) || (validationResult.isEmpty())) {
            return null;
        }

        Set<String> badRecordNames = new HashSet<String>();
        for (RecordValidationResult res : validationResult) {
            badRecordNames.add(res.recordName);
        }

        return badRecordNames;
    }

    private void updateJobQasResults(List<RecordValidationResult> results) {
        if ((results == null) || (results.isEmpty())) {
            return;
        }

        try {
            IQaService qaService = initQasService();

            for (RecordValidationResult res : results) {
                qaService.updateQaResults(res.recordId,
                        "<messages><uri name=\"" + res.recordName + ".xml" + "\"/>"
                                + "<message quality=\"\">File have failed QA: " + res.errorMessage
                                + "</message></messages>");
            }
        } catch (NamingException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private Map<String, String> initNotifyMessage(String deliveryFileName, int jobId) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(MessageSender.MSG_PARAM_DELIVERY_FILE, deliveryFileName);
        map.put(MessageSender.MSG_PARAM_JOB_ID, Integer.toString(jobId));
        return map;
    }

    private void sendValidationNotifications(Map<String, String> map, ValidationResult validationResult,
        String qaServiceResult) {

        if (validationResult != null) {

            sendErrorNotifications(map, validationResult.warningResultRecords,
                result.getDbName().equals(CochraneCMSPropertyNames.getCentralDbName())
                        ? "warnings_central" : "warnings");
            sendErrorNotifications(map, validationResult.failedResultRecords, "errors");
        }

        if (!result.isSuccessful()) {

            map.put("database", result.getDbName());
            map.put("report", qaServiceResult);
            if (deliveryFileVO != null) {
                map.put(DELIVERY_FILE_NAME, CmsUtils.getOrDefault(deliveryFileVO.getName()));
            }

            String identifiers =  MessageSender.getCDnumbersFromMessageByPattern(qaServiceResult, map);
            map.put(MessageSender.MSG_PARAM_RECORD_ID, identifiers);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_QAS_JOB_FAILED, map);

        } else if (result.getBadCount() > 0) {

            map.put(MessageSender.MSG_PARAM_REQUEST, qaServiceResult);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_RESUPPLY_REQUEST, map);
        }


        /*if (result.getBadCount() > 0) {
            String resupply = createResupply();
            map.put(NOTIFY_MESSAGE_BODY, resupply);
            MessageSender.sendMessage("resupply_request", map);
            AcceptQaResults.LOG.debug(resupply);
        }*/
    }

    private void sendErrorNotifications(Map<String, String> map, List<RecordValidationResult> errors,
                                        String requestTitle) {
        if (errors != null && !errors.isEmpty()) {

            String warnings = createNotificationMessage(errors,
                    "Qas was completed with errors for the following files:");
            map.put(MessageSender.MSG_PARAM_REQUEST, warnings);
            MessageSender.sendMessage(requestTitle, map);
            AcceptQaResults.LOG.debug(warnings);
        }
    }

    /*private String createResupply() {
        StringBuilder str = new StringBuilder();
        str.append("Qas failed for the following files: \n");
        for (Record record : records) {
            if (record.isSuccessful()) {
                continue;
            }

            str.append(record.getName()).append("\n");
            str.append(record.getQasErrorCause()).append("\n");

            for (int i = 0; i < record.getFilesList().size(); i++) {
                str.append(record.getFilesList().get(i)).append("\n");
            }
        }

        return str.toString();
    }*/

    private String createNotificationMessage(List<RecordValidationResult> recordValidationResults, String header) {

        StringBuilder str = new StringBuilder();
        str.append(header).append("\n");

        for (RecordValidationResult validationResult : recordValidationResults) {
            if (validationResult != null) {
                str.append(validationResult.recordName).append("\n");
                str.append(validationResult.buildQasErrorMessage()).append("\n");

                if (!validationResult.hasDuplicate()) {

                    for (int i = 0; i < validationResult.files.size(); i++) {
                        str.append(validationResult.files.get(i)).append("\n");
                    }
                }
            }
        }

        return str.toString();
    }

    private void parse() throws ParserConfigurationException, SAXException, NamingException, IOException {
        AcceptQaResults.LOG.debug("parse start");

        SAXParser parser = factory.newSAXParser();
        result = new QaParsingResult();
        parser.parse(new ByteArrayInputStream(qaResult.getBytes()),
                new QaResultHandler(jobId, result));
        qaResult = null;
        AcceptQaResults.LOG.debug("parse finish");
    }

    private void clearJob(int jobId) throws MalformedURLException, NamingException, ServiceException {
        //new ProvideQaClient(false).clearJobResult(jobId);
        IProvideQa ws = WebServiceUtils.getProvideQa();
        ws.clearJobResult(jobId);

        rs.deleteStartedJobQaId(jobId);
    }

    private void onPassed(IResultsStorage rs) throws Exception {
        List<Record> goodRecList = new ArrayList<Record>();
        int goodRecordsCount = fillRecordList(goodRecList);

        String text = "Passed " + goodRecordsCount + FROM + records.size();

        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.QAS_PASSED, deliveryFileVO.getId(),
                deliveryFileVO.getName(), acceptQaResults.logUser, JOB_ID + jobId);

        AcceptQaResults.LOG.debug(text);

        IRenderingService rndService = initRenderingService();

        long recsCountByDf = rndService.getRecordCountByDf(deliveryFileVO.getId());
        // records after qas not contain all delivery file records, it's a part
        String recNames = null;
        if (recsCountByDf != records.size()) {
            recNames = CmsUtils.createRecordNamesList(goodRecList);
        }

        synchronized (AcceptQaResults.class) {
            for (int i = 0; i < ATTEMPT_CREATING; i++) {
                try {
                    startRendering(rndService, recNames);
                    break;
                } catch (EJBException e) {
                    AcceptQaResults.LOG.debug("second attempt to start rendering because of persistenceException");
                    AcceptQaResults.LOG.debug(e, e);
                    try {
                        Thread.sleep(AcceptQaResults.DELAY_BECAUSE_OF_LOCK);
                    } catch (InterruptedException e1) {
                        AcceptQaResults.LOG.debug(e, e);
                    }
                }
            }
        }
    }

    protected void startRendering(IRenderingService rndService, String recNames) throws Exception {
        if (!rndService.startRendering(deliveryFileVO.getId(), recNames)) {
            logService.error(ActivityLogEntity.EntityLevel.FILE,
                    ILogEvent.RND_NOT_STARTED,
                    deliveryFileVO.getId(),
                    deliveryFileVO.getName(),
                    acceptQaResults.logUser,
                    "there are no records");
            if (rndService.isQasCompleted(deliveryFileVO.getId())) {
                rs.setDeliveryFileStatus(deliveryFileVO.getId(),
                        IDeliveryFileStatus.STATUS_RND_NOT_STARTED, false);
            }
        }
    }


    private int fillRecordList(List<Record> goodRecList) {
        int count = 0;
        for (Record record : records) {
            if (record.isSuccessful()) {
                count++;
            }
            if (record.isSuccessful()) {
                goodRecList.add(record);
            }
//                else if (!record.isSuccessful() && !record.isHangingOut())
//                {
//                    failedRecList.add(record);
//                }
        }
        return count;
    }

    private void deleteBadFiles() {
        if (result.getBadCount() == 0) {
            return;
        }
        AcceptQaResults.LOG.debug("deleteBadFiles start");
        for (String filePath : result.getBadFiles()) {
            if (filePath == null) {
                break;
            }
            try {
                acceptQaResults.rps.deleteFile(filePath);
            } catch (IOException e) {
                AcceptQaResults.LOG.debug(e, e);
            }
        }
        AcceptQaResults.LOG.debug("deleteBadFiles finish");
        //List<Record> recs = new ArrayList<Record>(badCount);
        //badRecordsCount = fillRecordList(recs, false);
        //rs.deleteTempRecords(recs);
    }


    private void onFailed(IResultsStorage rs) throws NamingException {
        String text = "Failed " + result.getBadCount()
                + FROM + (result.getBadCount() + result.getGoodCount()) + " files";

        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.QAS_FAILED,
                deliveryFileVO.getId(), deliveryFileVO.getName(),
                acceptQaResults.logUser, null);


        AcceptQaResults.LOG.debug(text);

        List<Record> recs = new ArrayList<Record>(records.size());

        //@todo Deleting after QA ??????????
        //CmsUtils.deleteSourceWithImages(fillAllFilePath());
        //fillAllRecordList(recs);
        IRenderingService rndService = initRenderingService();
        if (rndService.isQasCompleted(deliveryFileVO.getId())) {

            rs.setDeliveryFileStatus(deliveryFileVO.getId(), IDeliveryFileStatus.STATUS_QAS_FAILED, false);
            rs.setDeliveryFileStatus(deliveryFileVO.getId(), IDeliveryFileStatus.STATUS_QAS_ACCEPTED, true);
        }
    }

    private IRenderingService initRenderingService() throws NamingException {
        return CochraneCMSPropertyNames.lookup("RenderingService", IRenderingService.class);
    }

    private boolean checkRecordListPassed(int divisor) {
        if (!result.isSuccessful()) {
            return false;
        }
        boolean passed = false;
        if (result.getGoodCount() > result.getBadCount() / divisor) {
            passed = true;
        }
        return passed;
    }

    private void updateRecords(boolean isMeshterm, boolean isWhenReady) throws NamingException {
        if (records == null || records.size() == 0) {
            return;
        }
        AcceptQaResults.LOG.debug("updateRecords start size=" + records.size());

        //@todo      deleteOldSourceRecordsFile
        //deleteOldSourceRecordsFiles(recs, dbName, issueName);
        IQaService qaService = initQasService();
        //qaService.parseSources(records, result.getDbName(), result.getIssueId());
        qaService.updateRecords(records, result.getDbName(), result.getIssueId(), isTranslatedAbstracts,
                isMeshterm, isWhenReady);

        AcceptQaResults.LOG.debug("updateRecords finish");
    }

    private IQaService initQasService() throws NamingException {
        return CochraneCMSPropertyNames.lookup("QaService", IQaService.class);
    }

    private void updateRecordStatusesForCentral(Set<String> badRecordNames) throws NamingException {
        if (badRecordNames == null || badRecordNames.size() == 0) {
            return;
        }

        IQaService qaService = initQasService();
        qaService.updateRecordsQAStatuses(badRecordNames, result.getDbName(), result.getIssueId(),
                deliveryFileVO.getId(), false);
    }

    private class RecordValidationResult {
        private String recordName;
        private int recordId;
        private String errorMessage;
        private String unitStatus;
        private ValidationStatus status;
        private List<String> files;
        private String sortTitle;
        private EntireDBEntity duplicate;

        private RecordValidationResult(Record rec) {
            this.recordId = rec.getId();
            this.recordName = rec.getName();
        }

        private String buildQasErrorMessage() {
            return "record id = [" + this.recordName + "], unitStatus = ["
                    + this.unitStatus + "], sortTitle = [" + this.sortTitle + "]: " + this.errorMessage;
        }

        boolean hasDuplicate() {
            return duplicate != null;
        }
    }

    private class ValidationResult {
        private List<RecordValidationResult> failedResultRecords;
        private List<RecordValidationResult> warningResultRecords;

        private ValidationResult(List<RecordValidationResult> failedResultRecords,
                                 List<RecordValidationResult> warningResultRecords) {
            this.failedResultRecords = failedResultRecords;
            this.warningResultRecords = warningResultRecords;
        }
    }
}