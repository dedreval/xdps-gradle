package com.wiley.cms.cochrane.cmanager;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.rpc.ServiceException;

import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterIssueDb;
import org.apache.commons.collections.CollectionUtils;
import org.xml.sax.SAXException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.parser.RndParsingResult;
import com.wiley.cms.cochrane.cmanager.parser.RndResultHandler;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IProvideRendering;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 16.03.11
 */
//@MessageDriven(
//    activationConfig =
//        {
//            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
//            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/accept_rendering"),
//           @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "" + IRenderingProvider.MAX_SESSION)
//        }, name = "AcceptRenderingQueue"
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptRenderingQueue implements MessageListener {
    protected static Logger log = Logger.getLogger(AcceptRenderingQueue.class);

    private static final String JOB_ID = "jobId";
    private static final String PLAN = "plan";
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final int ATTEMPT_NUMBER = 3;
    private static final long DELAY_BECAUSE_OF_LOCK = 10000;
    private static final int PACK_SIZE = 1000;
    private static final String UNKNOWN_NAME = "unknown";

    protected String renderingResult;
    protected List<Record> recs;
    protected RndParsingResult result;
    protected int jobId;

    protected IRepository rps;

    @EJB(beanName = "DbStorage")
    IDbStorage dbStorage;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "ActivityLogService")
    private IActivityLogService logService;

    @EJB(beanName = "RenderingService")
    private IRenderingService rndService;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService dlvService;

    @EJB(beanName = "RecordCache")
    private IRecordCache cache;

    private String theLogUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

    @Deprecated
    public void onMessage(Message message) {
        log.trace("onMessage starts");
        DeliveryFileVO deliveryFileVO = null;
        try {
            if (!(message instanceof TextMessage)) {
                throw new JMSException("TextMessage expected!");
            }
            String text = ((TextMessage) message).getText();
            int ind = text.indexOf(":");
            jobId = Integer.decode(text.substring(0, ind));
            renderingResult = text.substring(ind + 1);
            log.debug("Accept rendering issue id=" + jobId);

            parse();
            recs = result.getRecords();
            if (recs == null) {
                logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RND_FAILED,
                    -1, UNKNOWN_NAME, theLogUser, JOB_ID + "=" + jobId);
                log.debug("In parsing results not found records");
                return;
            }

            deliveryFileVO = rs.getDeliveryFileVO(result.getIssueId(), result.getDbName(), recs.get(0).getName());

            correctRecordPaths(deliveryFileVO);

            loadContent(false);

            updateRecords();
            updateSuccessOfRecsAndIds();

            try {
                rndService.setDeliveryFileStatusAfterRnd(deliveryFileVO.getId(), result.getPlan());
                tryChangeDFsModifyStatus(deliveryFileVO);
            } catch (DeliveryPackageException e) {
                log.error(e, e);
            }

            notifyOfEvent(deliveryFileVO);
            deliveryFileVO = rs.getDeliveryFileVO(deliveryFileVO.getId());

            convertTo3g(deliveryFileVO);

            finishing(deliveryFileVO);
            processFailedRecords(deliveryFileVO, result.getPlan());
        } catch (Exception e) {
            exceptionToLog(e, deliveryFileVO, jobId);
        }
    }

    private void correctRecordPaths(DeliveryFileVO deliveryFileVO) throws Exception {
        List<Object[]> recordPaths = rs.getRecordsByDeliveryFile(deliveryFileVO.getId());
        Map<String, String> pathByName = new HashMap<String, String>();
        for (Object[] pathInfo : recordPaths) {
            pathByName.put((String) pathInfo[0], (String) pathInfo[1]);
        }
        for (Record r : recs) {
            if (r.getRecordSourceUri().contains("abstracts-tmp")) {
                rps.deleteFile(r.getRecordSourceUri());
                if (RepositoryUtils.getRealFile(r.getRecordSourceUri().replace(FilePathCreator.XML_EXT, "")).exists()) {
                    rps.deleteDir(r.getRecordSourceUri().replace(FilePathCreator.XML_EXT, ""));
                }
                r.setRecordSourceUri(pathByName.get(r.getName()));
            }
        }
    }

    private void updateSuccessOfRecsAndIds() {
        for (Record rec : recs) {

            RecordEntity re = rs.getRecord(result.getIssueId(), result.getDbName(), rec.getName());
            rec.setId(re.getId());
            rec.setSuccessful(re.isRenderingSuccessful());
        }
    }

    private void tryChangeDFsModifyStatus(DeliveryFileVO deliveryFileVO) throws DeliveryPackageException {

        int modifyStatusId = deliveryFileVO.getModifyStatus();
        if (modifyStatusId == IDeliveryFileStatus.STATUS_MAKE_PERM_STARTED
            || modifyStatusId == IDeliveryFileStatus.STATUS_EDITING_STARTED) {

            int dbId = rs.findDb(result.getIssueId(), result.getDbName());
            int firstRecordId = rs.findRecord(dbId, recs.get(0).getName());

            rndService.setDeliveryFileModifyStatusAfterRnd(deliveryFileVO.getId(), firstRecordId);
        }
    }

    private void finishing(DeliveryFileVO deliveryFileVO) throws Exception {
        clearJobResult();
        if (result.getDbName().contains(CochraneCMSPropertyNames.getCDSRDbName())) {
            fillPdfSize(deliveryFileVO.getId());
        }

        dbStorage.updateRenderedRecordCount(deliveryFileVO.getDbId());
        dlvService.finishUpload(result.getIssueId(), deliveryFileVO, result.getDbName(), recs, jobId);
    }

    private String getRecNamesString() {
        StringBuilder recList = new StringBuilder();
        for (Record rec : recs) {
            recList.append("'")
                .append(rec.getName())
                .append("',");
        }
        recList.delete(recList.length() - 1, recList.length());
        return recList.toString();
    }

    private void fillPdfSize(Integer dfId) {
        if (result.getPlan().equals(RenderingPlan.CD)) {
            return;
        }
        try {
            PdfSizeWriter.writePdfSize(dfId, getRecNamesString());
        } catch (Exception e) {
            log.error(e, e);
        }
    }

    protected void parse() throws IOException, SAXException, ParserConfigurationException {
        rps = RepositoryFactory.getRepository();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);

        SAXParser parser = factory.newSAXParser();
        result = new RndParsingResult();
        parser.parse(new ByteArrayInputStream(renderingResult.getBytes()),
            new RndResultHandler(jobId, result));
        renderingResult = null;
    }

    protected void processFailedRecords(DeliveryFileVO df, RenderingPlan plan)
        throws NamingException, URISyntaxException, IOException {

        if (result.getBadCount() == 0) {
            return;
        }
        sendResupply(df, createRecordNamesList(false), plan);
    }

    private List<String> createRecordNamesList(boolean b) {
        List<String> list = new ArrayList<String>();
        for (Record rec : recs) {
            if (rec.isSuccessful() == b) {
                list.add(rec.getName());
            }
        }
        return list;
    }

    private void sendResupply(DeliveryFileVO df, List<String> failedRecList, RenderingPlan plan) {

        StringBuilder resupply = new StringBuilder();
        resupply.append("Data base ").append(result.getDbName())
            .append(" , plan ").append(plan)
            .append(". The following records failed to render:\n");
        for (String recordName : failedRecList) {
            resupply.append(recordName).append("\n");
        }

        MessageSender.sendResupply(jobId, df.getName(), resupply.toString());
        log.debug(resupply.toString());
    }

    protected void clearJobResult() throws MalformedURLException, NamingException, ServiceException {

        if (!result.isCompleted()) {
            return;
        }
        IProvideRendering ws = null;
        try {
            ws = WebServiceUtils.getProvideRendering();
            ws.clearJobResult(jobId);
        } finally {
            WebServiceUtils.releaseServiceProxy(ws, IProvideRendering.class);
        }
        rs.deleteStartedJobId(jobId);
    }

    private void exceptionToLog(Exception e, DeliveryFileVO deliveryFileVO, int jobId) {
        log.error(e, e);
        int id = -1;
        String name = UNKNOWN_NAME;
        if (deliveryFileVO != null) {
            id = deliveryFileVO.getId();
            name = deliveryFileVO.getName();
        }
        logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.ACCEPT_RND_RESULTS_FAILED,
            id, name, theLogUser, JOB_ID + "=" + jobId);
    }

    private void notifyOfEvent(DeliveryFileVO deliveryFileVO) {
        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RND_COMPLETED,
            deliveryFileVO.getId(), deliveryFileVO.getName(), theLogUser,
            JOB_ID + "=" + jobId + ",plan=" + result.getPlan().planName
                + ",success=" + result.isSuccessful()
                + ",completed=" + result.isCompleted());

        MessageSender.sendRenderReport(jobId, deliveryFileVO.getName(), PLAN + "= " + result.getPlan().planName
            + ", success=" + result.isSuccessful() + ", completed=" + result.isCompleted());
    }

    protected void loadContent(boolean cdsr) throws IOException {

        if (CollectionUtils.isEmpty(recs)) {
            return;
        }
        log.debug("loadContent start size=" + recs.size() + ", " + PLAN + "= " + result.getPlan());

        boolean isSameServer = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RENDERRING_SAMESERVER)
            .equals("true");
        String rndRepository = null;
        if (isSameServer) {
            rndRepository = CochraneCMSPropertyNames.getRenderingRepository();
        }
        String webUrl = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RENDER_WEB_URL);
        String repository = CochraneCMSProperties.getProperty("filesystem.root",
            System.getProperty("jboss.server.config.url") + "/repository/");

        final String prefix = "file:///";
        if (!repository.startsWith(prefix)) {
            repository = prefix + repository;
        }

        String dbName = result.getDbName();
        String issue = String.valueOf(result.getIssueId());
        RenderingPlan plan = result.getPlan();
        String basePath = repository + FilePathCreator.SEPARATOR + getBaseDirPath(issue, dbName, plan);

        for (Record record : recs) {

            String recName = record.getName();
            if (cdsr && !checkRecordOnProcessed(recName, dbName, basePath)) {

                record.setConverted(false);
                //cache.updateRecordState(record.getName(), false, true, false, false, false, false);
                continue;
            }

            //deleteOldDirectory(repository + FilePathCreator.SEPARATOR + getDirPath(issue, dbName, record, plan));
            if (!record.isSuccessful()) {
                //cache.updateRecordState(record.getName(), false, true, false, false, false, false);
                continue;
            }
            //cache.updateRecordState(record.getName(), false, true, false, false, false, true);
            loadRecord(record, issue, dbName, isSameServer, repository, webUrl, rndRepository);
        }
        log.debug("loadContent finish size=" + recs.size() + " " + PLAN + "= " + plan);
    }

    protected void loadRecord(Record record, String issue, String dbName, boolean isSameServer, String repository,
        String webUrl, String rndRepository) throws IOException {

        RenderingPlan plan = result.getPlan();

        FileUtils.deleteDirectory(repository + FilePathCreator.SEPARATOR + getDirPath(issue, dbName, record, plan));

        for (String url : record.getFilesList()) {

            String newFilePath = getFilePath(issue, dbName, record, plan, url);
            putFile(newFilePath, isSameServer, url, webUrl, rndRepository);
        }
    }

    protected boolean checkRecordOnProcessed(String recName, String dbName, String basePath) {

        if (cache.containsRecord(recName)) {
            return false;
        }

        boolean ret = true;
        ExternalProcess pp = cache.getSingleProcess(dbName, ILogEvent.RND_STARTED);
        if (pp == null) {
            log.warn("cannot find pooled entire rendering process - " + dbName);
        } else {

            String filePath = FilePathCreator.addRecordNameToPath(basePath, recName, dbName);
            File fl = new File(filePath);
            ret = !fl.exists() || fl.lastModified() < pp.getStartDate().getTime();
        }
        return ret;
    }

    protected String getDirPath(String s, String dbName, Record record, RenderingPlan plan) {
        return FilePathCreator.getRenderedDirPath(s, dbName, record.getName(), plan);
    }

    protected String getFilePath(String s, String dbName, Record record, RenderingPlan plan, String url) {
        return FilePathCreator.getRenderedFilePath(s, dbName, record.getName(), plan, url);
    }

    protected String getBaseDirPath(String issue, String dbName, RenderingPlan plan) {
        return FilePathCreator.getRenderedDirPath(issue, dbName, plan);
    }

    protected void putFile(String newFilePath, boolean isSameServer, String url, String webUrl, String rndRepository)
        throws IOException {

        InputStream bis = null;
        try {
            bis = new BufferedInputStream(isSameServer
                ? new FileInputStream(RepositoryUtils.getRealFile(rndRepository, url))
                : new URL(webUrl + "DataTransferer?file=" + url).openStream());
            rps.putFile(newFilePath, bis);
        } finally {
            if (bis != null) {
                bis.close();
            }
        }
    }

    private void convertTo3g(DeliveryFileVO df) {

        if (CollectionUtils.isNotEmpty(recs)
                && CmsUtils.isConversionTo3gAvailable(result.getDbName())) {
            List<Integer> recIds = new ArrayList<Integer>();

            LOCK.lock();
            {
                for (Record rec : recs) {
                    if (rec.isSuccessful() && !cache.containsOnConversionRecordId(rec.getId())) {
                        recIds.add(rec.getId());
                    }
                }
                cache.addOnConversionRecordIds(recIds);
            }
            LOCK.unlock();

            if (!recIds.isEmpty()) {
                IssueVO issueVO = new IssueVO(rs.getIssue(result.getIssueId()));
                new Wml3gConverterIssueDb(issueVO, df.getDbId(), result.getDbName(), recIds).execute();

                cache.removeOnConversionRecordIds(recIds);
            }
        }
    }

    @Deprecated
    private void updateRecords() {
        if (CollectionUtils.isEmpty(recs)) {
            return;
        }
        log.debug("updateRecordsAfterRnd start size=" + recs.size() + " " + PLAN + "= " + result.getPlan());

        int dbId = rs.findDb(result.getIssueId(), result.getDbName());
        int numberRndPlans = getNumberRndPlans();

        int planId = rndService.getPlanId(result.getPlan().planName);

        int i = 0;
        int recsSize = recs.size();
        while (i < recsSize) {
            int end1 = (recsSize - i) > PACK_SIZE ? PACK_SIZE : recsSize - i;
            if (end1 <= 0) {
                break;
            }
            StringBuilder successRecords = new StringBuilder();
            StringBuilder failedRecords = new StringBuilder();

            int successNumber = 0;
            int failedNumber = 0;
            for (int j = 0; j < end1; j++) {
                if (writeCondition(successRecords, failedRecords, recs.get(i + j))) {
                    successNumber++;
                } else {
                    failedNumber++;
                }
            }
            i = i + end1;

            if (successNumber != 0) {
                updatePart(dbId, successRecords.toString(), planId, true, numberRndPlans, successNumber);
            }
            if (failedNumber != 0) {
                updatePart(dbId, failedRecords.toString(), planId, false, numberRndPlans, failedNumber);
            }
        }
        log.debug("updateRecordsAfterRnd finish " + PLAN + result.getPlan());
    }

    private int getNumberRndPlans() {
        if (result.getDbName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))
            || result.getDbName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLMETHREV))
            || result.getDbName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL))) {
            return 2;
        } else {
            return 1;
        }
    }

    @Deprecated
    private void updatePart(int dbId, String recs, int plan, boolean b, int numberRndPlans, int number) {

        int count = 0;
        int i = 0;
        while (count == 0 && i < ATTEMPT_NUMBER) {
            try {
                if (i > 0) {
                    delay();
                    log.debug("updateRenderings, attempt " + i);
                    log.debug(Thread.currentThread().getName());
                }
                count = rndService.updateRenderings(dbId, recs, plan, b);
                i++;

            } catch (EJBException e) {
                log.debug(e.getMessage());
                i++;
            }
        }
        if (count == 0) {
            return;
        }
        for (int j = 0; j < ATTEMPT_NUMBER; j++) {
            try {
                rndService.setRecordCompleted(dbId, recs, b, numberRndPlans, number);
                break;
            } catch (EJBException e) {
                log.debug("setRecordCompleted, attempt " + j);
                log.debug(e.getMessage());
                delay();
            }
        }
    }

    private boolean writeCondition(StringBuilder successRecords, StringBuilder failedRecords, Record record) {
        boolean isSuccess = false;
        if (record.isSuccessful()) {
            if (successRecords.length() > 0) {
                successRecords.append(",");
            }
            successRecords.append("'").append(record.getName()).append("'");
            isSuccess = true;
        } else {
            if (failedRecords.length() > 0) {
                failedRecords.append(",");
            }
            failedRecords.append("'").append(record.getName()).append("'");
        }
        return isSuccess;
    }

    private void delay() {
        String threadName = Thread.currentThread().getName();
        long delay;
        try {
            delay = Integer.valueOf(threadName.substring(threadName.length() - 1)) - 1;
        } catch (NumberFormatException e) {
            delay = 0;
        }
        delay = delay * DELAY_BECAUSE_OF_LOCK;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.debug(e.getMessage());
        }
    }
}