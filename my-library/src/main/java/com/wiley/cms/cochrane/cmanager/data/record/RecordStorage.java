package com.wiley.cms.cochrane.cmanager.data.record;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ejb.EJBException;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.commons.lang.StringEscapeUtils;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DbRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.JobQasResultEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.IRenderingStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Stateless
@Local(IRecordStorage.class)
public class RecordStorage implements IRecordStorage {
    private static final Logger LOG = Logger.getLogger(RecordStorage.class);

    private static final int FOUR = 4;
    private static final int TEN = 10;
    private static final MessageFormat QUERY_TEMPLATE;
    private static final MessageFormat SELECT_RECORD_ENTRY_QT;
    private static final MessageFormat RECORD_ENTRY_CONDITION_TEMPLATE;
    private static final String SQT0;
    private static final String SQT1;
    private static final String IDS_PARAM = "ids";
    private static final String DB_PARAM = "db";
    private static final String UNIT_STATUS_LIST_PARAM = "unitStatusList";
    private static final String DB_NAME_PARAM = "dbName";
    private static final String ISSUE_ID_PARAM = "issueId";
    private static final String DB_ID_PARAM = "dbId";
    private static final String DELIVERY_FILE_PARAM = "deliveryFile";
    private static final String RECORD_NAME_PARAM = "recordName";
    private static final String SUCCESS_PARAM = "success";
    private static final String RECORD_COUNT_BY_D_FILE = "recordCountByDFile";
    private static final String DESC_ORDER = " desc";
    private static final String ZERO_ARG = "{0}";
    private static final String DELIVERY_FILE_ID = "deliveryFileId";
    private static final String STATUS = "status";
    private static final String RECORDS = "records";

    @PersistenceContext
    private EntityManager manager;

