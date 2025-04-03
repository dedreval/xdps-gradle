package com.wiley.cms.cochrane.cmanager.data;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaCdsrDoiEntity;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaCdsrDoiViewEntity;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaEntity;
import com.wiley.cms.cochrane.cmanager.data.cca.WhenReadyPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entity.AbstractRecord;
import com.wiley.cms.cochrane.cmanager.entity.DbRecordEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchFilesStatus;
import com.wiley.cms.cochrane.cmanager.publish.EpochAwaitingTimeoutChecker;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IResultsStorage.class)
public class ResultsStorage implements IResultsStorage, java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(ResultsStorage.class);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private static final int N_24 = -24;
    private static final int N_7 = -7;

    private static final String NUMBER_PARAM = "number";
    private static final String DATE_PARAM = "date";
    private static final String YEAR_PARAM = "year";
    private static final String RECORD_NAME_PARAM = "recordName";

    private static final MessageFormat ISSUE_COUNT_QUERY_TEMPLATE;
    private static final MessageFormat LATER_ISSUE_COUNT_QUERY_TEMPLATE;
    private static final MessageFormat DELIVERY_FILE_QUERY_TEMPLATE;

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String ISSUE = "issue";
    private static final String RECORD = "record";
    private static final String JOB_ID = "jobId";
    private static final String DB_ID = "dbId";
    private static final String CREATE_RECORDS_FINISH = "createRecords finish";

    private static final int SAFE_RECORD_COUNT = 20;

    @PersistenceContext
    private EntityManager manager;

    static {
        StringBuilder sb = new StringBuilder()
                .append("select count(issue) from IssueEntity issue where issue.number = :number and ")
                .append("issue.year = :year ")
                .append("{0}");

        ISSUE_COUNT_QUERY_TEMPLATE = new MessageFormat(sb.toString());

        sb = new StringBuilder()
                .append("select count(issue) from IssueEntity issue where issue.number >= :number and ")
                .append("issue.year >= :year {0}");

        LATER_ISSUE_COUNT_QUERY_TEMPLATE = new MessageFormat(sb.toString());

        StringBuilder sbDeliveryFile = new StringBuilder()
                .append("select uf from DeliveryFileEntity uf where ")
                .append("uf.issue=:i ")
                .append("order by uf.date desc");

        DELIVERY_FILE_QUERY_TEMPLATE = new MessageFormat(sbDeliveryFile.toString());
    }

    public void mergeIssue(IssueEntity issue) {

        manager.merge(issue);
        manager.flush();
    }

    private int getSingleResultIntValue(Query q) {
        return ((Number) q.getSingleResult()).intValue();
    }

    public boolean isExistsIssueByYearAndNumber(int id, int year, int number, boolean equalAndMore) {
        int count;
        MessageFormat template = equalAndMore ? LATER_ISSUE_COUNT_QUERY_TEMPLATE : ISSUE_COUNT_QUERY_TEMPLATE;
        if (id > 0) {
            count = getSingleResultIntValue(
                    manager.createQuery(template.format(new Object[]{" and issue.id <> :i"}))
                            .setParameter("i", id)
                            .setParameter(NUMBER_PARAM, number)
                            .setParameter(YEAR_PARAM, year));
        } else {
            count = getSingleResultIntValue(manager.createQuery(template.format(new Object[]{""}))
                    .setParameter(NUMBER_PARAM, number)
                    .setParameter(YEAR_PARAM, year));
        }
        return count > 0;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public IssueEntity getIssue(int issueId) {
        return manager.find(IssueEntity.class, issueId);
    }

    public List<RecordEntity> getIssueRecordEntityList(int issueId) {
        try {
            List<RecordEntity> issueRecordList = new ArrayList<>();

            List dbList = getDbList(issueId);
            for (Object dbo : dbList) {
                ClDbEntity db = (ClDbEntity) dbo;
                List<RecordEntity> dbRecordList = getRecords(db.getId());
                issueRecordList.addAll(dbRecordList);
            }
            return issueRecordList;
        } catch (Exception e) {
            LOG.error(e, e);
        }
        throw new IllegalStateException("");
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getSimilarRecordsMap(int recordId) {
        Map<String, String> map = new LinkedHashMap<String, String>();

        /*RecordEntity record = manager.find(RecordEntity.class, recordId);

        Query getIssuesQuery = manager.createQuery("select i from IssueEntity i order by i.year desc, i.number desc");
        List<IssueEntity> issues = getIssuesQuery.getResultList();

        for (IssueEntity issue : issues) {
            try {
                ClDbEntity db = getClDbEntity(record.getDb().getTitle(), issue);

                Query getRecordQuery = RecordEntity.queryRecords(db.getId(), record.getName(), manager);

                map.put(issue.getTitle(), ((RecordEntity) getRecordQuery.getSingleResult()).getId().toString());
            } catch (Exception e) {
                LOG.debug(e.getMessage());
            }
        }*/
        return map;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<IssueEntity> getIssueList() {
        return getIssueList(0, 0);
    }

    @SuppressWarnings("unchecked")
    public List<IssueEntity> getIssuesWithEditorials() {
        return (List<IssueEntity>) IssueEntity.getIssuesWithEditorials(manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<IssueEntity> getIssueList(int beginIndex, int amount) {
        return IssueEntity.queryAll(beginIndex, amount, manager).getResultList();
    }

    public int createIssue(String title, Date date, int number, int year, Date publishDate) {
        IssueEntity issueEntity = new IssueEntity();
        issueEntity.setTitle(title);
        issueEntity.setDate(date);
        issueEntity.setNumber(number);
        issueEntity.setYear(year);
        issueEntity.setPublishDate(publishDate);
        issueEntity.setArchived(false);
        issueEntity.setArchiving(false);
        issueEntity.setMeshtermsDownloaded(false);
        issueEntity.setMeshtermsDownloading(false);

        manager.persist(issueEntity);
        return issueEntity.getId();
    }

    private IssueEntity getIssueByNumber(int year, int issueNumber) {
        List<IssueEntity> ret = IssueEntity.getIssue(year, issueNumber, manager).getResultList();
        return ret.isEmpty() ? null : ret.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int findIssue(int year, int issueNumber) {
        IssueEntity ret = getIssueByNumber(year, issueNumber);
        return ret == null ? DbEntity.NOT_EXIST_ID : ret.getId();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int findOpenIssue(int year, int issueNumber) throws CmsException {
        return findOpenIssueEntity(year, issueNumber).getId();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public IssueEntity findOpenIssueEntity(int year, int issueNumber) throws CmsException {

        IssueEntity ret = getIssueByNumber(year, issueNumber);
        if (ret == null) {
            throw new CmsException(String.format("Can't find issue by year: %d and number: %d", year, issueNumber));
        }

        if (ret.isClosed()) {
            throw new CmsException(String.format("The issue %d %d is archiving or has archived", year, issueNumber));
        }
        return ret;
    }


    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getIssueListCount() {
        Object res = manager.createQuery("select count(i) from IssueEntity i").getSingleResult();
        if (res == null) {
            return 0;
        } else {
            return ((Number) res).intValue();
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public DeliveryFileEntity updateDeliveryFile(int id, int issueId, String vendor, int dbId) {
        DeliveryFileEntity deliveryFileEntity = null;
        try {
            deliveryFileEntity = manager.find(DeliveryFileEntity.class, id);
            updateDf(deliveryFileEntity, issueId, dbId, vendor);
            manager.flush();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return deliveryFileEntity;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createDeliveryFile(String name, int status) {
        DeliveryFileEntity deliveryFileEntity = createDf(name, DeliveryFileEntity.TYPE_DEFAULT, status);
        manager.persist(deliveryFileEntity);
        return deliveryFileEntity.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createDeliveryFile(int dbId, String name, int type, int status, int interimStatus, String vendor) {
        DeliveryFileEntity deliveryFileEntity = createDf(name, type, status);
        deliveryFileEntity.setInterimStatus(manager.find(StatusEntity.class, interimStatus));
        deliveryFileEntity.setVendor(vendor);
        ClDbEntity dbEntity = manager.find(ClDbEntity.class, dbId);
        if (dbEntity != null) {
            deliveryFileEntity.setIssue(dbEntity.getIssue());
            deliveryFileEntity.setDb(dbEntity);
        }
        manager.persist(deliveryFileEntity);
        return deliveryFileEntity.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createDeliveryFile(String name, int type, int status, int interimStatus) {
        DeliveryFileEntity deliveryFileEntity = createDf(name, type, status);
        deliveryFileEntity.setInterimStatus(manager.find(StatusEntity.class, interimStatus));
        manager.persist(deliveryFileEntity);
        return deliveryFileEntity.getId();
    }

    private void updateDf(DeliveryFileEntity deliveryFileEntity, int issueId, int dbId, String vendor) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity dbEntity = dbId != DbEntity.NOT_EXIST_ID ? manager.find(ClDbEntity.class, dbId) : null;
        deliveryFileEntity.setIssue(issueEntity);
        deliveryFileEntity.setVendor(vendor);
        deliveryFileEntity.setDb(dbEntity);
    }

    private DeliveryFileEntity createDf(String name, int type, int status) {
        DeliveryFileEntity deliveryFileEntity;
        StatusEntity statusEntity = manager.find(StatusEntity.class, status);
        deliveryFileEntity = new DeliveryFileEntity();
        deliveryFileEntity.setName(name);
        deliveryFileEntity.setStatus(statusEntity);
        deliveryFileEntity.setDate(new Date());
        deliveryFileEntity.setType(type);
        return deliveryFileEntity;
    }

    public DeliveryFileVO getDeliveryFileVO(int issueId, String db, String name) {
        RecordEntity recordEntity = getRecord(issueId, db, name);
        if (recordEntity == null) {
            LOG.debug("NULL record: " + name);
            return null;
        }
        DeliveryFileEntity dfe = recordEntity.getDeliveryFile();

        return new DeliveryFileVO(dfe);
    }

    public DatabaseEntity getDatabaseEntity(String name) {
        DatabaseEntity databaseEntity = null;
        try {
            databaseEntity = (DatabaseEntity) DatabaseEntity.queryDatabase(name, manager).getSingleResult();
        } catch (NoResultException e) {
            LOG.debug(e.getMessage());
        }

        return databaseEntity;
    }

    public UnitStatusEntity getUnitStatus(String name, boolean isCDSR) {
        UnitStatusVO statusVO = CochraneCMSBeans.getRecordCache().getUnitStatus(name, isCDSR);
        return  (statusVO != null) ? manager.find(UnitStatusEntity.class, statusVO.getId()) : null;
    }

    public List<CcaEntity> getCcaEntitiesByName(String name) {
        return manager.createQuery("SELECT c FROM CcaEntity c WHERE c.name = :name ORDER BY c.date DESC")
                .setParameter(NAME, name)
                .getResultList();
    }

    public CcaEntity saveCcaEntity(String name, Date date) {
        CcaEntity ret = new CcaEntity();
        ret.setName(name);
        ret.setDate(date);
        manager.persist(ret);
        return ret;
    }

    public void saveDoiEntity(CcaEntity cca, String doi, String name) {
        CcaCdsrDoiEntity entity = new CcaCdsrDoiEntity();
        entity.setCca(cca);
        entity.setDoi(doi);
        entity.setCdsrName(name);
        manager.persist(entity);
    }

    public void createWhenReadyPublishSuccess(int dfId, String exportType) {
        createWhenReadyPublish(dfId, exportType, true, null);
    }

    public void createWhenReadyPublishFailure(int dfId, String exportType, String message) {
        createWhenReadyPublish(dfId, exportType, false, message);
    }

    private void createWhenReadyPublish(int dfId, String exportType, boolean successful, String message) {
        PublishTypeEntity typeEntity = getPublishType(exportType);
        DeliveryFileEntity df = getDeliveryFileEntity(dfId);

        WhenReadyPublishEntity entity = new WhenReadyPublishEntity();
        entity.setSuccessful(successful);
        entity.setDf(df);
        entity.setPublishType(typeEntity);
        entity.setMessage(message);
        manager.persist(entity);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setWhenReadyPublishStateByDeliveryFile(int oldState, int newState, int dfId) {
        return RecordEntity.querySetRecordsStateByDf(oldState, newState, dfId, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setWhenReadyPublishStateByDeliveryFile(int newState, int dfId, boolean successful) {
        Set<Integer> set =  new HashSet<>();
        set.add(RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION);
        set.add(RecordEntity.STATE_HW_PUBLISHING_ERR);
        return RecordEntity.querySetRecordsStateByDf(newState, dfId, successful, set, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getRecordCountByDeliveryFileAndState(Integer dfId, int state) {
        return RecordEntity.queryRecordsByDf(dfId, state, manager).getResultList().size();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int setWhenReadyPublishState(int oldState, int newState, int dbId) {
        return RecordEntity.querySetRecordsStateByDb(oldState, newState, dbId, manager).executeUpdate();
    }

    @Deprecated
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<EpochAwaitingTimeoutChecker.RecordSendingDate> getRecSendDateWhenReadyUnpublished(int wrType,
                                                                                                  int skip, int batch) {
        return new ArrayList<>();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<PublishedAbstractEntity> getPublishedAbstracts(Collection<Integer> recordNumbers, Integer dbId) {
        return PublishedAbstractEntity.queryPublishedAbstracts(dbId, recordNumbers, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setWhenReadyPublishState(int newState, int recordId) {
        RecordEntity.querySetRecordsStateByRecord(newState, recordId, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DeliveryFileVO getDeliveryFileVO(int id) {
        DeliveryFileEntity dfe = manager.find(DeliveryFileEntity.class, id);
        return new DeliveryFileVO(dfe);
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DeliveryFileVO> getDeliveryFiles(int clDbId) {
        return (List<DeliveryFileVO>) DeliveryFileEntity.queryDeliveryFilesVO(clDbId, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateDeliveryFileStatus(int deliveryId, int status, Set<Integer> notChangeableStatuses) {
        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, deliveryId);
        if (!notChangeableStatuses.contains(df.getStatus().getId())) {
            df.setStatus(manager.find(StatusEntity.class, status));
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String setDeliveryFileStatus(int deliveryId, int status, boolean isInterim, int type) {
        return setDeliveryFileStatus(manager.find(DeliveryFileEntity.class, deliveryId), status, isInterim, type);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String setDeliveryFileStatus(int deliveryId, int status, boolean interim) {
        DeliveryFileEntity df = DbUtils.exists(deliveryId) ? manager.find(DeliveryFileEntity.class, deliveryId) : null;
        return df != null ? setDeliveryFileStatus(df, manager.find(StatusEntity.class, status), interim) : "";
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setDeliveryFileStatus(Integer deliveryId, Integer status, Integer iStatus, Integer mStatus) {
        DeliveryFileEntity df = DbUtils.exists(deliveryId) ? manager.find(DeliveryFileEntity.class, deliveryId) : null;
        if (df == null) {
            return;
        }
        if (status != null) {
            df.setStatus(manager.find(StatusEntity.class, status));
        }
        if (iStatus != null) {
            df.setInterimStatus(manager.find(StatusEntity.class, iStatus));
        }
        if (mStatus != null) {
            df.setModifyStatus(manager.find(StatusEntity.class, mStatus));
        }
    }

    private String setDeliveryFileStatus(DeliveryFileEntity df, int status, boolean isInterim, int type) {
        if (df != null) {
            df.setType(type);
            return setDeliveryFileStatus(df, manager.find(StatusEntity.class, status), isInterim);
        }
        return "";
    }

    private String setDeliveryFileStatus(DeliveryFileEntity df, StatusEntity status, boolean interim) {
        if (status == null) {
            return "Undefined status";
        }
        if (interim) {
            df.setInterimStatus(status);
        } else {
            df.setStatus(status);
        }
        return status.getStatus();
    }

    public List<DeliveryFileEntity> getDeliveryFileList(int issueId, int interval) {
        return getDeliveryFileList(issueId, 0, 0, interval, false);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<DeliveryFileEntity> getLastDeliveryFileList(int issueId, int amount) {
        return getDeliveryFileList(issueId, 0, amount, 0, true);
    }

    public List<DeliveryFileVO> getDeliveryFileList(int dbId, Integer... statuses) {
        return DeliveryFileEntity.queryDeliveryFiles(dbId, Arrays.asList(statuses), manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<DeliveryFileEntity> getDeliveryFileList(int issueId, int beginIndex, int amount, int interval,
                                                         boolean withSpd) {
        try {
            Query query;
            if (interval > 0) {
                IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
                Calendar calendar = Calendar.getInstance();
                //addQuery = " and ";
                switch (interval) {
                    case SearchFilesStatus.LAST_DAY:
                        //addQuery +="uf.date >= SUBDATE(NOW(), INTERVAL 24 HOURS)";
                        calendar.add(Calendar.HOUR, N_24);
                        break;
                    case SearchFilesStatus.LAST_WEEK:
                        //addQuery +="uf.date >= SUBDATE(NOW(), INTERVAL 1 WEEK)";
                        calendar.add(Calendar.DATE, N_7);
                        break;
                    case SearchFilesStatus.LAST_MONTH:
                        //addQuery +="uf.date >= SUBDATE(NOW(), INTERVAL 1 MONTH)";
                        calendar.add(Calendar.MONTH, -1);
                        break;
                    default:
                        break;
                }
                Date date = calendar.getTime();
                query = manager.createQuery(DELIVERY_FILE_QUERY_TEMPLATE.format(new Object[]{"and uf.date>=:date"}))
                        .setParameter("i", issueEntity)
                        .setParameter(DATE_PARAM, date);
            } else {
                query = withSpd ? DeliveryFileEntity.queryDeliveryFiles(issueId, Constants.SPD_ISSUE_ID, manager)
                        : DeliveryFileEntity.queryDeliveryFiles(issueId, manager);
            }
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            return query.getResultList();

        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return Collections.emptyList();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public DeliveryFileEntity getDeliveryFileEntity(Integer deliveryFileEntityId) {
        if (deliveryFileEntityId != null) {
            return manager.find(DeliveryFileEntity.class, deliveryFileEntityId);
        }
        return null;
    }

    public void deleteDeliveryFileEntity(int deliveryFileEntityId) {
        manager.createNamedQuery("deleteDfById").setParameter(ID, deliveryFileEntityId).executeUpdate();
    }

    public void mergeDb(ClDbEntity db) {
        manager.merge(db);
        manager.flush();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ClDbEntity getDb(Integer clDbId) {
        if (clDbId != null) {
            return manager.find(ClDbEntity.class, clDbId);
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createDb(int issueId, String title, int priority) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity dbEntity;
        try {
            dbEntity = getClDbEntity(title, issueEntity);
            dbEntity.setApproved(false);
        } catch (Exception e) {
            dbEntity = createDb(issueEntity, getDatabaseEntity(title), priority);
        }
        return dbEntity.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ClDbEntity createDb(IssueEntity issueEntity, DatabaseEntity database, int priority) {

        ClDbEntity dbEntity = new ClDbEntity();

        dbEntity.setDatabase(database);
        dbEntity.setDate(new Date());
        dbEntity.setIssue(issueEntity);
        dbEntity.setPriority(priority);
        dbEntity.setInitialPackageDelivered(false);
        manager.persist(dbEntity);

        return dbEntity;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int findOpenDb(int issueId, String dbName) throws CmsException {
        return findClDbEntity(manager.find(IssueEntity.class, issueId), dbName).getId();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int findOpenDb(int year, int issueNumber, String dbName) throws CmsException {
        return findClDbEntity(getIssueByNumber(year, issueNumber), dbName).getId();
    }

    private ClDbEntity findClDbEntity(IssueEntity issue, String dbName) throws CmsException {
        if (issue == null) {
            throw new CmsException("issue is null");
        }
        ClDbEntity clDb = getClDbEntity(dbName, issue);
        if (clDb == null) {
            throw new CmsException(String.format("can't find clDb by issue: %d", issue.getFullNumber()));
        }
        if (clDb.isClearing()) {
            throw new CmsException(String.format("clDb %s [%d] is clearing", clDb.getTitle(), clDb.getId()));
        }
        return clDb;
    }

    public int findDb(int issueId, String title) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity db = getClDbEntity(title, issueEntity);
        return db != null ? db.getId() : DbEntity.NOT_EXIST_ID;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<ClDbEntity> getDbList(int issueId) {
        return ClDbEntity.queryClDb(issueId, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ClDbEntity> getDbList(Integer issueId, boolean withSpd) {
        return withSpd && issueId.equals(Constants.SPD_ISSUE_ID) ? getDbList(issueId)
                : ClDbEntity.queryClDb(issueId, Constants.SPD_ISSUE_ID, manager).getResultList();
    }

    public void setDbApproved(int clDbId, boolean approved) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, clDbId);
        clDbEntity.setApproved(approved);
        manager.flush();
    }

    public RenderingEntity getRendering(int id) {
        return manager.find(RenderingEntity.class, id);
    }

    @SuppressWarnings("unchecked")
    public List<RenderingEntity> getRenderingList(int recordId) {
        //RecordEntity recordEntity = manager.find(RecordEntity.class, recordId);
        List<RenderingEntity> recordRenderings = new ArrayList<>();
        try {
            recordRenderings = manager
                    .createNamedQuery("findRenderingByOrder")
                    .setParameter(RECORD, recordId)
                    .getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return recordRenderings;
    }

    public void mergeRendering(RenderingEntity rendering) {
        //manager.getReference(RenderingEntity.class,rendering);
        manager.merge(rendering);
        manager.flush();
    }

    public void mergeRecord(RecordEntity record) {
        manager.merge(record);
        manager.flush();
    }

    public void mergeRecord(RecordEntity record, String title) {
        TitleEntity ute = TitleEntity.checkEntity(title, record.getTitleEntity(), manager);
        record.setTitleEntity(ute);
        //record.setUnitTitle(title);
        mergeRecord(record);
    }

    public void mergeRecord(RecordWrapper recordWrapper) {
        RecordEntity record = manager.find(RecordEntity.class, recordWrapper.getId());

        ClDbEntity db = manager.find(ClDbEntity.class, recordWrapper.getDb().getId());
        record.setDb(db);

        DeliveryFileEntity file = manager.find(DeliveryFileEntity.class, recordWrapper.getDeliveryFile().getId());
        record.setDeliveryFile(file);

        record.setApproved(recordWrapper.getApproved());
        record.setDisabled(recordWrapper.isDisabled());
        record.setName(recordWrapper.getName());
        record.setQasCompleted(recordWrapper.isQasCompleted());
        record.setQasSuccessful(recordWrapper.isQasSuccessful());
        record.setRejected(recordWrapper.getRejected());
        record.setStateDescription(recordWrapper.getStateDescription());
        record.setNotes(recordWrapper.getNotes());
        record.setRenderingCompleted(recordWrapper.isRenderingCompleted());
        record.setRenderingSuccessful(recordWrapper.isRenderingSuccessful());
        //record.setUnitTitle(recordWrapper.getUnitTitle());
        record.setTitleEntity(TitleEntity.checkEntity(recordWrapper.getUnitTitle(), record, manager));
        record.setRecordPath(recordWrapper.getRecordPath());
        manager.merge(record);
        manager.flush();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta findLatestMetadata(String cdNumber, boolean withHistory) {
        List<RecordMetadataEntity> rmEntities = withHistory
                ? RecordMetadataEntity.queryRecordMetadata(cdNumber, manager).getResultList()
                : RecordMetadataEntity.queryRecordMetadata(cdNumber, 0, 1, manager).getResultList();
        return findLatestMetadataWithHistory(rmEntities);
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ICDSRMeta> findAllMetadata(String dbName, int skip, int batchSize) {
        int[] range = RecordHelper.getRecordNumbersRange(dbName);
        return RecordMetadataEntity.queryRecordMetadata(range[0], range[1], skip, batchSize, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Map<String, ICDSRMeta> findLatestMetadata(Collection<String> cdNumbers, boolean withHistory) {
        List<RecordMetadataEntity> rmEntities = RecordMetadataEntity.queryRecordMetadata(
                cdNumbers, manager).getResultList();
        if (rmEntities.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ICDSRMeta> ret = new HashMap<>();
        CDSRMetaVO cur = null;
        CDSRMetaVO lastHistory = null;

        for (RecordMetadataEntity rm: rmEntities) {
            int historyNumber = rm.getHistoryNumber();
            if (cur == null) {
                if (historyNumber == RecordEntity.VERSION_LAST) {
                    cur = new CDSRMetaVO(rm);
                }
                continue;
            }

            int pub = rm.getPubNumber();
            String curCdNumber = cur.getCdNumber();
            if (!curCdNumber.equals(rm.getCdNumber())) {
                lastHistory = null;
                ret.put(curCdNumber, cur);
                if (historyNumber == RecordEntity.VERSION_LAST) {
                    cur = new CDSRMetaVO(rm);
                }

            } else if (pub < cur.getPubNumber()) {
                if (historyNumber == RecordEntity.VERSION_LAST || !withHistory) {
                    continue;
                }
                if (lastHistory == null) {
                    lastHistory = new CDSRMetaVO(rm);
                    cur.setHistory(lastHistory);

                } else if (pub < lastHistory.getPubNumber()) {
                    CDSRMetaVO history = new CDSRMetaVO(rm);
                    lastHistory.setHistory(history);
                    lastHistory = history;
                }

            } else if (pub == cur.getPubNumber() && rm.getIssue() == cur.getIssue() && rm.isJats() == cur.isJats()) {
                if (CmsUtils.isFirstMoreThanSecond(rm.getCochraneVersion(), cur.getCochraneVersion())) {
                    CDSRMetaVO meta = new CDSRMetaVO(rm);
                    meta.setHistory(cur.getHistory());
                    cur = meta;
                }
            }
        }
        if (cur != null) {
            ret.put(cur.getCdNumber(), cur);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta findMetadataToIssue(int issueNumber, String cdNumber, boolean isIssueEqual) {
        if (isIssueEqual) {
            return findLatestMetadataWithHistory(RecordMetadataEntity.queryRecordMetadataByIssue(
                    issueNumber, cdNumber, manager).getResultList());
        }
        List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadataByIssue(issueNumber, cdNumber, 1,
                manager).getResultList();
        return list.isEmpty() ? null : new CDSRMetaVO(list.get(0));
    }

    private ICDSRMeta findLatestMetadataWithHistory(List<RecordMetadataEntity> rmEntities) {
        if (rmEntities.isEmpty()) {
            return null;
        }
        RecordMetadataEntity meta = null;
        RecordMetadataEntity history = null;
        for (RecordMetadataEntity rm: rmEntities) {
            if (CmsUtils.isSpecialIssueNumber(rm.getIssue())) {
                continue;
            }
            int pub = rm.getPubNumber();
            if (meta == null || pub > meta.getPubNumber()) {
                history = meta;
                meta = rm;

            } else if (RevmanMetadataHelper.isFirstLatest(rm, meta)) {
                meta = rm;

            } else if (pub < meta.getPubNumber() && (history == null || pub > history.getPubNumber()
                    || RevmanMetadataHelper.isFirstLatest(rm, history))) {
                history = rm;
            }
        }
        ICDSRMeta ret = null;
        if (meta != null) {
            ret = new CDSRMetaVO(meta);
            ret.setHistory(history);
        }
        return ret;
    }

    private ICDSRMeta findLatestMetadata(List<? extends ICDSRMeta> metadata) {
        ICDSRMeta ret = null;
        for (ICDSRMeta meta: metadata) {
            if (ret == null || RevmanMetadataHelper.isFirstLatest(meta, ret)) {
                ret = meta;
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getOpenAccessCDSRNames(int issueNumber) {
        int[] range = RecordHelper.getRecordNumbersRange(CochraneCMSPropertyNames.getCDSRDbName());
        return (List<String>) RecordMetadataEntity.queryOpenAccessCdNumbers(range[0], range[1],
                issueNumber, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<RecordMetadataEntity> findRecordMetadata(String cdNumber) {
        return (List<RecordMetadataEntity>) RecordMetadataEntity.queryRecordMetadata(cdNumber, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta findPreviousMetadata(int issueNumber, String cdNumber, int pubNumber) {
        List<RecordMetadataEntity> rmEntities = RecordMetadataEntity.queryRecordMetadataHistory(issueNumber, cdNumber,
                pubNumber, manager).getResultList();
        return findLatestMetadata(rmEntities);
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta findPreviousMetadata(String cdNumber, Integer historyNumber) {
        List<RecordMetadataEntity> rmEntities = RecordMetadataEntity.queryRecordMetadataHistory(cdNumber,
                        historyNumber, manager).getResultList();
        if (rmEntities.isEmpty()) {
            return null;
        }
        return findLatestMetadata(rmEntities);
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta findLatestMetadata(String cdNumber, int pubNumber) {
        List<RecordMetadataEntity> rmEntities = RecordMetadataEntity.queryRecordMetadataHistory(
                cdNumber, pubNumber, manager).getResultList();
        return findLatestMetadataWithHistory(rmEntities);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta getMetadata(int recordId) {
        RecordEntity re = manager.find(RecordEntity.class, recordId);
        return  (re != null) ? re.getMetadata() : null;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<? extends ICDSRMeta> findLatestMetadataHistory(String cdNumber) {
        List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadata(cdNumber, manager).getResultList();

        list.sort((r1, r2) -> sortHistory(r1, r2));

        Iterator<RecordMetadataEntity> it = list.iterator();
        int pub = 0;
        while (it.hasNext()) {
            RecordMetadataEntity rm = it.next();
            if (rm.getPubNumber() == pub) {
                it.remove();
            } else {
                pub = rm.getPubNumber();
            }
        }
        return list;
    }

    private int sortHistory(RecordMetadataEntity r1, RecordMetadataEntity r2) {
        int ret = Integer.compare(r2.getPubNumber(), r1.getPubNumber());
        if (ret != 0) {
            return ret;
        }
        return RevmanMetadataHelper.isFirstLatest(r2, r1) ? 1 : -1;
    }

    private IssueEntity getIssueEntity(int issueId) throws CmsException {
        IssueEntity issue = getIssue(issueId);
        if (issue == null) {
            throw new CmsException(String.format("Can't find issue by issueId: %d", issueId));
        }
        return issue;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RecordMetadataEntity> findRecordsMetadata(int issueId, String dbName) throws CmsException {
        int[] range = RecordHelper.getRecordNumbersRange(dbName);
        IssueEntity issue = getIssueEntity(issueId);
        return (List<RecordMetadataEntity>) RecordMetadataEntity.queryRecordMetadataByIssue(range[0], range[1],
                CmsUtils.getIssueNumber(issue.getYear(), issue.getNumber()), manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ICDSRMeta getCDSRMetadata(String cdNumber, int pubNumber) {
        List<RecordMetadataEntity> entities = RecordMetadataEntity.queryRecordMetadata(
                cdNumber, pubNumber, manager).getResultList();
        return (entities.isEmpty() ? null : findLatestMetadata(entities));
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public RecordEntity getRecord(int recordId) {
        return manager.find(RecordEntity.class, recordId);
    }

    public void removeRecord(BaseType baseType, int recordId, boolean spd, boolean canceled) {
        RecordEntity record = manager.find(RecordEntity.class, recordId);
        removeRecord(baseType, record, spd, canceled);
    }

    private void removeRecord(BaseType baseType, RecordEntity r, boolean spd, boolean canceled) {
        int pubNumber = r.getMetadata() != null ? r.getMetadata().getVersion().getPubNumber() : Constants.FIRST_PUB;
        String cdNumber = r.getName();
        int recordNumber = baseType.getProductType().buildRecordNumber(cdNumber);
        Integer clDbId = r.getDb().getId();
        if (spd) {
            if (!canceled) {
                PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(cdNumber, clDbId, manager).executeUpdate();
            }
            deletePublishedRecords(PublishRecordEntity.queryPublishedRecordsByDb(
                    recordNumber, clDbId, manager).getResultList(), pubNumber);

            List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadataByIssue(
                    Constants.SPD_ISSUE_NUMBER, cdNumber, manager).getResultList();
            for (RecordMetadataEntity rm: list) {
                if (canceled) {
                    rm.getVersion().setHistoryNumber(RecordEntity.VERSION_SHADOW);

                } else {
                    manager.remove(rm);
                    manager.remove(rm.getVersion());
                }
            }
        } else {
            if (!baseType.isCentral()) {
                PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(cdNumber, clDbId, manager).executeUpdate();
            } else {
                DbRecordEntity.deleteRecordByDb(recordNumber, clDbId, manager);
            }
            deletePublishedRecords(PublishRecordEntity.queryPublishedRecordsByDb(
                    baseType.getProductType().buildRecordNumber(cdNumber),
                    baseType.getProductType().getSPDDbId(), manager).getResultList(), pubNumber);
        }
        manager.remove(r);
    }

    private void deletePublishedRecords(List<PublishRecordEntity> list, int pubNumber) {
        for (PublishRecordEntity pre: list) {
            if (pubNumber == pre.getPubNumber()) {
                manager.remove(pre);
            }
        }
    }

    public void clearRecordsRenderings(RecordEntity r) {
        manager.createQuery("update RenderingEntity r set "
                + "r.completed=false, "
                + "r.successful=false, "
                + "r.approved=false, "
                + "r.rejected=false "
                + "where r.record=:record")
                .setParameter(RECORD, r).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getApprovedRecordCount(int dbId) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        try {
            return getSingleResultIntValue(
                    manager.createNamedQuery("approvedRecordCount")
                            .setParameter("db", clDbEntity).setParameter("a", true));
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getRecordCount(int dbId) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        try {
            return getSingleResultIntValue(manager.createNamedQuery(RecordEntity.QUERY_RECORD_COUNT).setParameter("db",
                    clDbEntity));
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getRecordCount(int dbId, String name) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        try {
            return getSingleResultIntValue(manager.createNamedQuery(RecordEntity.QUERY_RECORD_COUNT_BY_NAME)
                    .setParameter("db", clDbEntity)
                    .setParameter(RECORD_NAME_PARAM, name));
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public List<RecordEntity> getRecords(int dbId) {
        return (List<RecordEntity>) RecordEntity.queryRecords(dbId, manager).getResultList();
    }

    public List<RecordEntity> getRecordsOrderById(int dbId, int startId) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        List<RecordEntity> records = null;
        try {
            records = manager
                    .createQuery("select r from RecordEntity r where r.db=:db and r.id >=:id order by r.id")
                    .setParameter("db", clDbEntity)
                    .setParameter("id", startId)
                    .getResultList();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return records;
    }

    public int getDeliveryFilePrevStatus(int pckId) {
        int id = -1;
        StatusEntity st = manager.find(DeliveryFileEntity.class, pckId).getInterimStatus();
        if (st != null) {
            id = st.getId();
        }
        return id;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getRejectedRecordCount(int dbId) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        try {
            return getSingleResultIntValue(manager.createNamedQuery("rejectedRecordCount").setParameter("db",
                    clDbEntity).setParameter("r", true));
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    public void createRecordsWithoutManifests(SortedMap<String, String> recs, int dbId, int deliveryFileId) {
        LOG.debug("createRecords start size = [" + recs.size() + "]");

        ClDbEntity dbEntity = manager.find(ClDbEntity.class, dbId);
        DeliveryFileEntity deliveryFileEntity = manager.find(DeliveryFileEntity.class, deliveryFileId);
        for (String recordName : recs.keySet()) {
            createOrUpdateRecord(dbEntity, recs.get(recordName), deliveryFileEntity, recordName);
        }

        LOG.debug(CREATE_RECORDS_FINISH);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @SuppressWarnings("unchecked")
    public void createRecords(Map<String, String> recs, int deliveryFileId,
        Set<String> recordsWithRawData, final Set<String> goods, Set<String> delNames, boolean isTa) {

        LOG.debug(String.format("create CDSR records start, all=%d, deleted=%d", goods.size(),
                (delNames == null ? 0 : delNames.size())));

        DeliveryFileEntity dfEntity = manager.find(DeliveryFileEntity.class, deliveryFileId);
        ClDbEntity dbEntity = dfEntity.getDb();
        int dbId = dbEntity.getId();

        List<RecordEntity> list = goods.isEmpty() ? Collections.EMPTY_LIST : (goods.size() > SAFE_RECORD_COUNT
            ? getRecords(dbId) : (List<RecordEntity>) RecordEntity.queryRecords(dbId, goods, manager).getResultList());

        boolean isSystemUpdate = dfEntity.isSystemUpdate();

        Set<String> checkNames = goods;
        if (!list.isEmpty()) {

            checkNames = new HashSet<>(goods.size());
            checkNames.addAll(goods);

            for (RecordEntity re: list) {

                String recordName = re.getName();

                if (!checkNames.contains(recordName)) {
                    continue;
                }

                if (delNames == null || !delNames.contains(recordName)) {

                    updateRecordEntity(re, dfEntity, recs.get(recordName),
                            recordsWithRawData.contains(recordName), !isTa && !isSystemUpdate);
                    re.setState(RecordEntity.STATE_PROCESSING);
                    manager.merge(re);
                }

                checkNames.remove(recordName);
            }
        }

        for (String recordName : checkNames) {

            if (delNames != null && delNames.contains(recordName)) {
                continue;
            }

            RecordEntity record = createRecord(recordName, dfEntity, dbEntity, recs.get(recordName),
                recordsWithRawData.contains(recordName), isTa);
            record.setState(RecordEntity.STATE_PROCESSING);
            manager.persist(record);
        }

        manager.flush();
        LOG.debug("create CDSR records finish");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createRecords(Map<String, IRecord> records, int dbId, int dfId) {
        ClDbEntity dbEntity = manager.find(ClDbEntity.class, dbId);
        BaseType bt = BaseType.find(dbEntity.getTitle()).get();

        long count = bt.isUniqueInIssueDb() ? 0 : (Long) manager.createNamedQuery(
                RecordEntity.QUERY_RECORD_COUNT).setParameter("db", dbEntity).getSingleResult();

        DeliveryFileEntity deliveryFileEntity = manager.find(DeliveryFileEntity.class, dfId);
        boolean isSystemUpdate = deliveryFileEntity.isSystemUpdate();

        for (Map.Entry<String, IRecord> entry : records.entrySet()) {
            String recordName = entry.getKey();
            IRecord record = entry.getValue();
            int id;
            if (count != 0) {
                id = createOrUpdateRecord(dbEntity, record.getRecordPath(), deliveryFileEntity, recordName,
                        record.isRawExist(), false, !isSystemUpdate);
            } else {
                RecordEntity re = createRecord(recordName, deliveryFileEntity, dbEntity,
                        record.getRecordPath(), record.isRawExist(), false);
                manager.persist(re);
                id = re.getId();
            }
            record.setId(id);
        }
    }

    public void createRecords(Map<String, String> recs, int dbId, int deliveryFile, Set<String> recordsWithRawData) {

        LOG.debug("createRecords start size=" + recs.size());
        ClDbEntity dbEntity = manager.find(ClDbEntity.class, dbId);
        BaseType bt = BaseType.find(dbEntity.getTitle()).get();

        long count = bt.isUniqueInIssueDb() ? 0 : (Long) manager.createNamedQuery(
                RecordEntity.QUERY_RECORD_COUNT).setParameter("db", dbEntity).getSingleResult();

        DeliveryFileEntity deliveryFileEntity = manager.find(DeliveryFileEntity.class, deliveryFile);
        boolean isSystemUpdate = deliveryFileEntity.isSystemUpdate();

        int j = 0;
        for (String recordName : recs.keySet()) {
            if (count != 0) {
                createOrUpdateRecord(dbEntity, recs.get(recordName), deliveryFileEntity, recordName,
                        containsRawData(recordName, recordsWithRawData), false, !isSystemUpdate);
            } else {
                RecordEntity record = createRecord(recordName, deliveryFileEntity, dbEntity,
                    recs.get(recordName), containsRawData(recordName, recordsWithRawData), false);
                manager.persist(record);
            }

            if (++j % DbConstants.DB_PACK_SIZE == 0) {
                manager.flush();
                LOG.debug("j=" + j);
            }
        }

        LOG.debug(CREATE_RECORDS_FINISH);
    }

    private boolean containsRawData(String recordName, Set<String> recordsWithRawData) {
        return recordsWithRawData != null && recordsWithRawData.contains(recordName);
    }

    private void createOrUpdateRecord(ClDbEntity db, String recordPath, DeliveryFileEntity deliveryFile, String name) {
        List<RecordEntity> recordList = getNotTempRecordList(db, name);
        if (recordList == null || recordList.size() == 0) {
            createRecord(name, deliveryFile, db, recordPath);
        } else {
            updateRecordEntity(recordList.get(0), deliveryFile, recordPath, false, true);
        }
    }

    private int createOrUpdateRecord(ClDbEntity db, String recordPath, DeliveryFileEntity deliveryFile,
        String name, boolean isRawDataExists, boolean isTa, boolean resetUnitStatus) {

        RecordEntity record = null;
        List<RecordEntity> recordList;
        //get not temporary record list
        recordList = getNotTempRecordList(db, name);
        int size = recordList.size();

        if (size == 0) {
            record = createRecord(name, deliveryFile, db, recordPath, isRawDataExists, isTa);
            manager.persist(record);

        } else {

            record = recordList.get(0);
            updateRecordEntity(record, deliveryFile, recordPath, isRawDataExists, resetUnitStatus);

            manager.merge(record);

            if (size > 1) {
                LOG.error("More 1 records (HO?) were found with name " + name);
            }
        }
        return record != null ? record.getId() : 0;
    }

    @SuppressWarnings("unchecked")
    private List<RecordEntity> getNotTempRecordList(ClDbEntity db, String name) {
        List<RecordEntity> recordList;
        recordList = manager
                .createNamedQuery(RecordEntity.QUERY_SELECT_BY_DB_AND_NAME)
                .setParameter("db", db.getId())
                .setParameter(RECORD_NAME_PARAM, name)
                .getResultList();
        return recordList;
    }

    private void createRecord(String name, DeliveryFileEntity deliveryFile, ClDbEntity db, String recordPath) {

        RecordEntity record = new RecordEntity();
        updateRecordEntity(name, deliveryFile, db, recordPath, false, record);
        manager.persist(record);
    }

    private RecordEntity createRecord(String name, DeliveryFileEntity deliveryFile, ClDbEntity db,
                                      String recordPath, boolean rawDataExists, boolean isTa) {

        RecordEntity record = new RecordEntity();

        updateRecordEntity(name, deliveryFile, db, recordPath, rawDataExists, record);

        if (isTa) {
            record.setUnitStatus(manager.find(UnitStatusEntity.class,
                    UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS));
        }
        return record;
    }

    private void updateRecordEntity(String name, DeliveryFileEntity deliveryFile, ClDbEntity db, String recordPath,
        boolean rawDataExists, RecordEntity record) {

        record.setName(name);
        record.setDeliveryFile(deliveryFile);
        record.setDb(db);
        record.setRecordPath(recordPath);
        record.setRawDataExists(rawDataExists);
    }

    private void updateRecordEntity(RecordEntity record, DeliveryFileEntity deliveryFile, String recordPath,
        boolean rawDataExists, boolean resetUnitStatus) {

        record.setDeliveryFile(deliveryFile);
        record.setRecordPath(recordPath);
        record.setRawDataExists(rawDataExists);
        record.setQasCompleted(false);
        record.setQasSuccessful(false);
        record.setRenderingCompleted(false);
        record.setRenderingSuccessful(false);
        record.setApproved(false);
        record.setRejected(false);
        record.setState(RecordEntity.STATE_UNDEFINED);
        //record.setUnitTitle("");
        record.setTitleEntity(null);
        if (resetUnitStatus) {
            record.setUnitStatus(null);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getStartedJobForPlan(Integer dfId, String plan) {
        int planId = (
                (RenderingPlanEntity) manager.createNamedQuery("rndPlan").setParameter("d",
                        plan).getSingleResult()).getId();
        return manager.createNamedQuery("startedJobIdForDfAndPlan")
                .setParameter("df", dfId)
                .setParameter("planId", planId)
                .getResultList();
    }

    public List<RecordLightVO> getRecordLightVOList(List<String> list, int issueId, String dbName) {
        if (list == null || list.size() < 1) {
            return null;
        }

        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity dbEntity = getClDbEntity(dbName, issueEntity);

        List<RecordLightVO> recordList = new ArrayList<RecordLightVO>(list.size());

        for (String recordName : list) {

            RecordEntity ent = getRecord(dbEntity, recordName);
            if (ent == null) {
                logNoRecordFound(issueEntity, dbEntity, recordName);
                continue;
            }

            recordList.add(new RecordLightVO(ent.getId(), recordName, ent.isQasSuccessful()));
        }

        return recordList;
    }

    public List<RecordLightVO> getRecordLightVOListForRecords(List<Record> records, int issueId, String dbName) {
        if (records == null || records.size() < 1) {
            return null;
        }

        IssueEntity issueEntity = manager.find(IssueEntity.class, issueId);
        ClDbEntity dbEntity = getClDbEntity(dbName, issueEntity);

        List<RecordLightVO> recordList = new ArrayList<RecordLightVO>(records.size());

        for (Record record : records) {
            RecordEntity ent = getRecord(dbEntity, record.getName());
            if (ent == null) {
                logNoRecordFound(issueEntity, dbEntity, record.getName());
                continue;
            }

            recordList.add(new RecordLightVO(ent.getId(), record.getName(), ent.isQasSuccessful(),
                    record.getQasErrorCause()));
        }

        return recordList;
    }

    public boolean existsDoubleRecord(int issueId, int dbId, String recordName) {
        boolean result;
        try {
            RecordEntity record = (RecordEntity) manager
                    .createNamedQuery(RecordEntity.QUERY_SELECT_BY_DB_AND_NAME)
                    .setParameter("db", dbId)
                    .setParameter(RECORD_NAME_PARAM, recordName)
                    .getSingleResult();
            result = false;
        } catch (NoResultException e) {
            result = false;
        } catch (NonUniqueResultException e) {
            result = true;
        } catch (Exception e) {
            LOG.debug(e, e);
            result = false;
        }
        return result;
    }

    public int findRecord(int dbId, String recordName) {
        int id = -1;
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);

        RecordEntity record = getRecord(db, recordName);

        if (record != null) {
            id = record.getId();
        }
        return id;
    }

    public RecordEntity getRecord(int dbId, String name) {
        return getRecordEntity(dbId, name, true);
    }

    private RecordEntity getRecordEntity(int dbId, String name, boolean logErr) {
        try {
            return (RecordEntity) RecordEntity.queryRecords(dbId, name, manager).getSingleResult();
        } catch (Exception e) {
            if (logErr) {
                LOG.error(e.getMessage());
            }
        }
        return null;
    }

    private RecordEntity getRecord(ClDbEntity db, String name) {
        return getRecordEntity(db.getId(), name, true);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public RecordEntity getRecord(int issue, String db, String name) {
        RecordEntity record = null;
        try {
            ClDbEntity dbEntity = getClDb(issue, db);
            record = getRecord(dbEntity, name);
        } catch (Exception e) {
            LOG.debug(e, e);
        }
        return record;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Record> getRecordsByDfAndNames(int dfId, Collection<String> names) {
        return (List<Record>) RecordEntity.queryRecordsByDf(dfId, names, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RecordEntity> getRecordsByDb(Integer dbId, Collection<String> cdNumbers) {
        return RecordEntity.queryRecords(dbId, cdNumbers, manager).getResultList();
    }

    private ClDbEntity getClDb(int issue, String db) {
        IssueEntity issueEntity = manager.find(IssueEntity.class, issue);
        return getClDbEntity(db, issueEntity);
    }

    public PublishTypeEntity getPublishType(String name) {

        //PublishTypeEntity publishTypeEntity;
        //synchronized (ResultsStorage.class) {
        //    try {
        //        publishTypeEntity = (PublishTypeEntity) PublishTypeEntity.queryPublishType(
        //                name, manager).getSingleResult();
                //publishTypeEntity = (PublishTypeEntity) manager
                //        .createQuery("select type from PublishTypeEntity type where type.name=:n")
                //        .setParameter("n", name)
                //        .getSingleResult();
        //    } catch (NoResultException e) {
        //        publishTypeEntity = new PublishTypeEntity();
        //        publishTypeEntity.setName(name);
        //        manager.persist(publishTypeEntity);
        //        manager.flush();
        //    }
        //}
        return (PublishTypeEntity) PublishTypeEntity.queryPublishType(name, manager).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity updatePublish(int publishId, boolean sending, boolean isSent, Date sendingDate, boolean wait) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        if (publish == null) {
            LOG.warn(String.format("publish entity [%d] is deleted or not exists", publishId));
            return null;
        }
        publish.setSending(sending);
        publish.setSendingDate(sendingDate);
        publish.setSent(isSent);
        publish.setWaiting(wait);
        manager.merge(publish);
        return publish;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity updatePublish(int publishId, String fileName, boolean generating, boolean isGenerated,
                              Date generationDate, boolean wait, long size) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setFileName(fileName);
        publish.setGenerating(generating);
        publish.setGenerated(isGenerated);
        publish.setGenerationDate(generationDate);
        publish.setWaiting(wait);
        publish.setFileSize(size);
        manager.merge(publish);
        return publish;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity updatePublishUnpacking(int publishId, boolean unpacking, Date unpackingDate, boolean waiting) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setUnpacking(unpacking);
        publish.setUnpackingDate(unpackingDate);
        publish.setWaiting(waiting);
        manager.merge(publish);
        return publish;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setStartSendingAndUnpackingDates(Date date, int publishId, boolean sending, boolean unpacking) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        if (publish == null) {
            return;
        }

        if (sending) {
            publish.setStartSendingDate(date);
        }
        if (unpacking) {
            publish.setStartUnpackingDate(date);
        }

        manager.merge(publish);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setDeleting(int publishId, boolean deleting, boolean waiting) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setDeleting(deleting);
        publish.setWaiting(waiting);
        manager.merge(publish);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity setGenerating(int publishId, boolean generating, boolean waiting, long size) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setGenerating(generating);
        publish.setWaiting(waiting);
        publish.setFileSize(size);
        manager.merge(publish);
        return publish;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity setSending(int publishId, boolean sending, boolean waiting, boolean onSentFailed) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setSending(sending);
        publish.setWaiting(waiting);
        if (sending) {
            publish.setStartSendingDate(new Date());
            publish.setSendingDate(null);

        } else if (onSentFailed) {
            publish.setSent(false);
        }

        manager.merge(publish);
        return publish;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public PublishEntity setUnpacking(int publishId, boolean unpacking, boolean waiting) {
        PublishEntity publish = manager.find(PublishEntity.class, publishId);
        publish.setUnpacking(unpacking);
        publish.setWaiting(waiting);
        if (unpacking) {
            publish.setStartUnpackingDate(new Date());
        }
        manager.merge(publish);
        return publish;
    }

    public boolean isGenerating(int dbId, String type) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        PublishEntity entity;
        try {
            entity = findPublishByDbAndType(db, PublishTypeEntity.getNamedEntityId(type));
        } catch (NoResultException e) {
            return false;
        }
        return entity.isGenerating();
    }

    public boolean isDeleting(int dbId, String type) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        PublishEntity entity;
        try {
            entity = findPublishByDbAndType(db, PublishTypeEntity.getNamedEntityId(type));
        } catch (NoResultException e) {
            return false;
        }
        return entity.isDeleting();
    }

    public String getGenerationDate(int dbId, String type) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        PublishEntity entity;
        try {
            entity = findPublishByDbAndType(db, PublishTypeEntity.getNamedEntityId(type));
        } catch (NoResultException e) {
            return null;
        }

        return entity.getGenerationDate() == null ? null : DATE_FORMAT.format(entity.getGenerationDate());
    }

    public boolean isSending(int dbId, String type) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        PublishEntity entity;
        try {
            entity = findPublishByDbAndType(db, PublishTypeEntity.getNamedEntityId(type));
        } catch (NoResultException e) {
            return false;
        }
        return entity.isSending();
    }

    public String getSendingDate(int dbId, String type) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        PublishEntity entity;
        try {
            entity = findPublishByDbAndType(db, PublishTypeEntity.getNamedEntityId(type));
        } catch (NoResultException e) {
            return null;
        }

        return entity.getSendingDate() == null ? null : DATE_FORMAT.format(entity.getSendingDate());
    }

    public List<PublishEntity> findPublish(int dbId, String type) {
        return findPublishesByDbAndType(dbId, PublishTypeEntity.getNamedEntityId(type));
    }

    public PublishEntity findPublish(int publishId) {
        return manager.find(PublishEntity.class, publishId);
    }

    @Deprecated
    public Date getLastPublishedDateByRecordNameAndPublishType(String recordName, String publishType) {
        try {
            return (Date) manager.createNamedQuery("getLastPublishedDateByRecordNameAndPublishType")
                    .setParameter(RECORD_NAME_PARAM, recordName)
                    .setParameter("publishType", publishType)
                    .setMaxResults(1).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public RecordMetadataEntity findRecordMetadataForEDIAndCCAFirstOnline(BaseType bt, String cdNumber) {
        List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadataForEDIAndCCAFirstOnline(
                cdNumber, manager).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Date[] getLastPublishedCCADate(String recordName) {
        if (EntireDBEntity.queryRecordsByName(DatabaseEntity.CCA_KEY, recordName, manager).getResultList().isEmpty()) {
            return null;
        }
        return findPublishedCCADates(recordName);
    }

    private Date[] findPublishedCCADates(String recordName) {
        List<RecordMetadataEntity> prevList = RecordMetadataEntity.queryRecordMetadata(
                recordName, Constants.FIRST_PUB, manager).getResultList();
        prevList.removeIf(rme -> !rme.getVersion().isVersionFinal() || !rme.getVersion().isNewDoi());
        RecordMetadataEntity rme = prevList.isEmpty() ? null : prevList.get(0);
        if (rme != null) {
            return new Date[] {rme.getPublishedDate(), rme.getCitationLastChanged()};
        }
        List<CcaEntity> list = CcaEntity.queryFirstRecordByName(recordName, 1, manager).getResultList();
        return list.isEmpty() ? null : new Date[] {list.get(list.size() - 1).getDate(), null};
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updatePublishedCCA(Collection<Integer> ids, boolean success) {
        CcaEntity.querySetPublishingState(ids, success, manager).executeUpdate();
    }

    public List<PublishEntity> findPublishesByDbAndType(Integer dbId, Integer type) {
        return (List<PublishEntity>) PublishEntity.queryPublishEntityByDb(dbId, type, manager).getResultList();
    }

    private PublishEntity findPublishByDbAndType(ClDbEntity db, Integer type) throws NoResultException {

        List<PublishEntity> list = findPublishesByDbAndType(db.getId(), type);
        if (list.isEmpty()) {
            throw new NoResultException(String.format("%s [%d], type=%d", db.getTitle(), db.getId(), type));
        }

        return list.get(0);
    }

    public void createSearchQuery(String text, String area,
                                  String fileStatus, int systemStatus, Date date) {
        try {
            manager.createQuery("select sq from SearchQueryEntity sq where sq.text=:text "
                    + "and sq.area=:area and sq.fileStatus=:fileStatus and sq.systemStatus=:systemStatus")
                    .setParameter("text", text)
                    .setParameter("area", area)
                    .setParameter("fileStatus", fileStatus)
                    .setParameter("systemStatus", systemStatus)
                    .getSingleResult();
        } catch (Exception e) {
            SearchQueryEntity search = new SearchQueryEntity();

            search.setText(text);
            search.setArea(area);
            search.setFileStatus(fileStatus);
            search.setSystemStatus(systemStatus);
            search.setDate(date);

            manager.persist(search);
            manager.flush();
        }
    }

    @SuppressWarnings("unchecked")
    public List<SearchQueryEntity> getSearchQueryList(int beginIndex, int amount) {
        List<SearchQueryEntity> queryList = new ArrayList<SearchQueryEntity>();
        try {
            Query query = manager.createQuery("select sq from SearchQueryEntity sq order by sq.date desc");
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            queryList = query.getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return queryList;
    }

    public int getSearchQueryListCount() {
        try {
            Query query = manager.createQuery("select count(sq) from SearchQueryEntity sq");
            return ((Number) query.getSingleResult()).intValue();
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public List<ClDbEntity> listClDbEntityByIssueId(Integer issueId) {
        return manager.createQuery(
                "select clDbEntity from ClDbEntity as clDbEntity "
                        + "where clDbEntity.issue.id=:issueId")
                .setParameter("issueId", issueId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Integer> listIssueIdForArchiving(int liveCount) {
        List<Integer> listId = manager.createQuery(
                "select issueEntity.id from IssueEntity as issueEntity "
                        + "where (select count(i) from IssueEntity i)>:liveCount "
                        + "order by issueEntity.year DESC,issueEntity.number DESC")
                .setParameter("liveCount", (long) liveCount)
                .getResultList();

        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < listId.size() - liveCount; i++) {
            list.add(listId.get(i));
        }

        return list;
    }

    public List<DeliveryFileEntity> findNotCompletedPackage() {
        return manager.createNamedQuery("findNotCompletedPackage").getResultList();
    }

    public List<Object[]> getRecordsByDeliveryFile(int deliveryId) {
        return RecordEntity.queryRecordPaths(deliveryId, true, 0, 0, manager).getResultList();
    }

    public List<Integer> getNotCompletedJobId(int df) {
        return manager.createNamedQuery("notCompletedJobId")
                .setParameter("df", manager.find(DeliveryFileEntity.class, df))
                .getResultList();
    }

    public List<Integer> getNotCompletedQaJobId(Integer id) {
        return manager.createNamedQuery("notCompletedQaJobId")
                .setParameter("df", manager.find(DeliveryFileEntity.class, id))
                .getResultList();
    }

    public long findNotCompletedPackageByDb(int dbId) {
        return (Long) manager.createNamedQuery("findNotCompletedByDb")
                .setParameter(DB_ID, dbId)
                .getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public DeliveryFileEntity findLastPackageByIssueAndName(int issueId, String name) {

        List ret = manager.createNamedQuery("findLastDfByIssueAndName")
                .setParameter(ISSUE, issueId)
                .setParameter(NAME, name + "%")
                .setMaxResults(1).getResultList();

        return (ret.isEmpty()) ? null : (DeliveryFileEntity) ret.iterator().next();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DeliveryFileEntity findLastPackageByTypeAndName(int packageType, String packageName) {
        List<DeliveryFileEntity> ret = DeliveryFileEntity.queryDeliveryFile(
                packageType, packageName, manager).getResultList();
        return ret.isEmpty() ? null : ret.get(0);
    }

    public void deleteStartedJobId(int jobId) {
        manager.createNamedQuery("deleteStartedJobId")
                .setParameter(JOB_ID, jobId)
                .executeUpdate();
    }

    public void deleteStartedJobQaId(int jobId) {
        manager.createNamedQuery("deleteStartedJobQaId")
                .setParameter(JOB_ID, jobId)
                .executeUpdate();
    }

    public IssueEntity getLastApprovedDatabaseIssue(String dbName) {

        List<ClDbEntity> list = (List<ClDbEntity>) ClDbEntity.queryLastApprovedClDb(dbName, manager).getResultList();
        if (list.isEmpty()) {
            LOG.warn("no any database issues for " + dbName);
            return null;
        }

        int minAllowed = BaseType.find(dbName).get().getMinApproved();

        IssueEntity ret = null;
        for (ClDbEntity e: list) {

            if (e.getAllCount() <= 0) {
                continue;
            }

            int minCount = minAllowed;
            int realCount = getRecordCount(e.getId());
            if (realCount < minAllowed) {
                minCount = realCount;
            }

            int approvedRecordCount = getApprovedRecordCount(e.getId());
            if (approvedRecordCount < minCount) {
                continue;
            }

            if (getRejectedRecordCount(e.getId()) > 0) {
                continue;
            }

            ret = e.getIssue();
            break;
        }

        return ret;
    }

    public IssueEntity getLastNonPublishedIssue() {
        return (IssueEntity) manager
                .createNamedQuery("selectLastNonPublishedIssue")
                .setMaxResults(1)
                .getSingleResult();
    }

    public List<CcaCdsrDoiViewEntity> getCcaCdsrDoiViewByCdsrName(String cdsrName) {
        return manager.createNamedQuery("selectCcaByCdsrNane")
                .setParameter("cdsrName", cdsrName)
                .getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String getRecordTitle(Integer titleId) {
        TitleEntity te = DbUtils.exists(titleId) && !TitleEntity.EMPTY_TITLE_ID.equals(titleId)
                ?  manager.find(TitleEntity.class, titleId) : null;
        return te != null ? CmsUtils.unescapeEntities(te.getTitle()) : null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public OpStats getPublishStatsOnSent(Integer publishId, Integer dbId, Collection<Integer> pubTypesIds) {
        OpStats ret = new OpStats(PublishRecordEntity.queryCount(publishId, manager).getResultList());
        ret.getMultiCounters().forEach((dfId, v) -> v.setTotal(getSingleResultIntValue(
                            PublishRecordEntity.queryCountSentOrFailedByDf(dbId, dfId, pubTypesIds, manager))));
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void getTotalPublishStatsOnPublished(Integer dbId, Collection<Integer> publishTypesIds, OpStats stats) {
        stats.getMultiCounters().forEach((dfId, v) -> v.setTotal(getSingleResultIntValue(
                PublishRecordEntity.queryCountHandledByDf(dbId, dfId, publishTypesIds, manager))));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public OpStats getPublishStatsOnOffline(BaseType baseType, Collection<Integer> recordNumbers) {
        OpStats stats = new OpStats();
        int dbType = baseType.getDbId();
        if (DbUtils.isOneCommit(recordNumbers.size())) {
            addToOpStats(DbRecordEntity.queryRecords(dbType, recordNumbers, AbstractRecord.STATUS_DELETED,
                    RecordEntity.VERSION_LAST, manager).getResultList(), recordNumbers, stats);
        }  else {
            List<Integer> all =  new ArrayList<>(recordNumbers);
            DbUtils.commitListByIds(all, (list) -> addToOpStats(DbRecordEntity.queryRecords(dbType, list,
                AbstractRecord.STATUS_DELETED, RecordEntity.VERSION_LAST, manager).getResultList(),
                    recordNumbers, stats));
        }
        stats.getMultiCounters().forEach((dfId, st) -> st.setTotal(getDeletedRecordsCount(baseType, dfId)));
        return stats;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getDeletedRecordsCount(BaseType baseType, int dfId) {
        return getSingleResultIntValue(DbRecordEntity.queryRecordsCount(baseType.getDbId(), dfId,
                AbstractRecord.STATUS_DELETED, RecordEntity.VERSION_LAST, manager));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Collection<String> getDeletedRecordNames(BaseType baseType, int dfId) {
        List<DbRecordVO> list = DbRecordEntity.queryRecords(baseType.getDbId(), dfId,
                AbstractRecord.STATUS_DELETED, RecordEntity.VERSION_LAST, manager).getResultList();
        Set<String> ret = new HashSet<>();
        list.forEach(r -> ret.add(r.getLabel()));
        return ret;
    }

    private void addToOpStats(List<DbRecordVO> list, Collection<Integer> offlineRecordNumbers, OpStats stats) {
        for (DbRecordVO rec: list) {
            if (offlineRecordNumbers.remove(rec.getNumber())) {
                stats.addTotalCompletedByKey(rec.getDfId(), rec.getNumber());
            }
        }
    }

    private ClDbEntity getClDbEntity(String dbName, IssueEntity issue) {
        return (ClDbEntity) manager.createNamedQuery("dbByTitleAndIssue")
                .setParameter("db", dbName)
                .setParameter(ISSUE, issue).getSingleResult();   
    }

    private void logNoRecordFound(IssueEntity issueEntity, ClDbEntity dbEntity, String recordName) {
        LOG.error("Not found recordEntity for issue:" + issueEntity.getTitle()
                + " db: " + dbEntity.getTitle()
                + " recordName:" + recordName);
    }
}