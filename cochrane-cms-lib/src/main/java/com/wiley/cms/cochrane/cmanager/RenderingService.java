package com.wiley.cms.cochrane.cmanager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;

import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.hibernate.exception.LockAcquisitionException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DelayedThread;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.RndNextJobEntity;
import com.wiley.cms.cochrane.cmanager.data.StatusEntity;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IIssueStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.IRenderingStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IProvideRendering;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 10.11.2011
 */
@Stateless
@Local(IRenderingService.class)
public class RenderingService implements IRenderingService, java.io.Serializable {
    //static final int PLAN_QAS_ID = 0;

    private static final Logger LOG = Logger.getLogger(RenderingService.class);

    //private static final int MAX_RECORDS_NUMBER = 200000;
    private static final int PERCENT = 100;
    //private static final int COUNT_RND = 1;
    //private static final int COUNT_RND_SYSREV = 2;

    //private static final int XML_STRING_LENGTH = 130;
    private static final int THREE = 3;

    private static final String HTML_DIAMOND = "html_diamond";
    private static final String RND_PLAN = "rndPlan";

    private static final String CENTRAL_RECORDS_NOT_FOUND_MSG = "central records not found";
    private static final String RECORDS_FOUND_MSG = "records found ";
    private static final String COMPLETED_RECORDS_MSG = "completed records=";
    private static final String MESSAGE = "message";
    private static final String JOB_ID = "jobId";
    private static final String PLAN = "plan";
    //private static final String ISSUE_YEAR = "issueYear=";

    //private static final String ISSUE_NUMBER_PREFIX = "issueNumber=";
    //private static final String ISSUE_NUM_ONE = ISSUE_NUMBER_PREFIX + "1";
    //private static final String ISSUE_NUM_TWO = ISSUE_NUMBER_PREFIX + "2";
    //private static final String ISSUE_NUM_THREE = ISSUE_NUMBER_PREFIX + "3";
    //private static final String ISSUE_NUM_FOUR = ISSUE_NUMBER_PREFIX + "4";

    @EJB(beanName = "DbStorage")
    IDbStorage dbStorage;

    @EJB(beanName = "IssueStorage")
    IIssueStorage issueStorage;

    @EJB(beanName = "RecordStorage")
    IRecordStorage recordStorage;

    @EJB(beanName = "RenderingStorage")
    IRenderingStorage renderingStorage;

    @EJB(beanName = "ResultsStorage")
    IResultsStorage resultsStorage;

    @PersistenceContext
    private EntityManager manager;

    @EJB(beanName = "TranslatedAbstractsInserter")
    private ITranslatedAbstractsInserter taInserter;

    //@EJB(beanName = "RecordCache")
    //private IRecordCache rcache;

    private HashMap<String, Integer> planNameToId;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void deleteRendering(Collection<Integer> ids) {
        renderingStorage.deleteRecordsByIds(ids);
    }

    @Deprecated
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean startRendering(int deliveryFileId, String goodRecList) throws Exception {
        DeliveryFileEntity dfEntity = manager.find(DeliveryFileEntity.class, deliveryFileId);
        String deliveryFileName = dfEntity.getName();
        String message = "Delivery file name: " + deliveryFileName;

        boolean isPdfCreate = isPdfCreate(dfEntity.getDb().getTitle()) && !dfEntity.isPdfCompleted();
        int countRnd = calcCountRnd(dfEntity, isPdfCreate);
        if (countRnd < 1) {
            LOG.debug("countRnd=0");
            return false;
        }

        dfEntity.setCdCompleted(true); // don't perform CD rendering !!!!!
        RenderingPlanEntity[] planEntities = renderingStorage.getPlanEntities(isPdfCreate, !dfEntity.isHtmlCompleted(),
            !dfEntity.isCdCompleted(), countRnd);

        boolean isCentral = deliveryFileName.contains(
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL));

        deleteExistingRendering(dfEntity, goodRecList, planEntities, true);

        boolean isSuccessful;

