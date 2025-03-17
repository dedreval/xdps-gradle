package com.wiley.cms.cochrane.cmanager;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DelayedThread;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.JobQasResultEntity;
import com.wiley.cms.cochrane.cmanager.data.QaNextJobEntity;
import com.wiley.cms.cochrane.cmanager.data.StartedJobQaEntity;
import com.wiley.cms.cochrane.cmanager.data.StatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.parser.FinishException;
import com.wiley.cms.cochrane.cmanager.parser.SourceHandler;
import com.wiley.cms.cochrane.cmanager.parser.SourceParser;
import com.wiley.cms.cochrane.cmanager.parser.SourceParsingResult;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 02-Apr-2007
 */

@Stateless
@Local(IQaService.class)
public class QaService implements IQaService, java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(QaService.class);

    private static final int PACK_SIZE = Integer.parseInt(
            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_FLUSH_PACK_SIZE));
    private static final int FIELD_SIZE = Integer.parseInt(
            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.FIELD_SIZE_UNIT_TITLE));

    private static final String METHODOLOGY = "MR";

    private static IActivityLogService logService;
    private static String logUser = CochraneCMSProperties.getProperty(
        CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
    private static final String CMANAGER_PARSER_TITLE_PARSER = "com.wiley.cms.cochrane.cmanager.parser.TitleParser";
    private static final String CMANAGER_PARSER_SORT_TITLE_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.SortTitleParser";
    private static final String CMANAGER_PARSER_UNIT_STATUS_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.UnitStatusParser";
    private static final String CMANAGER_PARSER_SUB_TITLE_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.SubTitleParser";
    private static final String CMANAGER_PARSER_DOI_PARSER = "com.wiley.cms.cochrane.cmanager.parser.DoiParser";
    private static final String CMANAGER_PARSER_METHODOLOGY_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.MethodologyParser";
    private static final String CMANAGER_PARSER_GROUP_PARSER = "com.wiley.cms.cochrane.cmanager.parser.GroupParser";
    private static final String CMANAGER_PARSER_CLISSUE_PARSER = "com.wiley.cms.cochrane.cmanager.parser.CLIssueParser";
    private static final String CMANAGER_PARSER_CENTRAL_UNIT_STATUS_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.CentralUnitStatusParser";
    private static final String CMANAGER_PARSER_CMR_UNIT_STATUS_PARSER
        = "com.wiley.cms.cochrane.cmanager.parser.CmrUnitStatusParser";

    private static final String RECORD_NAME = "recordName";
    private static final String GET_QAS_RESULT_ENTITY_BY_RECORD = "getQasResultEntityByRecord";

    @EJB(beanName = "RecordStorage")
    IRecordStorage recordStorage;

    @PersistenceContext
    private EntityManager manager;

    private HashMap<String, Integer> subTitleMap;
    private HashMap<String, Integer> statusMapCDSR;
    private HashMap<String, Integer> statusMap;
    private HashMap<String, String> groupMap;

    @Deprecated
    public void startQas(List<URI> urisList, int pckId, String pckName, boolean delay) throws Exception {
        URI callback = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
            + "AcceptQaResults?wsdl");
        startQas(urisList, pckId, pckName, delay, callback);
    }

    @Deprecated
    public void startQas(List<URI> urisList, int pckId, String pckName, boolean delay, URI callback)
        throws Exception {
        try {
            if (urisList == null || urisList.size() == 0) {
                throw new Exception("Not formed URI list for Qas");
            }
            if (delay) {
                try {
                    long delayBetweenJobs = Long.valueOf(
                        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_STARTING_DELAY));
                    Thread.sleep(delayBetweenJobs);
                } catch (InterruptedException e) {
                    LOG.debug(e, e);
                }
            }

            URI[] uris = new URI[urisList.size()];
            urisList.toArray(uris);

            IProvideQa ws = WebServiceUtils.getProvideQa();
            int jobId = ws.multiFileQa("cochrane", "simple", uris, callback, IProcessManager.USUAL_PRIORITY);
            if (jobId == -1) {
                throw new Exception("Qas failed, jobId=-1");
            }
            createStartedJob(jobId, pckId);
            logQasStarted(jobId, pckId, pckName, ILogEvent.QAS_STARTED, MessageSender.MSG_TITLE_QAS_JOB_CREATED, "");
        } catch (Throwable e) {
            LOG.error(e, e);
            logQasStarted(-1, pckId, pckName, ILogEvent.QAS_FAILED, MessageSender.MSG_TITLE_QAS_JOB_FAILED,
                    e.getMessage());
        }
    }

    private void logQasStarted(int jobId, int pckId, String pckName, int status, String messageName,
                               String reportText) {
        LOG.debug("QAS started, job id: " + jobId);
        if (logService == null) {
            logService = AbstractManager.getActivityLogService();
        }
        logService.info(ActivityLogEntity.EntityLevel.FILE, status, pckId, pckName, logUser,
            "job id: " + jobId);

        Map<String, String> map = new HashMap<String, String>();
        map.put(MessageSender.MSG_PARAM_JOB_ID, Integer.toString(jobId));
        map.put(MessageSender.MSG_PARAM_DELIVERY_FILE, pckName);
        map.put(MessageSender.MSG_PARAM_REPORT, reportText);
        String identifiers = MessageSender.getCDnumbersFromMessageByPattern(reportText, map);
        map.put(MessageSender.MSG_PARAM_RECORD_ID, identifiers);

        MessageSender.sendMessage(messageName, map);
    }

    private void createStartedJob(int jobId, int pckId) {
        if (pckId == -1) {
            //delivery file is null in record saving/modifying process
            return;
        }
        StartedJobQaEntity st = new StartedJobQaEntity();
        st.setJobId(jobId);
        st.setDeliveryFile(manager.find(DeliveryFileEntity.class, pckId));
        //st.setPlanId(IRenderingService.PLAN_QAS_ID);
        manager.persist(st);
        manager.flush();
    }

    @Deprecated
    public void updateDeliveryFile(int dfId, boolean isSuccess) {

        StatusEntity statusEntity = null;

        try {
            if (dfId == -1) {
                throw new Exception("Delivery fileId =-1");
            }
            DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
            if (isSuccess) {
                statusEntity = manager.find(StatusEntity.class, IDeliveryFileStatus.STATUS_QAS_STARTED);
                df.setInterimStatus(statusEntity);
            } else {
                statusEntity = manager.find(StatusEntity.class, IDeliveryFileStatus.STATUS_QAS_FAILED);
                df.setStatus(statusEntity);
            }
            manager.merge(df);
            manager.flush();
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    
    public RecordEntity updateCCARecord(String recordName, String dbName, int issueId, boolean successful,
                                        String result, String unitTitle, Integer unitStatusId) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity db = getDbEntity(dbName, issueEntity);
        RecordEntity re = getRecord(db, recordName);
        re.setApproved(successful);
        re.setQasCompleted(true);
        re.setQasSuccessful(successful);
        re.setRenderingCompleted(true);
        re.setRenderingSuccessful(successful);
        re.setTitleEntity(TitleEntity.checkEntity(unitTitle, re, manager));
        if (unitStatusId != null) {
            re.setUnitStatus(manager.find(UnitStatusEntity.class, unitStatusId));
        }
        if (result != null) {
            updateQasResult(result, re);
        }
        manager.merge(re);
        return re;
    }

    private void updateQasResult(String result, RecordEntity re) {
        try {
            JobQasResultEntity qaResult = (JobQasResultEntity) manager.createNamedQuery(GET_QAS_RESULT_ENTITY_BY_RECORD)
                .setParameter("id", re.getId()).getSingleResult();
            qaResult.setResult(result);
            manager.merge(qaResult);
        } catch (NoResultException e) {
            JobQasResultEntity qaResult = new JobQasResultEntity();
            qaResult.setRecord(re);
            qaResult.setResult(result);
            manager.persist(qaResult);
        }
    }

    //update records after qas passed
    @Deprecated
    public void updateRecords(Collection<Record> records, String dbName, int issueId, boolean isTranslatedAbstracts,
        boolean isMeshterm, boolean isWhenReady) {

        parseSources(records, dbName, issueId, isWhenReady);
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity db = getDbEntity(dbName, issueEntity);
        Iterator<Record> recs = records.iterator();
        int j = 0;
        boolean isCDSR = CochraneCMSPropertyNames.getCDSRDbName().equals(dbName);

        //UnitStatusEntity meshStatus = !isMeshterm ? null
        //        : manager.find(UnitStatusEntity.class, UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED);

        while (recs.hasNext()) {
            Record record = recs.next();
            RecordEntity re = getRecord(db, record.getName());

            record.setId(re.getId());

            re.setQasCompleted(true);
            re.setQasSuccessful(record.isSuccessful());
            if (isCDSR && !record.isSuccessful()) {
                re.setState(RecordEntity.STATE_WR_ERROR);
            }
//            re.setQasResult(record.getMessages());
            //re.setUnitTitle(record.getTitle());
            re.setTitleEntity(TitleEntity.checkEntity(record.getTitle(), re, manager));

            if (re.getUnitStatus() == null || !re.getUnitStatus().isTranslationUpdated()) {

                if (isTranslatedAbstracts && re.getUnitStatus().isMeshtermUpdated()) {
                    re.setUnitStatus(manager.find(UnitStatusEntity.class,
                            UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS));
                } else {
                    re.setUnitStatus(record.getUnitStatus() == null ? null
                        : manager.find(UnitStatusEntity.class, record.getUnitStatus()));
                }
            }

            //re.setUnitStatus(manager.find(UnitStatusEntity.class, record.getUnitStatus()));
            re.setProductSubtitle(record.getSubTitle() == null
                    ? null : manager.find(ProductSubtitleEntity.class, record.getSubTitle()));

            if (record.getMessages() != null && record.getMessages().length() != 0) {
                fillQaResult(record.getMessages(), re);
            }
            if (++j % PACK_SIZE == 0) {
                manager.flush();
                LOG.debug("j=" + j);
            }
        }
    }

    @Deprecated
    public void updateRecordsQAStatuses(Collection<String> records, String dbName, int issueId, int deliveryFileId,
                                        boolean status) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity db = getDbEntity(dbName, issueEntity);

        recordStorage.updateRecords(records, deliveryFileId, status, db);
    }

    private void fillQaResult(String messages, RecordEntity re) {
        JobQasResultEntity result = new JobQasResultEntity();
        result.setRecord(re);
        result.setResult(messages);
        manager.persist(result);
    }

    public void updateQaResults(int recordId, String qasResultMessages) {
        try {
            JobQasResultEntity qaResult = (JobQasResultEntity) manager.createNamedQuery(GET_QAS_RESULT_ENTITY_BY_RECORD)
                .setParameter("id", recordId).getSingleResult();
            qaResult.setResult(qasResultMessages);
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    public void parseSources(Collection<Record> records, String dbName, int issueId, boolean isWhenReady) {
        String[] tagTitle = CmsUtils.fillTagsArray(dbName);
        String[] sortTags = CmsUtils.fillSortTagsArray(dbName);
        String[] tagSubTitle = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_SUB_TITLE).split("/");
        String[][] tags = null;
        String[] parserNames = null;
        boolean isSysrev = false;
        if (dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))) {
            tags = getTags(tagTitle, sortTags, tagSubTitle);
            parserNames = getCDSRParsers();
            isSysrev = true;
        } else if (dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEED))
            || dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLABOUT))) {
            String[] tagUnitStatus = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_UNIT_STATUS)
                .split("/");
            tags = new String[][]{tagTitle, sortTags, tagUnitStatus, tagSubTitle};
            parserNames = new String[]{CMANAGER_PARSER_TITLE_PARSER,
                CMANAGER_PARSER_SORT_TITLE_PARSER,
                CMANAGER_PARSER_UNIT_STATUS_PARSER,
                CMANAGER_PARSER_SUB_TITLE_PARSER};
        } else if (dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR))) {
            String[] tagUnitStatus = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_UNIT_STATUS_CLCMR)
                .split("/");
            tags = new String[][]{tagTitle, sortTags, tagUnitStatus};
            parserNames = getClcmrParsers();
        } else if (dbName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            String[] tagUnitStatus = CochraneCMSProperties.getProperty(
                CochraneCMSPropertyNames.TAG_UNIT_STATUS_CLCENTRAL)
                .split("/");
            tags = new String[][]{tagTitle, sortTags, tagUnitStatus};
            parserNames = getCentralParsers();
        } else {
            tags = buildDefaultTags(tagTitle, sortTags);
            parserNames = getDefaultParsers();
        }

        LOG.debug("parsing source XML started");
        processRecords(records, issueId, tags, parserNames, isSysrev, isWhenReady);
        LOG.debug("parsing source XML finished");
    }

    private String[][] buildDefaultTags(String[] tagTitle, String[] sortTags) {
        String[] tagUnitStatus = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_UNIT_STATUS).split("/");
        return new String[][]{tagTitle, sortTags, tagUnitStatus};
    }

    private String[] getDefaultParsers() {
        return new String[]{CMANAGER_PARSER_TITLE_PARSER,
            CMANAGER_PARSER_SORT_TITLE_PARSER,
            CMANAGER_PARSER_UNIT_STATUS_PARSER};
    }

    private String[] getCDSRParsers() {
        return new String[]{CMANAGER_PARSER_TITLE_PARSER,
            CMANAGER_PARSER_SORT_TITLE_PARSER,
            CMANAGER_PARSER_UNIT_STATUS_PARSER,
            CMANAGER_PARSER_SUB_TITLE_PARSER,
            CMANAGER_PARSER_DOI_PARSER,
            CMANAGER_PARSER_METHODOLOGY_PARSER,
            CMANAGER_PARSER_GROUP_PARSER,
            CMANAGER_PARSER_CLISSUE_PARSER};
    }

    private String[] getCentralParsers() {
        return new String[]{CMANAGER_PARSER_TITLE_PARSER,
            CMANAGER_PARSER_SORT_TITLE_PARSER,
            CMANAGER_PARSER_CENTRAL_UNIT_STATUS_PARSER};
    }

    private String[] getClcmrParsers() {
        return new String[]{CMANAGER_PARSER_TITLE_PARSER,
            CMANAGER_PARSER_SORT_TITLE_PARSER,
            CMANAGER_PARSER_CMR_UNIT_STATUS_PARSER};
    }

    private String[][] getTags(String[] tagTitle, String[] sortTags, String[] tagSubTitle) {
        String[][] tags;
        String[] tagUnitStatus = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_UNIT_STATUS).split("/");
        String[] tagDoi = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_DOI).split("/");
        String[] tagMeth = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_METH).split("/");
        String[] tagGroup = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_GROUP).split("/");
        String[] tagClIssue = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_CL_ISSUE).split("/");
        tags = new String[][]{tagTitle, sortTags, tagUnitStatus, tagSubTitle, tagDoi, tagMeth, tagGroup, tagClIssue};
        return tags;
    }

    private void processRecords(Collection<Record> records, int issueId,
                                String[][] tags, String[] parserNames, boolean isSysrev, boolean isWhenReady) {
        try {
            for (Record record : records) {
                parseSourceXml(record, tags, parserNames, issueId, isSysrev, isWhenReady);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void parseSourceXml(Record record, String[][] tags, String[] parserNames,
                                int issueId, boolean isSysrev, boolean isWhenReady) {
        IRepository rps = RepositoryFactory.getRepository();

        String recordXMLPath = record.getRecordSourceUri();
        if (recordXMLPath == null) {
            LOG.error("uri for record " + record.getName() + "=null");
            return;
        }
        SourceParsingResult sourceResult = new SourceParsingResult();
        try {
            //String data = hideEntities(CmsUtils.correctDtdPath
            //        (InputUtils.readStreamToString(rps.getFile(recordXMLPath))));

            String data = CmsUtils.correctDtdPath(InputUtils.readStreamToString(rps.getFile(recordXMLPath)));

            data = hideEntities(data);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(true);

            factory.setNamespaceAware(true);

            SAXParser parser = factory.newSAXParser();
            parser.parse(new ByteArrayInputStream(data.getBytes()),
                new SourceHandler(sourceResult, tags,
                    getParserInstances(sourceResult, parserNames, tags)));

        } catch (Exception e) {
            fillField(sourceResult, record, e, isSysrev);
        }
        // only for sysrev
        if (isSysrev) {
            fillFieldsForSysrev(record, sourceResult, issueId, isWhenReady);
        }
    }

    private String hideEntities(String source) {
        return source.replaceAll("&", "&amp;");
    }

    private void fillField(SourceParsingResult sourceResult, Record record, Exception e, boolean isCDSR) {
        String title;

        if (e instanceof FinishException) {
            title = sourceResult.getTitle();

        } else {
            LOG.error(e, e);
            title = sourceResult.getTitle();
        }

        //// a title size limitation below removed: XDPS-2198
        //if (title != null && title.length() > FIELD_SIZE) {
        //    title = title.substring(0, FIELD_SIZE - 1);
        //}

        record.setTitle(title);

        if (sourceResult.getStatus() != null) {
            record.setUnitStatusId(getStatusId(sourceResult.getStatus(), isCDSR));
        }
        if (sourceResult.getSubTitle() != null) {
            record.setSubTitle(getSubTitleId(sourceResult.getSubTitle()));
        }
    }

    public SourceParser[] getParserInstances(SourceParsingResult sourceResult,
                                             String[] parserNames, String[][] tags)
        throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        int i = 0;
        SourceParser[] parsers = new SourceParser[parserNames.length];
        for (String pName : parserNames) {
            SourceParser p = (SourceParser) Class.forName(pName).newInstance();
            p.setTagsCount(tags[i].length);
            p.setResult(sourceResult);
            parsers[i++] = p;
        }
        return parsers;
    }

    private boolean isCurrentIssue(String clIssue, int issueId) {
        int year = Integer.valueOf(StringUtils.substringBefore(clIssue, " "));
        int issue = Integer.valueOf(StringUtils.substringAfterLast(clIssue, " "));
        IssueEntity ie = manager.find(IssueEntity.class, issueId);
        return year == ie.getYear() && issue == ie.getNumber();
    }

    private void fillFieldsForSysrev(Record record, SourceParsingResult sourceResult, int issueId,
                                     boolean isWhenReady) {

        if (!isWhenReady && sourceResult.getClIssue() != null) {
            if (!isCurrentIssue(sourceResult.getClIssue().trim(), issueId)) {
                record.setUnitStatusId(getStatusId("Meshterms updated", true));
            }
        }
        if (sourceResult.getReviewType() != null) {
            record.setMeth(METHODOLOGY.equals(sourceResult.getReviewType()));
            record.setReviewType(sourceResult.getReviewType());
        }
        //if (sourceResult.getGroup() != null) {
        //    record.setGroup(getGroupSid(sourceResult.getGroup(), issueId));
        //}
    }

    private RecordEntity getRecord(ClDbEntity db, String name) {
        RecordEntity record = null;
        try {
            return (RecordEntity) manager.createNamedQuery(RecordEntity.QUERY_SELECT_BY_DB_AND_NAME)
                .setParameter("db", db.getId())
                .setParameter(RECORD_NAME, name)
                .getSingleResult();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return record;
    }

    private Integer getSubTitleId(String s) {
        if (subTitleMap == null) {
            subTitleMap = new HashMap<String, Integer>();
            List<ProductSubtitleEntity> titles = manager.createNamedQuery("subTitles").getResultList();
            for (ProductSubtitleEntity t : titles) {
                subTitleMap.put(t.getName().toUpperCase().trim(), t.getId());
            }
        }

        Integer id = -1;
        if (subTitleMap.size() != 0) {
            id = subTitleMap.get(s.toUpperCase().trim());
            if (id == null) {
                LOG.error("Not found subTitle: " + s);
                id = -1;
            }
        }
        return id;
    }

    private Integer getStatusId(String s, boolean isCDSR) {
        if (statusMapCDSR == null || statusMap == null) {
            statusMapCDSR = new HashMap<String, Integer>();
            statusMap = new HashMap<String, Integer>();

            List<UnitStatusEntity> st = UnitStatusEntity.queryAll(manager).getResultList();
            for (UnitStatusEntity t : st) {
                if (t.is4Cdsr()) {
                    statusMapCDSR.put(t.getName().trim(), t.getId());
                } else {
                    statusMap.put(t.getName().trim(), t.getId());
                }
            }
        }
        Integer id = -1;
        if (isCDSR) {
            id = getStatus(statusMapCDSR, s);

        } else {
            id = getStatus(statusMap, s);
        }
        return id;
    }

    private Integer getStatus(Map<String, Integer> statusMap, String s) {
        Integer id = null;

        if (statusMap.size() != 0) {
            id = statusMap.get(s.trim());
            if (id == null) {
                LOG.error("Not found status: " + s);
                id = -1;
            }
        }

        return id;
    }

    @Deprecated
    private String getGroupSid(String s, int issueId) {
        if (groupMap == null) {
            groupMap = new HashMap<String, String>();
            ClDbEntity db = null;
            try {
                db = getDbEntity(CochraneCMSPropertyNames.getAboutDbName(), manager.find(IssueEntity.class, issueId));
            } catch (NoResultException e) {
                LOG.error("Not found data base CLABOUT for issue" + issueId);
                return "";
            }
            List<RecordEntity> groups = manager.createNamedQuery("recordGroupNameFromClabout")
                .setParameter("db", db)
                .getResultList();
            for (RecordEntity t : groups) {
                groupMap.put(t.getUnitTitle().toUpperCase().trim(), t.getName());
            }
        }
        String id = "";
        if (groupMap.size() != 0) {
            id = groupMap.get(s.toUpperCase().trim());
            if (id == null) {
                LOG.error("Not found group in clabout: " + s);
                id = "";
            }
        }
        return id;
    }

    @Deprecated
    public void writeResultToDb(int jobId, String qaResult) {

        try {
            QaNextJobEntity job = (QaNextJobEntity) manager.createNamedQuery("findQaNextJobById")
                .setParameter("jobId", jobId).getSingleResult();
        } catch (NoResultException e) {
            QaNextJobEntity next = new QaNextJobEntity();
            next.setJobId(jobId);
            next.setResult(qaResult);
            manager.persist(next);
            LOG.debug("wrote result for jobId=" + jobId);
        }
    }

    @Deprecated
    public DelayedThread getQaNextJob() {
        try {
            QaNextJobEntity next = (QaNextJobEntity) manager.createNamedQuery("qaNextJob")
                .setFirstResult(0).setMaxResults(1)
                .getSingleResult();
            DelayedThread job = new DelayedThread(next.getJobId(), next.getResult());
            LOG.debug("got result for jobId=" + next.getJobId());
            manager.createNamedQuery("deleteQaNextJob").setParameter("id", next.getId())
                .executeUpdate();
            return job;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Deprecated
    public void deleteQasResultByDf(int dfId) {
        manager.createNamedQuery("qasResultDeleteByDf").setParameter("dfId", dfId).executeUpdate();
    }

    private ClDbEntity getDbEntity(String dbName, IssueEntity issueEntity) {
        return (ClDbEntity) manager
            .createNamedQuery("dbByTitleAndIssue")
            .setParameter("db", dbName)
            .setParameter("issue", issueEntity).getSingleResult();
    }
}