    static {
        StringBuilder sb = new StringBuilder()
                .append(" select new com.wiley.cms.cochrane.cmanager.data.record.CDSRVO4Entire(en)")
                .append(" from EntireDBEntity en")
                .append(" where pr.recordName=en.name and en.lastIssuePublished in (")
                .append(ZERO_ARG)
                .append(")")
                .append(" {1} {2}")
                .append(" order by en.titleEntity.title, en.lastIssuePublished desc");
        QUERY_TEMPLATE = new MessageFormat(sb.toString());

        StringBuilder sb0 = new StringBuilder();
        SQT0 = sb0.append(" (SELECT MAX(lastIssuePublished)")
                .append(" FROM EntireDBEntity en1 ")
                .append(" WHERE en.name = en1.name)").toString();

        StringBuilder sb1 = new StringBuilder("and (en.titleEntity.title like '0%'");
        for (int i = 1; i < TEN; i++) {
            sb1.append(" or en.titleEntity.title like '" + i + "%'");
        }
        SQT1 = sb1.append(")").toString();

        StringBuilder sbRecordEntity = new StringBuilder()
                .append("select r from RecordEntity r where ")
                .append("r.disabled=false ")
                .append("and r.db=:db ")
                .append(ZERO_ARG)
                .append("order by r.titleEntity.title");

        SELECT_RECORD_ENTRY_QT = new MessageFormat(sbRecordEntity.toString());

        StringBuilder sbRecordEntityCondition = new StringBuilder()
                .append(ZERO_ARG)
                .append(" where r.db=:db ")
                .append("and r.name in({1}) ")
                .append("{2} ")
                .append(" and (select count(rnd.id) from RenderingEntity rnd ")
                .append(" where rnd.record.id=r.id and rnd.successful=true)")
                .append("{3} {4}");

        RECORD_ENTRY_CONDITION_TEMPLATE = new MessageFormat(sbRecordEntityCondition.toString());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void remove(int dbId, String recordName) {
        List<RecordEntity> result = RecordEntity.queryRecords(dbId, recordName, manager).getResultList();
        IRenderingStorage rs = RenderingStorageFactory.getFactory().getInstance();

        for (RecordEntity record : result) {
            List<RenderingEntity> renderings = rs.findRenderingsByRecord(record);
            for (RenderingEntity rendering : renderings) {
                manager.remove(rendering);
            }
            List<JobQasResultEntity> qas =  manager.createNamedQuery(
                    "getQasResultEntityByRecord").setParameter("id", record.getId()).getResultList();
            for (JobQasResultEntity qa : qas) {
                manager.remove(qa);
            }
            manager.flush();
            manager.createNamedQuery("deleteDbRecordPublishByRecord").setParameter("record", record).executeUpdate();
            manager.remove(record);
        }
        manager.flush();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean isRecordExists(int dbId, String recordName) {
        return !RecordEntity.queryRecords(dbId, recordName, manager).getResultList().isEmpty();
    }

    private RecordEntity getRecord(int dbId, String recordName) {
        List list = RecordEntity.queryRecords(dbId, recordName, manager).getResultList();
        return list.isEmpty() ? null : (RecordEntity) list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public RecordEntity getRecordEntityById(int id) {
        return manager.find(RecordEntity.class, id);
    }

    public RecordEntity getEditorialRecordEntityById(int recordId) {
        try {
            return (RecordEntity) RecordEntity.queryEditorialRecordById(recordId, manager).getSingleResult();
        } catch (Exception e) {
            LOG.debug(e, e);
        }
        return null;
    }

    @Deprecated
    public List<RecordVO> getRecordsSuccessfulRenderingByName(String name) {
        String query = "SELECT r FROM RecordEntity r WHERE r.name = :recordName"
                + " AND r.qasSuccessful = true AND r.renderingSuccessful = true";
        List<RecordEntity> entities = manager.createQuery(query).setParameter(RECORD_NAME_PARAM, name).getResultList();
        List<RecordVO> recordVOs = new ArrayList<RecordVO>(entities.size());
        for (RecordEntity entity : entities) {
            recordVOs.add(new RecordVO(entity));
        }
        return recordVOs;
    }

    public List<RecordVO> getRecordVOsByIds(Collection<Integer> ids, boolean check4Ta) {
        List<RecordEntity> entities = getRecordEntitiesByIds(ids);
        List<RecordVO> vos = new ArrayList<>(entities.size());
        for (RecordEntity entity : entities) {
            RecordVO rvo = new RecordVO(entity);
            vos.add(rvo);
            if (check4Ta) {
                DeliveryFileEntity de = entity.getDeliveryFile();
                if (DeliveryFileEntity.isRevmanOrJats(de.getType())) {
                    rvo.setInsertTa(ITranslatedAbstractsInserter.INSERT_ISSUE);
                }
            }
        }
        return vos;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<IRecord> getTinyRecords(Collection<Integer> ids) {
        return RecordEntity.queryTinyRecords(ids, manager).getResultList();
    }

    public List<RecordEntity> getEditorialRecordsByIssue(int issueId, int size, int offset) {
        return RecordEntity.queryEditorialRecordsByIssue(
                issueId, manager).setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RecordVO> getRecordVOsByDF(Collection<Integer> dfIds) {
        return RecordEntity.queryRecords(dfIds, manager).getResultList();
    }

    public List<RecordVO> getDbRecordVOByFirstCharList(int dbId, char ch, int productSubtitle) {
        return createVOList(getDbRecordEntityByFirstCharList(dbId, ch, productSubtitle));
    }

    public List<RecordVO> getDbRecordVOByNumStartedList(int dbId, int productSubtitle) {
        return createVOList(getDbRecordEntityByNumStartedList(dbId, productSubtitle));
    }

    private List<RecordVO> createVOList(List<RecordEntity> inList) {
        List<RecordVO> outList = new ArrayList<>(inList.size());
        inList.forEach(entity -> outList.add(new RecordVO(entity)));
        return outList;
    }

    private List<ProductSubtitleVO> createProductSubtitleVOList(List<ProductSubtitleEntity> inList) {
        List<ProductSubtitleVO> outList = new ArrayList<>(inList.size());
        inList.forEach(entity -> outList.add(new ProductSubtitleVO(entity)));
        return outList;
    }
        
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<CDSRVO4Entire> getRecordsByUnitStatusesFromEntire(int[] unitStatuses, int curIss, int prevIss) {
        String addUnitStatuses = "";
        if (unitStatuses != null && unitStatuses.length > 0) {
            addUnitStatuses = " and (";
            for (int unitStatus : unitStatuses) {
                addUnitStatuses += "en.unitStatus='" + unitStatus + "' or ";
            }
            addUnitStatuses = addUnitStatuses.substring(0, addUnitStatuses.length() - FOUR);
            addUnitStatuses += ") ";
        }
        Object[] args = new Object[]{"" + curIss + (prevIss > 0 ? (", " + prevIss) : ""), addUnitStatuses, ""};
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));
        return query.getResultList();
    }

    public List<CDSRVO> getCDSRVOListByGroup4Entire(String group) {
        Object[] args = group == null ? new Object[]{SQT0, "", ""}
                : new Object[]{SQT0, " and pr.groupName = '" + group + "'", ""};
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));
        return query.getResultList();
    }
        
    public List<RecordEntity> getDbRecordEntityByFirstCharList(int dbId, char ch, int productSubtitle) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        String addQuery = buildAddQuery(productSubtitle, "r");
        return manager.createQuery(SELECT_RECORD_ENTRY_QT.format(
                new Object[]{"and r.titleEntity.title like '" + ch + "%" + "' " + addQuery}))
                .setParameter(DB_PARAM, db).getResultList();
    }