        if (isCentral && goodRecList == null) {
            //if central start for re-rendering as whole df package, it needs to read it in parts
            isSuccessful = startForCentral(dfEntity, message, isPdfCreate, !dfEntity.isHtmlCompleted(),
                !dfEntity.isCdCompleted(), isCentral, planEntities);
        } else {
            Object[] urisAndInfoAboutRawData = prepareUrisAndRendering(dfEntity, planEntities,
                goodRecList, isCentral);
            isSuccessful = startRendering(urisAndInfoAboutRawData, dfEntity, message,
                isPdfCreate, !dfEntity.isHtmlCompleted(), !dfEntity.isCdCompleted(), isCentral);
        }
        return isSuccessful;
    }

    private boolean isPdfCreate(String dbName) {
        boolean isPdfCreate = false;
        if (dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))
                || dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLMETHREV))
                || dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL))) {
            isPdfCreate = true;
        }
        return isPdfCreate;
    }

    private int calcCountRnd(DeliveryFileEntity dfEntity, boolean isPdfCreate) {
        int completedRenderings = 0;
        if (dfEntity.isHtmlCompleted()) {
            completedRenderings++;
        }
//        if (dfEntity.isCdCompleted())
//        {
//            completedRenderings++;
//        }
        int countRnd;
        if (isPdfCreate) {
            countRnd = 2 - completedRenderings;
        } else {
            countRnd = 1 - completedRenderings;
        }

        return countRnd;
    }

    private boolean startForCentral(DeliveryFileEntity dfEntity, String message,
                                    boolean isPdfCreate, boolean isHtmlCreate, boolean isCdCreate,
                                    boolean isCentral, RenderingPlanEntity[] planEntities) throws Exception {
        boolean isSuccessful = false;
        int start = 0;
        int pieceSize = CochraneCMSPropertyNames.getCentralRerenderingPartSize();

        List<RecordEntity> records = recordStorage.getRecordsByDFile(dfEntity, true);
        if (records == null || records.size() == 0) {
            LOG.debug(CENTRAL_RECORDS_NOT_FOUND_MSG);
            return false;
        }
        int recordsSize = records.size();

        try {
            for (; start < recordsSize; start += pieceSize) {
                List<RecordEntity> subRecords = records.subList(start,
                        recordsSize - start >= pieceSize ? (start + pieceSize) : recordsSize);
                LOG.debug(RECORDS_FOUND_MSG + subRecords.size());

                Object[] urisAndInfoAboutRawData = createArrays(subRecords, planEntities, dfEntity, isCentral);
                isSuccessful = startRendering(urisAndInfoAboutRawData, dfEntity, message,
                        isPdfCreate, isHtmlCreate, isCdCreate, isCentral);
                if (!isSuccessful) {
                    break;
                }
            }
        } catch (NoResultException e) {
            LOG.debug(CENTRAL_RECORDS_NOT_FOUND_MSG + e.getMessage());
        }

        return isSuccessful;
    }

    @Deprecated
    public boolean startRendering(Object[] urisAndRaw, String dbName, int dfId,
                                  String message, RenderingPlan plan) {

        if (dbName.equals("")) {
            LOG.error("Coudn't start rendering because dbName is empty ");
            return false;
        }
        DeliveryFileEntity dfEntity = null;
        if (dfId != -1) {
            dfEntity = manager.find(DeliveryFileEntity.class, dfId);
        }

        boolean isPdfCreate = false;
        boolean isHtml = false;
        boolean isCd = false;
        if (plan != null) {
            if (plan.equals(RenderingPlan.PDF_TEX)) {
                isPdfCreate = true;
            } else if (plan.equals(RenderingPlan.HTML)) {
                isHtml = true;
            } else if (plan.equals(RenderingPlan.CD)) {
                isCd = false; // don't perform CD rendering !!!
            }
        } else {
            isPdfCreate = isPdfCreate(dbName);
            isHtml = true;
            isCd = false;     // don't perform CD rendering !!!!!
        }
        boolean isCentral = dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL));
        return startRendering(urisAndRaw, dfEntity, message,
            isPdfCreate, isHtml, isCd, isCentral);
    }

    private boolean startRendering(Object[] urisAndRaw, DeliveryFileEntity dfEntity, String message,
                                   boolean isPdfCreate, boolean isHtml, boolean isCd,
                                   boolean isCentral) {

        if (urisAndRaw == null || urisAndRaw.length == 0) {
            LOG.error("Not formed list of uri for rendering ");
            return false;
        }
        if (isPdfCreate) {
            renderForDeliveryFile(RenderingPlan.PDF_TEX,
                (URI[]) urisAndRaw[0], (boolean[]) urisAndRaw[1], dfEntity, message, isCentral);
        }
        if (isHtml) {
            renderForDeliveryFile(RenderingPlan.HTML, (URI[]) (urisAndRaw[2] != null ? urisAndRaw[2] : urisAndRaw[0]),
                (boolean[]) urisAndRaw[1], dfEntity, message, isCentral);
        }
        if (isCd) {
            renderForDeliveryFile(RenderingPlan.CD,
                (URI[]) urisAndRaw[0], (boolean[]) urisAndRaw[1], dfEntity, message, isCentral);
        }
        if (dfEntity != null) {
            resultsStorage.setDeliveryFileStatus(dfEntity.getId(), IDeliveryFileStatus.STATUS_RENDERING_STARTED, true);
            /*dfEntity.setInterimStatus(manager.find(StatusEntity.class,
                IDeliveryFileStatus.STATUS_RENDERING_STARTED));*/
        }
        return true;
    }

    @Deprecated
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setRecordCompleted(int dbId, String recordNames, boolean success, int numberRndPlans, int number) {
        LOG.debug("setRecordCompleted started");
        ClDbEntity dbEntity = manager.find(ClDbEntity.class, dbId);
        long done = 0;
        synchronized (RenderingService.class) {
            done = recordStorage.getRecordsCountByClDbAndNames(recordNames, numberRndPlans, dbEntity);
        }
        if (done != number) {
            LOG.info(COMPLETED_RECORDS_MSG + done + " should be=" + number);
            return 0;
        }

        int doneUpdated = recordStorage.setCompletedAndDisableForRecords(recordNames, numberRndPlans, dbEntity);
        if (doneUpdated != done) {
            throw new EJBException(
                new PersistenceException(new LockAcquisitionException("doneUpdated=" + doneUpdated, null)));
        }

        int done1 = 0;
        if (doneUpdated > 0 && success) {
            done1 = recordStorage.setSuccessForRecords(recordNames, numberRndPlans, dbEntity, true);
        }
        int done2 = 0;
        if (doneUpdated > 0 && done1 != doneUpdated) {
            done2 = recordStorage.setSuccessForRecords(recordNames, numberRndPlans, dbEntity, false);
        }
        LOG.info(COMPLETED_RECORDS_MSG + done + " successful=" + done1 + " not successful=" + done2);
        return doneUpdated;
    }


    private void logRndStarted(DeliveryFileEntity dfEntity, int jobId, RenderingPlan plan, String message) {
        if (dfEntity == null) {
            return;
        }
        IActivityLogService logService;
        try {
            logService = AbstractManager.getActivityLogService();
            logService.info(ActivityLogEntity.EntityLevel.FILE,
                ILogEvent.RND_STARTED, dfEntity.getId(), dfEntity.getName(),
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME),
                "job id=" + jobId + ", plan=" + plan.planName + " " + message);
        } catch (Exception e) {
            LOG.error(e);
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put(MESSAGE, message);
        map.put(JOB_ID, Integer.toString(jobId));
        map.put(PLAN, plan.planName);

        MessageSender.sendMessage("render_job_created", map);
    }

    public int renderForDeliveryFile(RenderingPlan plan, URI[] uris, boolean[] raw,
                                     DeliveryFileEntity dfEntity, String message, boolean isCentral) {
        int jobId;
        String[] requestParameters = null;

        if (dfEntity != null) { // delivery file is null in record saving/modifying process
            requestParameters = addIssueAndYearParams(dfEntity, isCentral);
        }

        if (isCentral) {
            return renderForDeliveryFileCentral(plan, dfEntity, message, uris, requestParameters);
        }

        int priority = IProcessManager.USUAL_PRIORITY;
        if (dfEntity == null) {
            priority = IProcessManager.LOW_PRIORITY;
        } else if (DeliveryFileEntity.isRevman(dfEntity.getType())) {

            String packName = dfEntity.getName();
            if (DeliveryPackage.isArchieAut(packName)) {
                priority = IProcessManager.HIGHEST_PRIORITY;
            } else {
                priority = IProcessManager.HIGH_PRIORITY;
            }
        }

        jobId = startOneJob(plan, uris, raw, dfEntity, message, requestParameters, priority);
        renderingStorage.createStartedJob(jobId, plan, dfEntity);

        return jobId;
    }

    private int renderForDeliveryFileCentral(RenderingPlan plan, DeliveryFileEntity dfEntity,
                                             String message, URI[] uris, String[] requestParameters) {
        int jobId = -1;
        for (URI uri : uris) {
            jobId = startOneJob(plan, new URI[]{uri}, null, dfEntity,
                message, requestParameters, IProcessManager.USUAL_PRIORITY);
            renderingStorage.createStartedJob(jobId, plan, dfEntity);
//            createStartedJob(jobId, plan, dfEntity);
        }
        return jobId;
    }

    private String[] addIssueAndYearParams(DeliveryFileEntity dfEntity, boolean isCentral) {
        String[] result = null;
        if (isCentral || (dfEntity != null && dfEntity.getName()
            .contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR)))) {
            IssueEntity issue = dfEntity.getIssue();
            result = CmsUtils.buildIssueParamString(issue.getYear(), issue.getNumber(), isCentral);
        }
        return result;
    }

    /*private String[] getParametersString(IssueEntity issue, boolean isCentral) {
        if (isCentral) {
            return new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUMBER_PREFIX + issue.getNumber()};
        }

        String[] result;
        switch (issue.getNumber()) {
            case DECEMBER:
                result = new String[]{ISSUE_YEAR + issue.getYear() + 1, ISSUE_NUM_ONE};
                break;
            case JANUARY:
                result = new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUM_ONE};
                break;
            case MARCH:
            case APRIL:
                result = new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUM_TWO};
                break;
            case JUNE:
            case JULY:
                result = new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUM_THREE};
                break;
            case SEPTEMBER:
            case OCTOBER:
                result = new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUM_FOUR};
                break;
            default:
                result = new String[]{ISSUE_YEAR + issue.getYear(), ISSUE_NUMBER_PREFIX + issue.getNumber()};
        }
        return result;
    }*/

    public int startOneJob(RenderingPlan plan, URI[] uris, boolean[] isRawDataExists,
                           DeliveryFileEntity dfEntity, String message,
                           String[] requestParameters, int priority) {
        int jobId = -1;
        IProvideRendering ws = null;
        try {
            URI callback = getAcceptRenderingResultsCallback();
            ws = WebServiceUtils.getProvideRendering();
            jobId = ws.render("cochrane", plan.planName, uris, isRawDataExists, callback, requestParameters,
                    priority);
        } catch (Exception ex) {
            LOG.error(ex, ex);
            return jobId;
        } finally {
            WebServiceUtils.releaseServiceProxy(ws, IProvideRendering.class);
        }
//        LOG.debug("Rendering job id: " + jobId);
        logRndStarted(dfEntity, jobId, plan, message);
        return jobId;
    }

    public void setAboutResources(int dbId) {
        DbVO dbVO = dbStorage.getDbVO(dbId);
        IssueVO issue = issueStorage.getIssueVO(dbVO.getIssueId());
        String issueName = issue.getYear() + "_Issue_" + issue.getNumber();
        List<RecordVO> records = recordStorage.getDbRecordVOList(dbId);
        List<URI> uriList = new ArrayList<URI>();
        for (RecordVO record : records) {
            try {
                uriList.add(FilePathCreator.getUri(record.getRecordPath()));
            } catch (URISyntaxException e) {
                LOG.debug(e, e);
            }
        }
        if (uriList.size() > 0) {
            URI[] uris = new URI[uriList.size()];
            uriList.toArray(uris);

            IProvideRendering ws = null;
            try {
                URI callback = getAcceptRenderingResultsCallback();
                ws = WebServiceUtils.getProvideRendering();
                ws.setAboutResources(uris, issueName, callback);

                LOG.debug("Request to setup About db to Rendering service sended.");
            } catch (Exception e) {
                LOG.error(e, e);
            } finally {
                WebServiceUtils.releaseServiceProxy(ws, IProvideRendering.class);
            }
        }
    }

    public Object[] prepareUrisAndRendering(DeliveryFileEntity dfEntity, RenderingPlanEntity[] planEntities,
        String goodRecNames, boolean isCentral) throws Exception {

        List<RecordEntity> records;
        if (goodRecNames != null) {
            records = recordStorage.getRecordsByDFile(goodRecNames, dfEntity);
        } else {
            records = recordStorage.getRecordsByDFile(dfEntity, true);
        }
        if (records == null || records.size() == 0) {
            LOG.debug("not found entities for df: " + dfEntity.getName() + "  " + goodRecNames);
            return null;
        }

        LOG.debug(RECORDS_FOUND_MSG + records.size());

        return createArrays(records, planEntities, dfEntity, isCentral);
    }


    /*private RenderingPlanEntity[] getPlanEntities(boolean pdfCreate, boolean htmlCreate, boolean cdCreate,
                                                  int countRnd) {
        RenderingPlanEntity[] planEntitys;
        planEntitys = new RenderingPlanEntity[countRnd];
        int aNumber = 0;
        if (pdfCreate) {
            planEntitys[aNumber++] = getPlanEntity("pdf_tex");
        }

        if (htmlCreate) {
            planEntitys[aNumber++] = getPlanEntity(HTML_DIAMOND);

        return planEntitys;
    }*/

    private RenderingPlanEntity getPlanEntity(String plan) {
        return (RenderingPlanEntity) manager
            .createNamedQuery(RND_PLAN)
            .setParameter("d", plan).getSingleResult();
    }

    @Deprecated
    public int getPlanId(String plan) {
        return ((RenderingPlanEntity) manager
            .createNamedQuery(RND_PLAN).setParameter("d", plan).getSingleResult()).getId();
    }

    private void deleteExistingRendering(DeliveryFileEntity dfEntity, String goodRecNames,
                                         RenderingPlanEntity[] planEntitys, boolean useNewQa) {
        Long count;
        StringBuilder plans = new StringBuilder();
        plans.append(" and (");
        for (int i = 0; i < planEntitys.length; i++) {
            if (i > 0) {
                plans.append(" or ");
            }
            plans.append(" r.renderingPlan.id=").append(planEntitys[i].getId());
        }
        plans.append(")");
        if (goodRecNames != null && !useNewQa) {
            LOG.debug("goodRecNames not null");
            count = renderingStorage.getRecordsCountByDFileAndPlan(goodRecNames, plans.toString(), dfEntity);

            if (count != 0) {
                renderingStorage.deleteRecordsByDFileAndPlan(goodRecNames, plans.toString(), dfEntity);
            }
        } else if (goodRecNames == null) {
//            count = (Long) manager
//                    .createNamedQuery("findAllRendering")
//                    .setParameter("df", dfEntity)
//                    .getSingleResult();
            count = renderingStorage.getRecordsCountByDFileAndPlan(plans.toString(), dfEntity);

            if (count != 0) {
//                manager.createNamedQuery("deleteRenderingbyDf")
//                        .setParameter("df", dfEntity)
//                        .executeUpdate();
                renderingStorage.deleteRecordsByDFileAndPlan(plans.toString(), dfEntity);
            }
        }
    }

    private Object[] createArrays(List<RecordEntity> records, RenderingPlanEntity[] planEntities,
                                  DeliveryFileEntity dfEntity, boolean central) throws Exception {
        URI[] uris;
        boolean[] rawData = null;
        URI[] htmlRendering = null;

        if (DeliveryFileEntity.isRevman(dfEntity.getType()) || dfEntity.isMethReview()) {

            for (RenderingPlanEntity rpe : planEntities) {
                if (HTML_DIAMOND.equals(rpe.getDescription())) {
                    htmlRendering = createUrisAndRndForHtml(dfEntity.getDb().getTitle(), records);
                    break;
                }
            }

            rawData = createArrayIsRawDataExists(records);
        }

        uris = central ? createUrisAndRndForCentral(records, planEntities, dfEntity)
            : createUrisAndRnd(records, planEntities, dfEntity);

        return new Object[]{uris, rawData, htmlRendering};
    }

    private URI[] createUrisAndRndForHtml(String dbName, List<RecordEntity> records) {

        List<URI> uris = new ArrayList<>();

        StringBuilder recNames = new StringBuilder();
        boolean ta = BaseType.find(dbName).get().hasTranslationHtml();

        for (RecordEntity record : records) {
            if (record.isQasSuccessful()) {
                try {
                    DeliveryFileEntity df = record.getDeliveryFile();
                    String path = ta
                        ? taInserter.getSourceForRecordWithInsertedAbstracts(record, record.getRecordPath(),
                            df.getIssue().getId(), df.getId(), BaseType.find(dbName).get().getTranslationModeHtml())
                                : record.getRecordPath();
                    uris.add(FilePathCreator.getUri(path));

                } catch (Exception e) {
                    LOG.error(e, e);
                    record.setQasSuccessful(false);
                    record.setRenderingSuccessful(false);
                    manager.merge(record);
                    manager.flush();
                }
                recNames.append('\'').append(record.getName()).append('\'').append(',');
            }
        }
        if (recNames.length() > 0) {
            // delete last comma
            recNames.deleteCharAt(recNames.length() - 1);
        }

        URI[] ret = new URI[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            ret[i] = uris.get(i);
        }
        return ret;
    }

    private boolean[] createArrayIsRawDataExists(List<RecordEntity> records) {
        List<Boolean> rawData = new ArrayList<Boolean>();
        for (RecordEntity record : records) {
            if (record.isQasSuccessful()) {
                rawData.add(record.isRawDataExists());
            }
        }
        boolean[] rd = new boolean[rawData.size()];
        for (int i = 0; i < rawData.size(); i++) {
            rd[i] = rawData.get(i);
        }
        return rd;
    }

    private URI[] createUrisAndRndForCentral(List<RecordEntity> records, RenderingPlanEntity[] planEntities,
                                             DeliveryFileEntity dfEntity) throws IOException, URISyntaxException {

        String timestamp = String.valueOf(System.currentTimeMillis());
        URI[] uris = new BatchRenderer<RecordEntity>(records, timestamp, planEntities, dfEntity) {

            private boolean repeat = false;

            public String getSourceFilePath(RecordEntity entity) throws URISyntaxException {
                return FilePathCreator.getUri(entity.getRecordPath()).toString();
            }

            public void createRendering(String recordNames) {
                try {
                    renderingStorage.createRenderingRecords(recordNames, this.getPlanEntities(), this.getDfEntity());
                } catch (Exception e) {
                    if (repeat) {
                        LOG.error(e);
                    }  else {
                        repeat = true;
                        LOG.warn("createRendering failed: " + e.getMessage());
                        LOG.warn("trying repeat ...");
                        createRendering(recordNames);
                    }
                }
            }

            public int getIssueId() {
                return this.getRecords().get(0).getDb().getIssue().getId();
            }

            public String getDbTitle() {
                return this.getRecords().get(0).getDb().getTitle();
            }
        }.createUrisForCentral();

        return uris;
    }

    private URI[] createUrisAndRnd(List<RecordEntity> records, RenderingPlanEntity[] planEntitys,
                                   DeliveryFileEntity dfEntity) throws URISyntaxException {
        //URI[] uris = new URI[records.size()];
        List<URI> uris = new ArrayList<URI>();
        StringBuilder recNames = new StringBuilder();
        for (RecordEntity record : records) {
            if (record.isQasSuccessful()) {
                uris.add(FilePathCreator.getUri(record.getRecordPath()));
                recNames.append('\'').append(record.getName()).append('\'').append(',');
                //recs.add(record);
            }
        }
        if (recNames.length() > 0) {
            // delete last comma
            recNames.deleteCharAt(recNames.length() - 1);
        }

        renderingStorage.createRenderingRecords(recNames.toString(), planEntitys, dfEntity);

        URI[] ret = new URI[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            ret[i] = uris.get(i);
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int updateRenderings(int dbId, String condition, int planId, boolean success) {
        return renderingStorage.updateRenderings(dbId, condition, planId, success);
    }

    @Deprecated
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setDeliveryFileStatusAfterRnd(Integer dfId, RenderingPlan plan) {
        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
        synchronized (RenderingService.class) {
            StatusEntity statusEntity = null;
            try {
                //long count1 = (Long) manager.createNamedQuery("recordCountByDFileQasSuccess")
                //    .setParameter("df", df).getSingleResult();
                Long count1  = (Long) RecordEntity.queryRecordCountRenderByDfId(df.getId(), manager).getSingleResult();
                RenderingPlanEntity planEntity = getPlanEntity(plan.planName);
                //number of completed renderings for this plan
                long count2 = (Long) manager.createNamedQuery("rndCountByDFileAndRndCompleted").setParameter("df", df)
                    .setParameter(PLAN, planEntity).getSingleResult();
                //number of completed records for all plans
                long count3 = (Long) manager.createNamedQuery("recordCountByDFileAndRndCompleted")
                    .setParameter("df", df).getSingleResult();

                LOG.debug("sum recs in df " + dfId + " =" + count1
                    + " completed for plan " + plan + " " + count2 + " comp for all plans=" + count3);
                if (count1 == count2) {
                    setPlanCompleted(df, plan);
                }

                if (count1 == count3) {
                    //number of successful completed records for all plans
                    long count4 = (Long) manager.createNamedQuery("recordCountByDFileRndSuccessful")
                        .setParameter("df", df).getSingleResult();
                    if (count1 == count4) {
                        statusEntity = manager.find(StatusEntity.class,
                            IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS);
                    } else if ((count1 - count4) * PERCENT / count1 > Integer.valueOf(
                        CochraneCMSProperties.getProperty("cms.cochrane.threshold.renderingFailed"))) {
                        statusEntity = manager.find(StatusEntity.class,
                            IDeliveryFileStatus.STATUS_RND_FAILED);
                    } else {
                        statusEntity = manager.find(StatusEntity.class,
                            IDeliveryFileStatus.STATUS_RND_SOME_FAILED);
                    }
                    df.setStatus(statusEntity);
                }
            } catch (Exception e) {
                LOG.error("Coudn't set status: " + e.getMessage());
            }
        }
    }

    public void setDeliveryFileModifyStatusAfterRnd(Integer dfId, int firstRecordId) throws DeliveryPackageException {
        synchronized (RenderingService.class) {
            DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
            RecordEntity record = manager.find(RecordEntity.class, firstRecordId);
            int modifyStatusId = df.getModifyStatus().getId();
            StatusEntity modifyStatusEntity = null;

            if (record.isRenderingCompleted()) {
                switch (modifyStatusId) {
                    case IDeliveryFileStatus.STATUS_MAKE_PERM_STARTED:
                        modifyStatusEntity = manager.find(StatusEntity.class,
                            IDeliveryFileStatus.STATUS_MAKE_PERM_FINISHED);
                        df.setModifyStatus(modifyStatusEntity);
                        break;
                    case IDeliveryFileStatus.STATUS_EDITING_STARTED:
                        modifyStatusEntity = manager.find(StatusEntity.class,
                            IDeliveryFileStatus.STATUS_EDITING_FINISHED);
                        df.setModifyStatus(modifyStatusEntity);
                        break;
                    default:
                        ;
                }
            }
        }
    }

    private void setPlanCompleted(DeliveryFileEntity df, RenderingPlan plan) {
        if (plan.equals(RenderingPlan.CD)) {
            df.setCdCompleted(true);
        } else if (plan.equals(RenderingPlan.HTML)) {
            df.setHtmlCompleted(true);
        } else if (plan.equals(RenderingPlan.PDF_TEX)) {
            df.setPdfCompleted(true);
        }
    }

    public long getRecordCountByDf(Integer id) {
        return recordStorage.getRecordCountByDf(id);
    }

    public boolean isQasCompleted(Integer id) {
        OpStats stats = recordStorage.getQasCompleted(id);
        return stats.isCompleted();
    }

    public HashMap<String, Integer> getPlanNameToId() {
        if (planNameToId == null) {
            planNameToId = new HashMap<String, Integer>();
            List<RenderingPlanEntity> plans = manager.createNamedQuery("rndPlans").getResultList();
            for (RenderingPlanEntity plan : plans) {
                planNameToId.put(plan.getDescription(), plan.getId());
            }
        }
        return planNameToId;
    }

    public void writeResultToDb(int jobId, String result) {
        RndNextJobEntity next = new RndNextJobEntity();
        next.setJobId(jobId);
        next.setResult(result);
        manager.persist(next);
        LOG.debug("wrote result for jobId=" + jobId);
    }

    public DelayedThread getRndNextJob(int jobId) {
        try {
            RndNextJobEntity next = (RndNextJobEntity) manager.createNamedQuery("rndNextJob")
                .setFirstResult(0).setMaxResults(1)
                .getSingleResult();
            DelayedThread job = new DelayedThread(next.getJobId(), next.getResult());
            LOG.debug("got result for jobId=" + next.getJobId());
            manager.createNamedQuery("deleteRndNextJob").setParameter("id", next.getId())
                .executeUpdate();
            return job;
        } catch (NoResultException e) {
            return null;
        }
    }

    private URI getAcceptRenderingResultsCallback() throws URISyntaxException {
        return new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
            + "AcceptRenderingResults/AcceptRenderingResults?wsdl");
    }

}