    public List<CDSRVO> getCDSREntityByFirstCharList4Entire(char ch, int productSubtitle) {
        String addQuery = buildAddQuery(productSubtitle, "en");
        Object[] args = new Object[]{SQT0, " and en.titleEntity.title like '" + ch + "%' ", addQuery};
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));
        return query.getResultList();
    }

    public List<CDSRVO> getCDSRVOByNumStartedList4Entire(int productSubtitle) {
        String addQuery = buildAddQuery(productSubtitle, "en");
        Object[] args = new Object[]{SQT0, SQT1, addQuery};
        Query query = manager.createQuery(QUERY_TEMPLATE.format(args));
        return query.getResultList();
    }

    public List<RecordEntity> getDbRecordEntityByNumStartedList(int dbId, int subtitle) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        Object[] args = new Object[]{"and (r.titleEntity.title like '0%' or r.unitTitle like '1%'"
                + " or r.unitTitle like '2%' or r.titleEntity.title like '3%' or r.unitTitle like '4%'"
                + " or r.unitTitle like '5%' or r.titleEntity.title like '6%' or r.unitTitle like '7%'"
                + " or r.unitTitle like '8%' or r.titleEntity.title like '9%') " + buildAddQuery(subtitle, "r")
        };
        return manager.createQuery(SELECT_RECORD_ENTRY_QT.format(args))
                .setParameter(DB_PARAM, db).getResultList();
    }

    public List<RecordEntity> getRecordEntitiesByIds(Collection<Integer> ids) {
        return manager.createNamedQuery("recordByIds").setParameter(IDS_PARAM, ids).getResultList();
    }

    public List<Integer> getDbRecordIdList(int dbId, String[] items, int searchStatus, int offset, int limit) {
        ClDbEntity db = manager.find(ClDbEntity.class, dbId);
        String addQuery = extendSearchRecordQueryString(items, searchStatus, 0, false, null, null, false);
        Query q = manager.createQuery("select r.id from RecordEntity r where r.db=:db " + addQuery)
                .setParameter(DB_PARAM, db);
        if (offset > 0) {
            q.setFirstResult(offset);
        }
        if (limit > 0) {
            q.setMaxResults(limit);
        }
        return q.getResultList();
    }

    public List<RecordEntity> getDFRecordList(int dfId, int beginIndex, int amount, int orderField, boolean orderDesc) {
        DeliveryFileEntity df = manager.find(DeliveryFileEntity.class, dfId);
        List<RecordEntity> records = new ArrayList<>();
        try {
            String addQuery = extendSearchRecordQueryString(null, 0, orderField, orderDesc, null, null, false);
            Query query = manager.createQuery("select r from RecordEntity r where r.deliveryFile=:df" + addQuery)
                    .setParameter("df", df);
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            records = query.getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return records;
    }

    public List<RecordVO> getDbRecordVOList(int dbId) {
        return createVOList(getDbRecordList(dbId, 0, 0, null, 0, null, SearchRecordOrder.UNIT_TITLE, false));
    }

    public List<String> getRecordNamesByIds(List<Integer> ids) {
        return RecordEntity.queryRecordNamesByIds(ids, manager).getResultList();
    }

    public List<Integer> getRecordIds(Collection<Integer> ids, int state) {
        return RecordEntity.queryRecordIds(ids, state, manager).getResultList();
    }

    public String[] getDbRecordListNames(int dbId, int beginIndex, int amount, String[] items, int search,
                                         String fileStatus, int orderField, boolean orderDesc) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        List<String> records = new ArrayList<>();
        try {
            String addQuery = extendSearchRecordQueryString(items, search, orderField, orderDesc, null, fileStatus,
                    false);
            Query query = manager.createQuery("select r.name from RecordEntity r where r.db=:d"
                    + addQuery).setParameter("d", clDbEntity);
            if (fileStatus != null && !fileStatus.isEmpty()) {
                query = query.setParameter(UNIT_STATUS_LIST_PARAM, getUnitStatusList(fileStatus));
            }
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            records = query.getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return records.toArray(new String[records.size()]);
    }

    public List<RecordEntity> getDbRecordList(int dbId, int beginIndex, int amount, String[] items,
                                              int searchStatus, String fileStatus, int orderField, boolean orderDesc) {
        return getDbRecordList(dbId, beginIndex, amount, items, searchStatus, fileStatus, orderField, orderDesc, null);
    }

    public List<RecordEntity> getDbRecordList(int dbId, int dfId, int beginIndex, int amount, String[] items,
        int search, String fileStatus, int orderField, boolean orderDesc, String text) {

        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        List<RecordEntity> records = new ArrayList<>();
        try {
            String addQuery = extendSearchRecordQueryString(items, search, orderField, orderDesc, text, fileStatus,
                    false);
            Query query = manager.createQuery("select r from RecordEntity r where r.db=:d and r.deliveryFile.id=:dfId"
                    + addQuery).setParameter("d", clDbEntity).setParameter(RecordEntity.PARAM_DF_ID, dfId);
            if (fileStatus != null && !fileStatus.isEmpty()) {
                query = query.setParameter(UNIT_STATUS_LIST_PARAM, getUnitStatusList(fileStatus));
            }
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            records = query.getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return records;
    }

    public List<RecordEntity> getDbRecordList(int dbId, int beginIndex, int amount, String[] items,
        int search, String fileStatus, int orderField, boolean orderDesc, String text) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        List<RecordEntity> records = new ArrayList<>();
        try {
            String addQuery = extendSearchRecordQueryString(items, search, orderField, orderDesc, text, fileStatus,
                    BaseType.find(clDbEntity.getTitle()).get().isCentral());
            Query query = manager.createQuery("select r from RecordEntity r where r.db=:d" + addQuery)
                    .setParameter("d", clDbEntity);
            if (fileStatus != null && !fileStatus.isEmpty()) {
                query = query.setParameter(UNIT_STATUS_LIST_PARAM, getUnitStatusList(fileStatus));
            }
            if (amount > 0) {
                query = query.setFirstResult(beginIndex).setMaxResults(amount);
            }
            records = query.getResultList();
        } catch (Exception e) {
            LOG.debug(e.getMessage());
        }
        return records;
    }

    private String extendSearchRecordQueryString(String[] items, int searchStatus, int orderField, boolean orderDesc,
                                                 String text, String fileStatus, boolean central) {
        String addQuery = "";
        if (fileStatus != null && fileStatus.length() > 0) {
            addQuery += " and r.unitStatus in (:unitStatusList)";
        }
        if (text != null && text.length() > 0) {

            String title = StringEscapeUtils.escapeSql(text.replaceAll("(\\*){1,}", "%"));
            addQuery += " and r.name like('" + title + "')";
        }
        if (items != null && items.length > 0) {
            addQuery += getInToken(items);
        }
        if (searchStatus > 0) {
            addQuery += getSearchRecordsQueryParameters(searchStatus);
        }
        if (orderField > 0) {
            addQuery += getSearchRecordsOrder(orderField, orderDesc, central);
        }
        return addQuery;
    }

    private String getSearchRecordsOrder(int orderField, boolean orderDesc, boolean central) {
        String addQuery = "";
        switch (orderField) {
            case SearchRecordOrder.NAME:
                addQuery += " order by r.name";
                if (orderDesc) {
                    addQuery += DESC_ORDER;
                }
                break;
            case SearchRecordOrder.UNIT_TITLE:
                addQuery += " order by r.titleEntity.title";
                if (orderDesc) {
                    addQuery += DESC_ORDER;
                }
                break;
            case SearchRecordOrder.STATUS:
                addQuery += central ? (orderDesc ? " order by r.state desc, r.approved desc, r.rejected desc"
                    : " order by r.approved, r.rejected, r.state") : (orderDesc
                        ? " order by r.approved desc, r.rejected desc" : " order by r.approved, r.rejected");
                break;
            case SearchRecordOrder.STATE:
                addQuery += orderDesc ? " order by r.state desc" : " order by r.state";
                break;
            case SearchRecordOrder.SPD:
                addQuery += orderDesc ? " order by r.metadata.publishedDate desc"
                        : " order by r.metadata.publishedDate";
                break;
            default:
                addQuery = "";
        }
        return addQuery;
    }

    private String getSearchRecordsQueryParameters(int searchStatus) {
        String addQuery = " and r.disabled=false";
        switch (searchStatus) {
            case SearchRecordStatus.QA_PASSED:
                addQuery += " and r.qasSuccessful=true";
                break;
            case SearchRecordStatus.QA_FAILED:
                addQuery += " and r.qasSuccessful=false";
                break;
            case SearchRecordStatus.RENDER_PASSED:
                addQuery += " and r.renderingSuccessful=true";
                break;
            case SearchRecordStatus.RENDER_FAILED:
                addQuery += " and r.renderingSuccessful=false";
                break;
            case SearchRecordStatus.APPROVED:
                addQuery += " and r.approved=true";
                break;
            case SearchRecordStatus.UNAPPROVED:
                addQuery += " and r.approved=false";
                break;
            case SearchRecordStatus.REJECTED:
                addQuery += " and r.rejected=true";
                break;
            case SearchRecordStatus.WHEN_READY_PUBLISHING:
                addQuery += " and r.state in(" + RecordEntity.STATE_WR_PUBLISHING + ","
                        + RecordEntity.STATE_HW_PUBLISHING + "," + RecordEntity.STATE_DS_PUBLISHING + ","
                        + RecordEntity.STATE_CCH_PUBLISHING + ")";
                break;
            case SearchRecordStatus.CCH_PUBLISHING:
                addQuery += " and r.state=" + RecordEntity.STATE_CCH_PUBLISHING;
                break;
            case SearchRecordStatus.DS_PUBLISHING:
                addQuery += " and  r.state=" + RecordEntity.STATE_DS_PUBLISHING;
                break;
            case SearchRecordStatus.HW_PUBLISHING:
                addQuery += " and  r.state in(" + RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION + ","
                    + RecordEntity.STATE_WR_PUBLISHING + "," + RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION + ")";
                break;
            case SearchRecordStatus.HW_FAILURE:
                addQuery += " and  r.state =" + RecordEntity.STATE_HW_PUBLISHING_ERR;
                break;

            default:
                addQuery = "";
        }
        return addQuery;
    }

    private String getInToken(String[] items) {
        if (items.length == 1 && (items[0] == null || items[0].trim().length() == 0)) {
            return "";
        }

        String addQuery = " and r.name in(";
        for (String name : items) {
            addQuery += "'" + StringEscapeUtils.escapeSql(name) + "'" + ", ";
        }
        return addQuery.substring(0, addQuery.lastIndexOf(", ")) + ")";
    }

    public List<RecordLightVO> getRecordLightVOList(int dbId, String[] items, int searchStatus) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        String addQuery = extendSearchRecordQueryString(items, searchStatus, 0, false, null, null, false);
        List<RecordLightVO> recordList = new ArrayList<RecordLightVO>();
        try {
            Query query = manager.createQuery("select r from RecordEntity as r where r.db=:d" + addQuery)
                    .setParameter("d", clDbEntity);
            List<RecordEntity> recordEntityList = query.getResultList();
            if (recordEntityList != null) {
                for (RecordEntity record : recordEntityList) {
                    recordList.add(new RecordLightVO(record.getId(), record.getName(), true));
                }
            }
        } catch (Exception e) {
            LOG.debug(e, e);
        }
        return recordList;
    }

    public void updateRecordList(int dbId, String[] items, boolean approved, boolean rejected, int searchStatus) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        String addQuery = extendSearchRecordQueryString(items, searchStatus, 0, false, null, null, false);
        try {
            Query query = manager.createQuery("update RecordEntity r set " + "r.rejected = "
                    + Boolean.toString(rejected) + ", " + "r.approved = " + Boolean.toString(approved)
                    + " where r.db=:d" + addQuery).setParameter("d", clDbEntity);
            query.executeUpdate();
        } catch (Exception e) {
            LOG.debug(e, e);
        }
    }

    public int getDbRecordListCount(int dbId, String[] items, int searchStatus) {
        return getDbRecordListCount(dbId, items, searchStatus, null);
    }

    public int getDbRecordListCount(int dbId, String[] items, int searchStatus, String fileStatus) {
        return getDbRecordListCount(dbId, items, searchStatus, null, fileStatus);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getDbRecordListCount(int dbId, String[] items, int searchStatus, String text, String fileStatus) {
        ClDbEntity clDbEntity = manager.find(ClDbEntity.class, dbId);
        try {
            String addQuery = extendSearchRecordQueryString(items, searchStatus, 0, false, text, fileStatus, false);
            Query query = manager.createQuery("select count(r.id) from RecordEntity r where r.db=:d" + addQuery)
                    .setParameter("d", clDbEntity);
            if (fileStatus != null && fileStatus.length() > 0) {
                query = query.setParameter(UNIT_STATUS_LIST_PARAM, getUnitStatusList(fileStatus));
            }
            return ((Number) query.getSingleResult()).intValue();
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getRecordCountByDf(int dfId) {
        try {
            Query query = manager.createNamedQuery(RECORD_COUNT_BY_D_FILE).setParameter("df", dfId);
            return ((Long) query.getSingleResult()).intValue();
        } catch (Exception e) {
            LOG.error(e, e);
            return 0;
        }
    }

    public List<String> getRecordPathByDf(int dfId, boolean success) {
        return manager.createNamedQuery("recordsPath")
                .setParameter(DELIVERY_FILE_PARAM, dfId).setParameter(SUCCESS_PARAM, success).getResultList();
    }

    public List<String> getRecordPathByDf(int dfId, boolean success, String recNames) {
        String query = "SELECT r.recordPath from RecordEntity r where r.deliveryFile.id=:deliveryFile "
                + "and  r.qasSuccessful=:success" + " and name in (" + recNames + ")";
        return manager.createQuery(query).setParameter(DELIVERY_FILE_PARAM, dfId).setParameter(SUCCESS_PARAM,
                success).getResultList();
    }

    public List<ProductSubtitleVO> getProductSubtitles(int[] groups) {
        if (groups == null || groups.length < 1) {
            throw new EJBException("ProductSubtitle array is empty");
        }
        String list = "";
        for (int group : groups) {
            list += group + ", ";
        }
        list = list.substring(0, list.lastIndexOf(','));
        List<ProductSubtitleEntity> result = manager.createQuery("SELECT s from ProductSubtitleEntity s where s.id IN ("
                + list + ") order by id").getResultList();
        return createProductSubtitleVOList(result);
    }

    public String getRecordQASResults(int id) {
        String result;
        try {
            result = (String) manager.createNamedQuery("getQasResultByRecord").setParameter("id", id).getSingleResult();
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    public List<String> getNewReviews(int dbId) {
        return manager.createNamedQuery("recordNewReviews").setParameter(DB_ID_PARAM, dbId).getResultList();
    }

    public List<String> getUpdatedReviews(int dbId) {
        return manager.createNamedQuery("recordUpdatedReviews").setParameter(DB_ID_PARAM, dbId).getResultList();
    }

    public List<String> getNewProtocols(int dbId) {
        return manager.createNamedQuery("recordNewProtocols").setParameter(DB_ID_PARAM, dbId).getResultList();
    }

    public List<String> getUpdatedProtocols(int dbId) {
        return manager.createNamedQuery("recordUpdatedProtocols").setParameter(DB_ID_PARAM, dbId).getResultList();
    }

    public List<RecordEntity> getCdsrRecords4Report(int dbId, List<Integer> ignoredStatuses) {
        return manager.createNamedQuery("cdsrRecords4Report")
                .setParameter(DB_ID_PARAM, dbId).setParameter("ignoredStatuses", ignoredStatuses).getResultList();
    }

    public List<String> getRecordPathByDb(int dbId, int start, int pieceSize) {
        return manager.createNamedQuery("recordsPathByDb")
                .setParameter(DB_PARAM, dbId).setFirstResult(start).setMaxResults(pieceSize).getResultList();
    }

    public List<String> getRecordPathByDf(int dfId, int start, int pieceSize) {
        return RecordEntity.queryRecordPath(dfId, start, pieceSize, manager).getResultList();
    }

    @Override
    public List<Object[]> getRecordPathMappedToId(Collection<Integer> recIds) {
        return RecordEntity.queryRecordPath(recIds, manager).getResultList();
    }

    private List<UnitStatusEntity> getUnitStatusList(String unitStatuses) {
        List<UnitStatusEntity> ret = new ArrayList<>();
        String[] strs = unitStatuses.split(",");
        for (String s : strs) {
            ret.add(manager.find(UnitStatusEntity.class, Integer.parseInt(s.trim())));
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<UnitStatusVO> getUnitStatusList(List<Integer> unitStatusIds) {
        List<UnitStatusVO> ret = new ArrayList<>();
        unitStatusIds.forEach(id -> ret.add(new UnitStatusVO(manager.find(UnitStatusEntity.class, id))));
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getRecordPathByNameAndIssue(int issueId, String name) {
        return manager.createQuery("SELECT r.recordPath FROM RecordEntity r where r.db.id=:dbId and r.name=:name")
                        .setParameter("name", name).setParameter(DB_ID_PARAM, issueId).getResultList();
    }

    @Deprecated
    public List<Object[]> getWithdrawnRecords() {
        return manager.createQuery("SELECT r.name, c.issue.id"
                + " FROM RecordEntity r, ClDbEntity c, IssueEntity i"
                + " WHERE (r.unitStatus.id =5 OR r.unitStatus.id =15 OR r.unitStatus.id =25)"
                + " AND r.recordPath LIKE '%clsysrev%' AND r.db.id = c.id AND c.issue.id = i.id"
                + " ORDER BY r.name, i.year DESC , i.number DESC ").getResultList();
    }

    @Deprecated
    public List<String> getWithdrawnRecords(String dbName, int issueId) {
        return manager.createNamedQuery("getWithdrawnRecords")
                .setParameter(DB_NAME_PARAM, dbName).setParameter(ISSUE_ID_PARAM, issueId).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<String> getWithdrawnRecords(int dbId, int dfId) {
        return RecordEntity.queryWithdrawnRecordNamesByDf(dbId, dfId, manager).getResultList();
    }

    public OpStats getQasCompleted(int dfId) {
        long completed = (long) RecordEntity.queryRecordCountQasCompletedByDfId(dfId, true, manager).getSingleResult();
        OpStats stats = new OpStats((int) completed, (int) getRecordCountByDf(dfId));
        if (stats.isCompleted()) {
            stats.setTotalSuccessful((int) getCountQaByDfId(dfId, true));
        }
        return stats;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getQasUncompleted(int dfId) {
        return (int) RecordEntity.queryRecordCountQasCompletedByDfId(dfId, false, manager).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<RecordEntity> getRecordsByDFile(DeliveryFileEntity dfEntity, boolean onlySuccessful) {
        return manager.createNamedQuery(onlySuccessful ? RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE_QAS_SUCCESSFUL
                : RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE).setParameter("df", dfEntity).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RecordEntity> getRecordsQasUncompleted(int dfId) {
        return RecordEntity.queryRecordsQasUncompleted(dfId, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Record> getRecordsForRendering(int dfId) {
        return manager.createNamedQuery(RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE_ID_QAS_SUCCESSFUL).setParameter(
                "dfId", dfId).getResultList();
    }

    public List<RecordEntity> getRecordsByDFile(String recordNames, DeliveryFileEntity dfEntity) {
        return  manager.createQuery("SELECT r from RecordEntity r where r.name in("
                            + recordNames + ") and r.deliveryFile=:df").setParameter("df", dfEntity).getResultList();
    }

    public List<Integer> getRecordIdsByDfId(int dfId, int offset, int limit) {
        return RecordEntity.queryRecordIdsByDf(dfId, offset, limit, manager).getResultList();
    }

    public long getRecordsCountByClDbAndNames(String recordNames, int numberRndPlans, ClDbEntity dbEntity) {
        Object[] args = new Object[]{
            "select count(r.id) from RecordEntity r ", recordNames, "", "=", numberRndPlans
        };
        return (Long) manager.createQuery(RECORD_ENTRY_CONDITION_TEMPLATE.format(args))
                .setParameter("db", dbEntity).getSingleResult();
    }

    public int setCompletedAndDisableForRecords(String recordNames, int numberRndPlans, ClDbEntity dbEntity) {
        Object[] args = new Object[]{"update RecordEntity r set r.renderingCompleted=true, r.disabled=false ",
            recordNames, "", "=", numberRndPlans
        };
        return manager.createQuery(RECORD_ENTRY_CONDITION_TEMPLATE.format(args)).setParameter(
                "db", dbEntity).executeUpdate();
    }

    public int setSuccessForRecords(String recordNames, int numberRndPlans, ClDbEntity dbEntity, boolean isEqualsNums) {
        Object[] args = new Object[]{"update RecordEntity r set r.renderingSuccessful=true ",
            recordNames, " and r.renderingCompleted=true", (isEqualsNums) ? "=" : "!=", numberRndPlans
        };
        return manager.createQuery(RECORD_ENTRY_CONDITION_TEMPLATE.format(args))
                .setParameter("db", dbEntity).executeUpdate();
    }

    public void updateRecords(Collection<String> records, int deliveryFileId, boolean status, ClDbEntity db) {
        String query = "update RecordEntity re set re.qasSuccessful = :status where re.db = :db"
                + " and re.deliveryFile.id = :deliveryFileId and re.name in (:records)";
        manager.createQuery(query).setParameter(STATUS, status).setParameter(DELIVERY_FILE_ID, deliveryFileId)
                .setParameter(DB_PARAM, db).setParameter(RECORDS, records).executeUpdate();
    }

    public void updateRenderingStateQaSuccessByDfIds(List<Integer> dfIds, boolean state) {
        RecordEntity.querySetRenderingStateQaSuccessByDfIds(dfIds, state, manager).executeUpdate();
    }

    public void updateRecordsRenderingStatus(Collection<String> recs, int packageId, boolean renderSuccess,
                                             ClDbEntity db) {
        if ((recs == null) || (recs.isEmpty())) {
            return;
        }
        String query = "update RecordEntity re set re.renderingSuccessful = :status, re.renderingCompleted = true"
                + " where re.db = :db and re.deliveryFile.id = :deliveryFileId and re.name in (:records)";
        manager.createQuery(query).setParameter(STATUS, renderSuccess).setParameter(
            DELIVERY_FILE_ID, packageId).setParameter(DB_PARAM, db).setParameter(RECORDS, recs).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getCountRenderingCompletedByDfID(int dfId) {
        return (Long) RecordEntity.queryRecordCountRenderCompletedByDfId(dfId, manager).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getCountRenderingSuccessfulByDfId(int dfId) {
        return (Long) RecordEntity.queryRecordCountSuccessfulByDfId(dfId, manager).getSingleResult();
    }

    public long getCountRenderingFailedByDfId(int dfId) {
        return (Long) RecordEntity.qRecordCountRenderingFailedByDfId(dfId, manager).getSingleResult();
    }

    public long getCountRenderingFailedQaSuccessByDfId(int dfId) {
        return (Long) RecordEntity.qRecordCountRenderingFailedQaSuccessByDfId(dfId, manager).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getCountRenderingByDfId(int dfId) {
        return (Long) RecordEntity.queryRecordCountRenderByDfId(dfId, manager).getSingleResult();
    }

    public long getCountQaByDfId(int dfId, boolean successful) {
        return (Long) RecordEntity.queryRecordCountQaByDfId(dfId, successful, manager).getSingleResult();
    }

    public List<RecordPublishVO> getRecordPublishVOByRecordIdsAndStates(List<Integer> ids, List<Integer> states) {
        return DbRecordPublishEntity.qVOByRecordIdsAndStates(ids, states, manager).getResultList();
    }

    public List<Object[]> getRecordPaths(int dfId, boolean qasSuccessful, int offset, int limit) {
        return RecordEntity.queryRecordPaths(dfId, qasSuccessful, offset, limit, manager).getResultList();
    }

    public List<Integer> getRecordIds(List<Integer> recIds, List<Integer> states) {
        return DbRecordPublishEntity.qRecordId(recIds, states, manager).getResultList();
    }

    public long getRecordPublishCountByStateAndDbId(int state, int dbId) {
        return (Long) DbRecordPublishEntity.qCountByStateAndDbId(state, dbId, manager).getSingleResult();
    }

    public long getRecordPublishCountByRecordIdsAndStates(List<Integer> ids, List<Integer> states) {
        return (Long) DbRecordPublishEntity.qCountByRecordIdsAndStates(ids, states, manager).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecordPublishByRecordIds(Collection<Integer> recIds) {
        DbRecordPublishEntity.qDeleteByRecordIds(recIds, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void persistRecordPublish(Map<Integer, RecordPublishVO> recIds) {
        Date date = new Date();
        List<RecordEntity> recEntities = getRecordEntitiesByIds(recIds.keySet());
        for (RecordEntity re: recEntities) {
            RecordPublishVO rpVO = recIds.get(re.getId());
            if (rpVO != null) {
                DbRecordPublishEntity rpEntity = new DbRecordPublishEntity();
                rpEntity.setName(rpVO.getName());
                rpEntity.setState(rpVO.getState());
                rpEntity.setDate(date);
                rpEntity.setRecord(re);
                manager.persist(rpEntity);
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateRecordPublishStateByRecordIds(Date date, int state, List<Integer> recIds) {
        DbRecordPublishEntity.qUpdateStateByRecordIds(date, state, recIds, manager).executeUpdate();
    }

    public int setRenderResults(Collection<Integer> ids, boolean successful, boolean completed) {
        return completed ? RecordEntity.querySetRenderingStateByIds(ids, successful, manager).executeUpdate()
                : RecordEntity.queryResetRenderingStateByIds(ids, manager).executeUpdate();
    }

    public void getLatestQasResultsByRecordsIds(Collection<Integer> recordIds, Map<Integer, String> ret) {
        List list = JobQasResultEntity.queryLatestQasResultByRecord(recordIds, manager).getResultList();
        for (Object obj: list) {
            JobQasResultEntity e = (JobQasResultEntity) obj;
            int recordId = e.getRecord().getId();
            if (!ret.containsKey(recordId)) {
                ret.put(recordId, e.getResult());
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void flushQAResults(IRecord record, boolean wr, boolean successful, String message, IActivityLog logger) {
        RecordEntity re = manager.find(RecordEntity.class, record.getId());
        if (re == null) {
            return;
        }
        re.setQasCompleted(true);
        re.setQasSuccessful(successful);
        if (message != null && !message.isEmpty()) {
            createJobQasResult(re, message);
        }
        if (!successful) {
            if (wr) {
                re.setState(RecordEntity.STATE_WR_ERROR);
            }
            logger.logRecordError(ILogEvent.QAS_FAILED, re.getDeliveryFile().getId(), re.getName(),
                    re.getDb().getId(), re.getDb().getIssue().getFullNumber(), message);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void flushQAResults(Collection<? extends IRecord> records, int dbId, boolean wr, boolean isTa,
                               boolean toMl3g, boolean setRender) {
        IActivityLogService logService = ActivityLogFactory.getFactory().getInstance();
        BaseType bt = null;
        IssueEntity issue = null;
        List<Integer> ids = toMl3g ? new ArrayList<>() : null;
        for (IRecord record: records) {
            RecordEntity re = getRecord(dbId, record.getName());  //todo optimise select
            if (re == null) {
                LOG.error(String.format("record %s, db=%d has not found", record.getName(), dbId));
                continue;
            }
            if (bt == null) {
                bt = BaseType.find(re.getDb().getTitle()).get();
                issue = re.getDb().getIssue();
            }
            String cdNumber = record.getName();
            updateRecord(bt, record, re, wr, isTa, toMl3g, setRender);
            if (record.getMessages() != null && !record.getMessages().isEmpty()) {
                createJobQasResult(re, record.getMessages());
                if (!record.isSuccessful()) {
                    logService.logRecordError(ILogEvent.QAS_FAILED, re.getDeliveryFile().getId(), cdNumber,
                            bt.getDbId(), issue.getFullNumber(), record.getMessages());
                }
            }
            if (ids != null) {
                ids.add(re.getId());
            }
            manager.merge(re);
        }
        if (ids != null && !ids.isEmpty()) {
            deleteRecordPublishByRecordIds(ids);
        }
    }

    private void updateRecord(BaseType bt, IRecord record, RecordEntity re, boolean wr,  boolean isTa, boolean toMl3g,
                              boolean setRender) {
        record.setId(re.getId());
        record.setRawExist(re.isRawDataExists());
        re.setQasCompleted(true);
        re.setQasSuccessful(record.isSuccessful());
        if (!record.isSuccessful()) {
            if (bt.isCentral()) {
                re.setState(RecordEntity.STATE_WR_ERROR_FINAL);
            } else if (wr) {
                re.setState(RecordEntity.STATE_WR_ERROR);
            }
        }
        if (setRender){
            re.setRenderingCompleted(true);
            re.setRenderingSuccessful(record.isSuccessful());
        }
        if (record.getTitle() != null) {
            re.setTitleEntity(TitleEntity.checkEntity(record.getTitle(), re, manager));
        }
        re.setProductSubtitle(record.getSubTitle() == null ? null
            : manager.find(ProductSubtitleEntity.class, record.getSubTitle()));

        if (re.getUnitStatus() == null || !bt.isCDSR()) {
            // replace unit status for all databases except CDSR because the existing (initial) CDSR unit status
            // should be kept to prevent replacement with translations
            re.setUnitStatus(record.getUnitStatusId() == null ? null
                    : manager.find(UnitStatusEntity.class, record.getUnitStatusId()));

        } else if (!toMl3g && re.getUnitStatus().isMeshtermUpdated() && (isTa
                || RecordHelper.hasOnlyNotJatsTranslations(re.getDb().getIssue().getId(),
                re.getDeliveryFile().getId(), re.getName()))) {

            re.setUnitStatus(manager.find(UnitStatusEntity.class,
                    UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS));
        }
    }

    private void createJobQasResult(RecordEntity re, String message) {
        JobQasResultEntity result = new JobQasResultEntity();
        result.setRecord(re);
        result.setResult(message);
        manager.persist(result);
    }

    private String buildAddQuery(int productSubtitle, String alias) {
        String addQuery = "";
        if (productSubtitle > 0) {
            addQuery = "and " + alias + ".productSubtitle='" + productSubtitle + "' ";
        }
        return addQuery;
    }
}