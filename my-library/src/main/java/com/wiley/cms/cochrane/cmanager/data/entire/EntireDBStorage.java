package com.wiley.cms.cochrane.cmanager.data.entire;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DbRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.EntireRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.StatsUpdater;
import com.wiley.cms.cochrane.cmanager.data.record.EntireRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchEntireRecordOrder;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */
@Stateless
@Local(IEntireDBStorage.class)
public class EntireDBStorage implements IEntireDBStorage {
    private static final Logger LOG = Logger.getLogger(EntireDBStorage.class);
    private static final String LAST_ISSUE_PUBLISHED = "lastIssuePublished";
    private static final String RECORD_NAME = "recordName";
    private static final String ISSUE = "issue";
    private static final String IDS = "ids";
    private static final String UNIT_STATUS = "unitStatus";
    private static final String SRC_DIR = "/src/";
    private static final MessageFormat QUERY_TEMPLATE;
    private static final MessageFormat QUERY_TEMPLATE_EX;
    private static final MessageFormat QUERY_TEMPLATE_TITLE;
    private static final String RECORD_NOT_FOUND_IN_ENTIRE_DB = "Record not found in Entire DB: ";
    private static final String RECORD_NOT_FOUND_BY_TITLE_IN_ENTIRE_DB = "Record not found by name in Entire DB: ";
    private static final String DESC_ORDER = " desc";
    private static final String FAILED_DELETE_WML3G_MSG_TEMP = "Failed to delete WML3G %s [%s]";
    private static final String FAILED_COPY_WML3G_MSG_TEMP = "Failed to copy WML3G %s from [%s] to [%s], %s.\n";

    @EJB(beanName = "ResultsStorage")
    IResultsStorage rs;

    @EJB(beanName = "VersionManager")
    IVersionManager vm;

    @PersistenceContext
    private EntityManager manager;

    private IRepository rps = RepositoryFactory.getRepository();

    static {
        String s1 =
            "SELECT {0} FROM (SELECT name, max(lastIssuePublished) AS maxissue FROM COCHRANE_ENTIRE_DB GROUP BY name) ";

        String s2 = "AS x INNER JOIN COCHRANE_ENTIRE_DB AS r ON r.name=x.name AND r.lastIssuePublished=x.maxissue "
                + "INNER JOIN COCHRANE_DATABASE AS dbn ON dbn.id=r.database_id WHERE dbn.id=:db {1}";

        String s3 = "SELECT {0} FROM (SELECT name, max(lastIssuePublished) AS maxissue FROM COCHRANE_ENTIRE_DB z "
                + "INNER JOIN CMS_PROCESS_PART AS pp ON (pp.process_id=:processId AND z.id=pp.uri) GROUP BY name) ";

        String s11 = "SELECT {0} FROM COCHRANE_ENTIRE_DB AS r LEFT JOIN COCHRANE_ENTIRE_DB AS e"
                + " ON (r.name=e.name AND r.lastIssuePublished < e.lastIssuePublished) ";

        String s111 = "LEFT JOIN COCHRANE_TITLE AS t ON (r.title_id=t.id) ";

        String s22 = "WHERE r.database_id=:db AND e.id IS NULL {1}";

        String s33 = "SELECT {0} FROM COCHRANE_ENTIRE_DB AS r LEFT JOIN COCHRANE_ENTIRE_DB AS e "
            + "ON (r.name=e.name AND r.lastIssuePublished < e.lastIssuePublished)"
            + "INNER JOIN CMS_PROCESS_PART AS pp ON (pp.process_id=:processId AND r.id=pp.uri) ";

        StringBuilder sb = new StringBuilder(s11).append(s22);
        QUERY_TEMPLATE = new MessageFormat(sb.toString());

        sb = new StringBuilder(s11).append(s111).append(s22);
        QUERY_TEMPLATE_TITLE = new MessageFormat(sb.toString());

        sb = new StringBuilder(s33).append(s22);
        QUERY_TEMPLATE_EX = new MessageFormat(sb.toString());
    }

    public void remove(String db, String recordName) {
        DatabaseEntity database = rs.getDatabaseEntity(db);
        List<EntireDBEntity> result = findEntityByDbAndRecordName(database.getId(), recordName);
        EntireRecordPublishEntity.qDeleteByRecordIds(getIds(result), manager).executeUpdate();
        for (EntireDBEntity entity : result) {
            manager.remove(entity);
        }
        manager.flush();
    }

    @SuppressWarnings("unchecked")
    public List<String> findSysrevRecordNames() {
        return findRecordNames(CochraneCMSPropertyNames.getCDSRDbName(), 0, 0);
    }

    @SuppressWarnings("unchecked")
    public List<String> findSysrevReviewRecordNames() {
        DatabaseEntity db = (DatabaseEntity) DatabaseEntity.queryDatabase(
                CochraneCMSPropertyNames.getCDSRDbName(), manager).getSingleResult();
        return EntireDBEntity.qDistinctRecordNamesByDbIdProductSubtitleId(db.getId(),
                ProductSubtitleEntity.ProductSubtitle.REVIEWS, manager).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<String> findSysrevRecordNames(int issueId) {
        IssueEntity entity = manager.find(IssueEntity.class, issueId);
        DatabaseEntity database = rs.getDatabaseEntity(CochraneCMSPropertyNames.getCDSRDbName());
        List<EntireDBEntity> edbList = findRecordNamesByIssueAndDb(entity.getFullNumber(), database);
        List<String> result = new ArrayList<String>();
        for (EntireDBEntity e : edbList) {
            result.add(e.getName());
        }
        return result;
    }

    public Integer findUnitStatus(DatabaseEntity database, String recordName) {
        List<UnitStatusEntity> result = EntireDBEntity.qUnitStatusByRecNameAndDb(recordName, database, manager)
                .getResultList();
        if (result.isEmpty()) {
            LOG.error(RECORD_NOT_FOUND_IN_ENTIRE_DB + recordName);
            return null;
        }
        return result.get(0).getId();
    }

    public void updateRecord(int dbId, String recordName, boolean setApproved) {
        RecordEntity r = ((List<RecordEntity>) RecordEntity.queryRecords(
                dbId, recordName, manager).getResultList()).get(0);
        if (setApproved) {
            r.setApproved(true);
            manager.merge(r);
            if (!r.getDb().getApproved()) {
                r.getDb().setApproved(true);
            }
            manager.merge(r.getDb());
        }
        EntireDBEntity edb;
        List<EntireDBEntity> edbList = (List<EntireDBEntity>) manager.createNamedQuery(
            "findEntityByDbAndRecordNameAndIssue").setParameter(RECORD_NAME, r.getName()).setParameter("db",
                r.getDb().getDatabase()).setParameter(ISSUE, r.getDb().getIssue().getFullNumber()).getResultList();
        if (edbList.isEmpty()) {
            edb = new EntireDBEntity();
            edb.setName(r.getName());
            edb.setDatabase(r.getDb().getDatabase());
        } else {
            edb = edbList.get(0);
        }
        UnitStatusEntity unitStatus = r.getUnitStatus();
        if ((r.getUnitStatus() != null)
                && (r.getUnitStatus().getId() == UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED)) {
            List<EntireDBEntity> previousRecords = findEntityByDbAndRecordName(r.getDb().getDatabase().getId(),
                    recordName);
            UnitStatusEntity previousRecordStatus = null;
            if ((previousRecords != null) && (!previousRecords.isEmpty())) {
                previousRecordStatus = previousRecords.get(0).getUnitStatus();
            }
            if ((previousRecordStatus != null)
                    && (previousRecordStatus.getId() == UnitStatusEntity.UnitStatus.WITHDRAWN
                    || previousRecordStatus.getId() == UnitStatusEntity.UnitStatus.WITHDRAWN1)) {
                unitStatus = previousRecordStatus;
            }
        }
        edb.setUnitStatus(unitStatus);
        edb.setLastIssuePublished(r.getDb().getIssue().getFullNumber());
        edb.setTitleEntity(r.getTitleEntity());
        edb.setProductSubtitle(r.getProductSubtitle());
        if (edbList.isEmpty()) {
            manager.persist(edb);
        } else {
            manager.merge(edb);
        }
        manager.flush();
        updateRecordPublish(edb, r.getId());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void getRecordExists(int databaseId, int issueNumber, Set<String> ret) {
        List<EntireDBEntity> edbList = findEntitiesByDbAndRecordName(databaseId, ret);
        ret.clear();
        for (EntireDBEntity ee: edbList) {
            if (ee.getLastIssuePublished() != issueNumber) {
                ret.add(ee.getName());
            }
        }
    }

    public boolean isRecordExists(int databaseId, int issueNumber, String recordName) {
        List<EntireDBEntity> edbList = findEntityByDbAndRecordName(databaseId, recordName);
        boolean ret = false;
        if (issueNumber > 0) {
            for (EntireDBEntity ee: edbList) {
                if (ee.getLastIssuePublished() != issueNumber) {
                    ret = true;
                    break;
                }
            }
        } else {
            ret = !edbList.isEmpty();
        }
        return ret;
    }

    public List<Integer> getRecordIds(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                      int orderField, boolean orderDesc, String text, int lastIssuePublished) {
        List<Integer> ids = new ArrayList<>();
        try {
            Object[] args = new Object[]{"r.id", extendSearchRecordQueryString(items, orderField, orderDesc,
                    text, fileStatus, lastIssuePublished)};
            Query query = manager.createNativeQuery(getQueryTemplate(orderField).format(args)).setParameter("db",
                    BaseType.find(db).get().getDbId());
            query = query.setFirstResult(beginIndex);
            if (amount > 0) {
                query = query.setMaxResults(amount);
            }
            ids = query.getResultList();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return ids;
    }

    private MessageFormat getQueryTemplate(int orderField) {
        return SearchEntireRecordOrder.UNIT_TITLE == orderField ? QUERY_TEMPLATE_TITLE : QUERY_TEMPLATE;
    }

    public Map<Integer, String> getRecordIdsAndNames(String db, int beginIndex, int amount, String[] items,
        String fileStatus, int orderField, boolean orderDesc, String text) {
        Map<Integer, String> idsAndNames = new HashMap<Integer, String>();
        Object[] args = new Object[]{"r.id, r.name", extendSearchRecordQueryString(items, orderField, orderDesc,
                text, fileStatus, Constants.UNDEF)};
        Query query = manager.createNativeQuery(getQueryTemplate(orderField).format(args)).setParameter("db",
                BaseType.find(db).get().getDbId());
        query = query.setFirstResult(beginIndex);
        if (amount > 0) {
            query = query.setMaxResults(amount);
        }
        List<Object[]> resultList = query.getResultList();
        for (Object[] idAndName : resultList) {
            idsAndNames.put(Integer.valueOf(idAndName[0].toString()), idAndName[1].toString());
        }
        return idsAndNames;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<EntireDBEntity> getRecordList(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                              int orderFields, boolean orderDesc) {
        return getRecordList(db, beginIndex, amount, items, fileStatus, orderFields, orderDesc, 0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<EntireDBEntity> getRecordList(String db, int beginIndex, int amount, String[] items, String fileStatus,
                                              int orderField, boolean orderDesc, int processId) {
        List<EntireDBEntity> records = new ArrayList<>();
        try {
            Object[] args = new Object[]{"r.*", extendSearchRecordQueryString(items, orderField, orderDesc, null,
                    fileStatus, Constants.UNDEF)};
            Query query = (processId > 0)
                    ? manager.createNativeQuery(QUERY_TEMPLATE_EX.format(args),
                        EntireDBEntity.class).setParameter("db", BaseType.find(db).get().getDbId()).setParameter(
                            "processId", processId)
                    : manager.createNativeQuery(getQueryTemplate(orderField).format(args),
                        EntireDBEntity.class).setParameter("db", BaseType.find(db).get().getDbId());
            query = query.setFirstResult(beginIndex);
            if (amount > 0) {
                query = query.setMaxResults(amount);
            }
            records = query.getResultList();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return records;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getRecordListCount(int dbId) {
        return ((Number) EntireDBEntity.queryRecordsCount(dbId, manager).getSingleResult()).intValue();
    }

    public int getRecordListCount(String db, String[] items, String fileStatus) {
        return getRecordListCount(db, items, null, fileStatus, SearchEntireRecordOrder.NONE, false);
    }

    public int getRecordListCount(String db, String[] items, String text, String fileStatus, int orderField,
                                  boolean orderDesc) {
        int result = 0;
        try {
            Object[] args = new Object[]{"count(*)",
                    extendSearchRecordQueryString(items, orderField, orderDesc, text, fileStatus, Constants.UNDEF)};
            Query query = manager.createNativeQuery(QUERY_TEMPLATE.format(args)).setParameter("db",
                    BaseType.find(db).get().getDbId());
            result = ((Number) query.getSingleResult()).intValue();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return result;
    }

    public EntireDBEntity findRecord(int recordId) {
        return manager.find(EntireDBEntity.class, recordId);
    }

    private String extendSearchRecordQueryString(String[] items, int orderField, boolean orderDesc, String text,
                                                 String fileStatus, int lastIssuePublished) {
        StringBuilder addQuery = new StringBuilder();
        if (fileStatus != null && !fileStatus.isEmpty()) {
            addQuery.append(" and r.unitStatus_id in (").append(fileStatus).append(")");
        }
        if (text != null && !text.isEmpty()) {
            String title = text.replaceAll("(\\*)+", "%");
            addQuery.append(" and r.name like ('").append(title).append("')");
        }
        if (items != null) {
            getInToken(items, addQuery);
        }
        if (lastIssuePublished != Constants.UNDEF) {
            addQuery.append(" AND r.lastIssuePublished = ").append(lastIssuePublished);
        }
        if (orderField != SearchEntireRecordOrder.NONE) {
            getEntireRecordsOrder(orderField, orderDesc, addQuery);
        }
        return addQuery.toString();
    }

    private void getEntireRecordsOrder(int orderField, boolean orderDesc, StringBuilder addQuery) {
        switch (orderField) {
            case SearchEntireRecordOrder.NAME:
                addQuery.append(" order by r.name");
                if (orderDesc) {
                    addQuery.append(DESC_ORDER);
                }
                break;
            case SearchEntireRecordOrder.STATUS:
                addQuery.append(" order by r.unitStatus");
                if (orderDesc) {
                    addQuery.append(DESC_ORDER);
                }
                break;
            case SearchEntireRecordOrder.UNIT_TITLE:
                addQuery.append(" order by r.unitTitle");
                if (orderDesc) {
                    addQuery.append(DESC_ORDER);
                }
                break;
            case SearchEntireRecordOrder.LAST_ISSUE:
                addQuery.append(" order by r.lastIssuePublished");
                if (orderDesc) {
                    addQuery.append(DESC_ORDER);
                }
                break;
            default:
        }
    }

    private void getInToken(String[] items, StringBuilder addQuery) {
        if (items.length == 1 && (items[0] == null || items[0].trim().isEmpty())) {
            return;
        }

        addQuery.append(" and r.name in(");
        boolean first = true;
        for (String name : items) {
            if (!first) {
                addQuery.append(", ");
            } else {
                first = false;
            }
            addQuery.append("'").append(StringEscapeUtils.escapeSql(name)).append("'");
        }
        addQuery.append(")");
    }

    public List<String> findRecordNames(List<Integer> ids) {
        return manager.createNamedQuery("findRecordNames").setParameter(IDS, ids).getResultList();
    }

    public List<String> getRecordNamesByDbNameAndUnitStatuses(String dbName, List<Integer> unitStatuses) {
        return manager.createNamedQuery("findDistinctRecordNamesByDbAndUnitStatus")
                .setParameter("db", dbName).setParameter(UNIT_STATUS, unitStatuses).getResultList();
    }

    public List<String> getRecordNamesFromListByDbName(List<String> recordNames, String dbName) {
        return manager.createNamedQuery("findDistinctRecordNamesFromListByDb")
                .setParameter(RECORD_NAME, recordNames).setParameter("db", dbName).getResultList();
    }

    public List<String> getRecordNamesFromListByDbNameAndUnitStatuses(List<String> recordNames, String dbName,
                                                                      List<Integer> unitStatuses) {
        return manager.createNamedQuery("findDistinctRecordNamesFromListByDbAndUnitStatus")
                .setParameter(RECORD_NAME, recordNames).setParameter("db", dbName)
                .setParameter(UNIT_STATUS, unitStatuses).getResultList();
    }

    public List<Integer> getLastIssuePublishedList(int dbId) {
        return EntireDBEntity.qLastIssuePublishedByDbId(dbId, manager).getResultList();
    }

    public List<Integer> getIdsBelongToIssue(int lastIssuePublished, List<Integer> recIds) {
        return EntireDBEntity.qIdBelongsToIssue(lastIssuePublished, recIds, manager).getResultList();
    }

    @TransactionAttribute(value = TransactionAttributeType.SUPPORTS)
    @SuppressWarnings("unchecked")
    public Collection<String> clearDb(Integer clDbId, boolean clearPdf) throws Exception {
        LOG.debug("Clearing entire DB started...");
        ClDbEntity clDb = manager.find(ClDbEntity.class, clDbId);
        int lastIssuePublished = clDb.getIssue().getFullNumber();
        List<EntireDBEntity> edbList = findRecordNamesByIssueAndDb(lastIssuePublished, clDb.getDatabase());
        String dbName = clDb.getTitle();
        BaseType baseType = BaseType.find(dbName).get();
        boolean cdsr = baseType.isCDSR();
        boolean central = !cdsr && baseType.isCentral();
        TranslatedAbstractsHelper tah = cdsr ? new TranslatedAbstractsHelper() : null;

        List<String> names = new ArrayList<>();
        if (!central) {
            edbList.forEach(edb -> names.add(edb.getName()));
            Hooks.captureRecords(clDbId, names, Hooks.CLEAR_DB_START);
        }
        try {
            for (EntireDBEntity entity : edbList) {
                String name = entity.getName();
                Integer issueId = clDb.getIssue().getId();
                boolean jats = false;
                if (cdsr) {
                    String group = vm.clearVersionFolders(clDb.getIssue().getFullNumber(), name);
                    RecordMetadataEntity prevMeta = removeMetadata(name, lastIssuePublished, true);
                    deleteFromEntire(baseType, clDbId, name, false, false);
                    deleteDirFromEntire(FilePathBuilder.JATS.getPathToEntireDir(dbName, name));
                    deleteRevmanFromEntire(name, group);
                    jats = restoreJatsFromBackup(issueId, dbName, name, prevMeta);
                    restoreAbstractsFromBackup(issueId, name, tah);
                    restoreRevmanFromBackup(name, issueId, group);
                } else {
                    if (!central) {
                        removeMetadata(name, lastIssuePublished, false);
                    }
                    deleteFromEntire(baseType, clDbId, name, central, false);
                }
                if (jats) {
                    restoreFromBackup(issueId, dbName, name, baseType);
                } else {
                    restoreFromBackup(issueId, dbName, name, clearPdf, baseType, central);
                }
            }
            PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(clDbId, manager).executeUpdate();
            deletePublishedRecords(PublishRecordEntity.queryPublishedRecordsByDb(clDbId, manager).getResultList());

        } catch (Exception e) {
            LOG.error("Error restoring objects", e);

        } finally {
            StatsUpdater.onUpdate(dbName);
        }
        manager.createNamedQuery("deleteRecordsByIssueAndDb").setParameter(LAST_ISSUE_PUBLISHED, lastIssuePublished)
                .setParameter("db", clDb.getDatabase()).executeUpdate();
        LOG.debug("Clearing entire DB finished.");
        return names;
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void clearRecord(int clDbId, String recordName, boolean clearPdf) throws Exception {
        clearRecord(clDbId, recordName, clearPdf, true);
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void clearRecord(int clDbId, String recordName, boolean clearPdf, boolean restoreBackup) throws Exception {
        LOG.debug("Clearing entire DB started for " + recordName);
        ClDbEntity clDb = manager.find(ClDbEntity.class, clDbId);
        String dbName = clDb.getTitle();
        BaseType baseType = BaseType.find(dbName).get();
        boolean cdsr = baseType.isCDSR();
        boolean central = !cdsr && baseType.isCentral();

        int lastIssuePublished = clDb.getIssue().getFullNumber();
        EntireDBEntity edbRecord = findRecordByIssueAndName(lastIssuePublished, recordName);
        if (edbRecord  == null) {
            removeMetadata(recordName, lastIssuePublished, cdsr);
            if (!central) {
                PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(recordName, clDbId, manager).executeUpdate();
            }
            LOG.debug(String.format("%s was not found. Clearing entire DB passed without results", recordName));
            return;
        }
        Integer issueId = clDb.getIssue().getId();
        TranslatedAbstractsHelper tah = cdsr ? new TranslatedAbstractsHelper() : null;
        String group = null;
        RecordMetadataEntity prevMeta = null;
        if (cdsr) {
            group = vm.clearVersionFolders(clDb.getIssue().getFullNumber(), recordName);
            prevMeta = removeMetadata(recordName, lastIssuePublished, true);
            deleteFromEntire(baseType, clDbId, recordName, false, true);
            deleteRevmanFromEntire(recordName, group);
            deleteDirFromEntire(FilePathBuilder.JATS.getPathToEntireDir(dbName, recordName));
        } else {
            if (!central) {
                removeMetadata(recordName, lastIssuePublished, false);
            }
            deleteFromEntire(baseType, clDbId, recordName, central, true);
        }
        boolean jats = false;
        if (restoreBackup) {
            if (cdsr) {
                jats = restoreJatsFromBackup(issueId, dbName, recordName, prevMeta);
                restoreAbstractsFromBackup(issueId, recordName, tah);
                restoreRevmanFromBackup(recordName, issueId, group);
            }
            if (jats) {
                restoreFromBackup(issueId, dbName, recordName, baseType);
            } else {
                restoreFromBackup(issueId, dbName, recordName, clearPdf, baseType, central);
            }
            RecordHelper.clearBackUpSrc(issueId, dbName, recordName, central, rps);
        }

        StatsUpdater.onUpdate(dbName);
        manager.createNamedQuery("deleteRecordsByIssueAndName").setParameter(LAST_ISSUE_PUBLISHED,
                lastIssuePublished).setParameter(RECORD_NAME, recordName).executeUpdate();
        LOG.debug("Clearing entire DB finished for " + recordName);
    }

    private RecordMetadataEntity removeMetadata(String recordName, int lastIssuePublished, boolean cdsr) {
        List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadata(recordName, manager).getResultList();
        for (RecordMetadataEntity rm: list) {
            int issue = rm.getIssue();
            if (issue == lastIssuePublished) {
                manager.remove(rm);
                manager.remove(rm.getVersion());
                continue;
            }
            Integer historyNumber = rm.getHistoryNumber();
            if (historyNumber == null || RecordEntity.VERSION_LAST == historyNumber || issue > lastIssuePublished) {
                break;
            }
            if (historyNumber > RecordEntity.VERSION_LAST || historyNumber == RecordEntity.VERSION_INTERMEDIATE) {
                rm.getVersion().setHistoryNumber(RecordEntity.VERSION_LAST);
                manager.merge(rm);
                if (cdsr) {
                    RecordHelper.deletePreviousDir(recordName, historyNumber, rm.getGroupSid(), rps);
                }
                return rm;
            }
        }
        return null;
    }

    private void deletePublishedRecords(List<PublishRecordEntity> list) {
        for (PublishRecordEntity pre: list) {
            manager.remove(pre);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> findEditorialRecordNames() {
        return findRecordNames(CochraneCMSPropertyNames.getEditorialDbName(), Constants.UNDEF, Constants.UNDEF);
    }

    public List<String> findEditorialRecordNames(int issueId) {
        IssueEntity entity = manager.find(IssueEntity.class, issueId);
        DatabaseEntity database = rs.getDatabaseEntity(CochraneCMSPropertyNames.getEditorialDbName());
        List<EntireDBEntity> edbList = findRecordNamesByIssueAndDb(entity.getFullNumber(), database);
        List<String> result = new ArrayList<>();
        edbList.forEach(e -> result.add(e.getName()));
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Integer> findIssuesWithEditorials() {
        DatabaseEntity dbEntity = rs.getDatabaseEntity(CochraneCMSPropertyNames.getEditorialDbName());
        return manager.createNamedQuery("findIssuesByDbName").setParameter("db", dbEntity).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<EntireDBEntity> findRecordNamesByIssueAndDb(int lastIssuePublished, DatabaseEntity database) {
        return EntireDBEntity.qRecordsByIssueAndDb(lastIssuePublished, database, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getEditorialCountRecordsByIssue(int issuePublished) {
        DatabaseEntity database = rs.getDatabaseEntity(CochraneCMSPropertyNames.getEditorialDbName());
        return ((Number) EntireDBEntity.qCountLatestRecordsByIssueAndDb(issuePublished, database, manager)
                                 .getSingleResult()).intValue();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<EntireDBEntity> getEditorialRecordsByIssue(int issuePublished, int size, int offset) {
        DatabaseEntity database = rs.getDatabaseEntity(CochraneCMSPropertyNames.getEditorialDbName());
        return (List<EntireDBEntity>) EntireDBEntity.qRecordsByIssueAndDb(issuePublished, database, manager)
                                              .setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @SuppressWarnings("unchecked")
    private EntireDBEntity findRecordByIssueAndName(int lastIssuePublished, String recordName) {
        List<EntireDBEntity> result = manager.createNamedQuery("findRecordNamesByIssueAndName").setParameter(
                LAST_ISSUE_PUBLISHED, lastIssuePublished).setParameter(RECORD_NAME, recordName).getResultList();
        if (result.size() == 0) {
            LOG.error(RECORD_NOT_FOUND_BY_TITLE_IN_ENTIRE_DB + recordName);
            return null;
        }
        return result.get(0);
    }

    public List<String> findRecordNames(String dbName, int offset, int limit) {
        Query query  = manager.createNamedQuery("findDistinctRecordNames").setParameter("db", dbName);
        if (offset > 0) {
            query.setFirstResult(offset);
        }
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    public EntireDBEntity findRecordByName(DatabaseEntity database, String recordName) {
        List<EntireDBEntity> result = findEntityByDbAndRecordName(database.getId(), recordName);
        return result.isEmpty() ? null : result.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public EntireRecordVO findRecordByName(Integer clDbId, String recordName) {
        ClDbEntity clDb = manager.find(ClDbEntity.class, clDbId);
        List<EntireDBEntity> result = (List<EntireDBEntity>) EntireDBEntity.queryRecordsByName(
                clDb.getDatabase().getId(), recordName, manager).getResultList();
        return result.isEmpty() ? null : new EntireRecordVO(result.get(0));
    }

    private List<EntireDBEntity> findEntityByDbAndRecordName(int dbId, String recordName) {
        return EntireDBEntity.queryRecordsByName(dbId, recordName, manager).getResultList();
    }

    private List<EntireDBEntity> findEntitiesByDbAndRecordName(int dbId, Collection<String> recordNames) {
        return EntireDBEntity.queryRecordsByNames(dbId, recordNames, manager).getResultList();
    }

    public List<EntireDBEntity> getRecordsByIds(Collection<Integer> ids) {
        return manager.createNamedQuery("findEntityByIds").setParameter(IDS, ids).getResultList();
    }

    public List<EntireDBEntity> getRecordsOrderById(DatabaseEntity database, int startId) {
        List<EntireDBEntity> records = null;
        try {
            records = manager.createQuery("select edb from EntireDBEntity edb where edb.database = :db "
                + " and edb.id >=:id order by edb.id asc").setParameter("db", database)
                    .setParameter("id", startId).getResultList();
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return records;
    }

    private void deleteFromEntire(BaseType bt, Integer clDbId, String recordName, boolean central,
                                  boolean removePublished) throws Exception {
        String dbName = bt.getId();
        deleteDirFromEntire(FilePathCreator.getFilePathToSourceEntire(dbName, recordName));
        if (bt.canHtmlConvert()) {
            deleteDirFromEntire(FilePathCreator.getRenderedDirPathEntire(dbName, recordName, RenderingPlan.HTML));
        }
        if (removePublished) {
            if (!central) {
                PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(recordName, clDbId, manager).executeUpdate();
            }
            deletePublishedRecords(PublishRecordEntity.queryPublishedRecordsByDb(
                    bt.getProductType().buildRecordNumber(recordName), clDbId, manager).getResultList());
        }
        if (central) {
            return;
        }
        deleteDirFromEntire(FilePathCreator.getFilePathToSourceEntire(dbName, recordName).replace(Extensions.XML, ""));
        if (bt.canPdfFopConvert()) {
            deleteDirFromEntire(FilePathCreator.getRenderedDirPathEntire(dbName, recordName, RenderingPlan.PDF_TEX));
            deleteDirFromEntire(FilePathCreator.getRenderedDirPathEntire(dbName, recordName, RenderingPlan.PDF_FOP));
        }
        deleteMl3gContentFromEntire(dbName, recordName, bt.hasStandaloneWml3g());
    }

    private void deleteDirFromEntire(String filePath) throws IOException {
        try {
            rps.deleteFile(filePath);
        } catch (FileNotFoundException e) {
            LOG.warn("Failed to delete " + filePath + ". " + e);
        }
    }

    private void deleteRevmanFromEntire(String recordName, String group) {
        if (group == null) {
            return;
        }
        String basePath = FilePathBuilder.getPathToEntireRevmanSrc(group);
        String recPath = basePath + recordName;
        String metadataPath = FilePathBuilder.buildMetadataRecordName(recPath);
        recPath += Extensions.XML;
        try {
            if (rps.isFileExists(recPath)) {
                rps.deleteFile(recPath);
            }
            if (rps.isFileExists(metadataPath)) {
                rps.deleteFile(metadataPath);
            }
        } catch (IOException ie) {
            LOG.error(ie);
        }
    }

    private void deleteMl3gContentFromEntire(String dbName, String recordName, boolean hasStandaloneWml3g) {
        if (!CmsUtils.isConversionTo3gAvailable(dbName)) {
            return;
        }
        String uri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recordName);
        try {
            rps.deleteFile(uri);
        } catch (IOException e) {
            LOG.error(String.format(FAILED_DELETE_WML3G_MSG_TEMP, Constants.XML_STR, uri));
        }
        if (hasStandaloneWml3g) {
            uri = FilePathCreator.getFilePathForEntireMl3gAssets(dbName, recordName);
            try {
                rps.deleteFile(uri);
            } catch (IOException e) {
                LOG.error(String.format(FAILED_DELETE_WML3G_MSG_TEMP, Constants.ASSETS_STR, uri));
            }
        }
    }

    private void restoreFromBackup(int issueId, String dbName, String recordName, BaseType baseType) throws Exception {
        String pathFrom = ContentLocation.ISSUE_COPY.getPathToPdf(issueId, dbName, null, recordName);
        String pathTo = ContentLocation.ENTIRE.getPathToPdf(issueId, dbName, null, recordName);
        CmsUtils.moveDir(pathFrom, pathTo, rps);
        restoreWml3gContentFromBackup(issueId, dbName, recordName, baseType.hasStandaloneWml3g(), false);
    }

    private void restoreFromBackup(int issueId, String dbName, String recordName, boolean clearPdf,
                                   BaseType baseType, boolean central) throws Exception {
        String filePath = FilePathCreator.getFilePathToSourceCopy(issueId, dbName, recordName);
        InputStream is = null;
        String pathTo;
        String pathFrom;
        String prefixForEntire = FilePathCreator.getEntireDirPath(dbName);
        try {
            is = rps.isFileExistsQuiet(filePath) ? rps.getFile(filePath) : null;
            if (is != null) {
                if (central) {
                    pathTo = FilePathCreator.addRecordNameToPath(prefixForEntire + "/src", recordName, dbName)
                            + Extensions.XML;
                } else {
                    pathTo = prefixForEntire + SRC_DIR + filePath.substring(filePath.lastIndexOf("/"));
                }
                rps.putFile(pathTo, is);
                rps.deleteFile(filePath);
            }
        } catch (IOException e) {
            LOG.error(e);
            IOUtils.closeQuietly(is);
            return;
        }

        String dirPath = filePath.replace(Extensions.XML, "");
        File[] files = rps.getFilesFromDir(dirPath);
        if (files != null && files.length != 0) {
            pathTo = FilePathCreator.addRecordNameToPath(prefixForEntire + SRC_DIR, recordName, dbName);
            CmsUtils.moveDir(dirPath, pathTo, rps);
        }
        if (clearPdf && baseType.canPdfFopConvert()) {
            pathTo = FilePathBuilder.PDF.getPathToEntirePdf(dbName, recordName);
            pathFrom = FilePathCreator.getRenderedDirPathCopy(issueId, dbName, RenderingPlan.PDF_TEX) + "/"
                    + recordName;
            CmsUtils.moveDir(pathFrom, pathTo, rps);

            pathFrom = FilePathBuilder.PDF.getPathToBackupPdfFop(issueId, dbName, recordName);
            pathTo = FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, recordName);
            CmsUtils.moveDir(pathFrom, pathTo, rps);
        }
        if (baseType.canHtmlConvert()) {
            pathTo = FilePathCreator.getRenderedDirPathEntire(dbName, recordName, RenderingPlan.HTML);
            pathFrom = FilePathCreator.getRenderedDirPathCopy(issueId, dbName, RenderingPlan.HTML) + "/" + recordName;
            CmsUtils.moveDir(pathFrom, pathTo, rps);
        }
        if (CmsUtils.isConversionTo3gAvailable(dbName)) {
            restoreWml3gContentFromBackup(issueId, dbName, recordName, baseType.hasStandaloneWml3g(), central);
        }
        try {
            String backupSrcDir = FilePathBuilder.replaceXmlExtension(filePath, "");
            if (rps.isFileExists(backupSrcDir)) {
                rps.deleteDir(backupSrcDir);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void restoreWml3gContentFromBackup(Integer issueId, String dbName, String recName,
                                               boolean hasStandaloneWml3g, boolean central) throws Exception {
        String srcXmlUri = FilePathBuilder.ML3G.getPathToBackupMl3gRecord(issueId, dbName, recName, central);
        String destXmlUri = FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, recName, central);

        if (!rps.isFileExistsQuiet(srcXmlUri)) {
            if (rps.isFileExistsQuiet(destXmlUri)) {
                rps.deleteFile(destXmlUri);
            }
            return;
        }

        StringBuilder errs = new StringBuilder();
        try {
            InputStream is = rps.getFile(srcXmlUri);
            rps.putFile(destXmlUri, is);
            rps.deleteFile(srcXmlUri);

        } catch (IOException e) {
            errs.append(String.format(FAILED_COPY_WML3G_MSG_TEMP, Constants.XML_STR, srcXmlUri, destXmlUri, e));
        }
        if (hasStandaloneWml3g) {
            Ml3gAssetsManager.copyAssetsFromOneLocation2Another(dbName, issueId, recName, Constants.UNDEF,
                    ContentLocation.ISSUE_COPY, ContentLocation.ENTIRE, errs);
        }
        if (errs.length() > 0) {
            LOG.error(errs);
        }
    }

    private void restoreAbstractsFromBackup(int issueId, String recName, TranslatedAbstractsHelper tah) {

        Set<String> langsExisted = tah.copyAbstractsFromBackup(issueId, recName);
        langsExisted.addAll(tah.copyAbstractsFromBackup(recName,
            FilePathBuilder.TR.getPathToBackupJatsTA(issueId, recName), FilePathBuilder.TR::getPathToJatsTARecord));
        tah.copyAbstractsFromBackup(recName,
            FilePathBuilder.TR.getPathToBackupWML3GTA(issueId, recName), FilePathBuilder.TR::getPathToWML3GTARecord);

        List<DbRecordVO> removed =  CochraneCMSBeans.getRecordManager().restoreTranslationsFromBackUp(
                issueId, recName, langsExisted);
        for (DbRecordVO vo: removed) {
            tah.deleteAbstract(vo.getLanguage(), recName, vo.getVersion());
        }
    }

    private boolean restoreJatsFromBackup(Integer issueId, String dbName, String recName, RecordMetadataEntity prevMeta)
            throws Exception {
        String pathFrom = FilePathBuilder.JATS.getPathToBackupDir(issueId, dbName, recName);
        if (prevMeta == null || prevMeta.isJats()) {
            String pathTo = FilePathBuilder.JATS.getPathToEntireDir(dbName, recName);
            boolean jatsDirExisted = CmsUtils.moveDir(pathFrom, pathTo, rps);
            return prevMeta != null || jatsDirExisted;
        }
        RepositoryUtils.deleteDir(pathFrom, RepositoryFactory.getRepository());
        return false;
    }
            
    private void restoreRevmanFromBackup(String recName, int issueId, String group) {
        if (group == null) {
            return;
        }
        String copyDir = FilePathBuilder.getPathToRevmanBackupSrc(issueId, group);
        String toDir = FilePathBuilder.getPathToEntireRevmanSrc(group);
        String metadataFileName = FilePathBuilder.buildMetadataRecordName(recName);
        String fileName = recName + Extensions.XML;
        RepositoryUtils.replaceFiles(copyDir + fileName, toDir + fileName, true, rps);
        RepositoryUtils.replaceFiles(copyDir + metadataFileName, toDir + metadataFileName, true, rps);
    }

    public List<RecordPublishVO> getRecordPublishVOByRecordIdsAndStates(List<Integer> recIds, List<Integer> states) {
        return EntireRecordPublishEntity.qVOByRecordIdsAndStates(recIds, states, manager).getResultList();
    }

    public List<Integer> getRecordIds(List<Integer> recIds, int state) {
        return EntireRecordPublishEntity.qRecordId(recIds, state, manager).getResultList();
    }

    public List<Integer> getRecordIds(List<Integer> recIds, List<Integer> states) {
        return EntireRecordPublishEntity.qRecordId(recIds, states, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void persistRecordPublish(Map<Integer, RecordPublishVO> recIdsMap) {
        Date date = new Date();
        List<EntireDBEntity> edbEntities = getRecordsByIds(recIdsMap.keySet());
        for (EntireDBEntity re: edbEntities) {
            RecordPublishVO rpVO = recIdsMap.get(re.getId());
            if (rpVO != null) {
                EntireRecordPublishEntity rpEntity = new EntireRecordPublishEntity();
                rpEntity.setName(rpVO.getName());
                rpEntity.setState(rpVO.getState());
                rpEntity.setDate(date);
                rpEntity.setRecord(re);
                manager.persist(rpEntity);
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteRecordPublish(Collection<Integer> recIds) {
        EntireRecordPublishEntity.qDeleteByRecordIds(recIds, manager).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateRecordPublishStateByRecordIds(Date date, int state, Collection<Integer> recordIds) {
        EntireRecordPublishEntity.qUpdateStateByRecordIds(date, state, recordIds, manager).executeUpdate();
    }

    private void updateRecordPublish(EntireDBEntity edbEntity, int recordId) {
        List<RecordPublishVO> rpVOs = DbRecordPublishEntity.qVOByRecordId(recordId, manager).getResultList();
        if (!rpVOs.isEmpty()) {
            RecordPublishVO rpVO = rpVOs.get(0);

            EntireRecordPublishEntity erpEntity = edbEntity.getRecordPublishEntity();
            if (erpEntity != null) {
                erpEntity.setState(rpVO.getState());
                erpEntity.setDate(rpVO.getDate());
                erpEntity.setRecord(edbEntity);
                manager.merge(erpEntity);
            } else {
                erpEntity = new EntireRecordPublishEntity();
                erpEntity.setState(rpVO.getState());
                erpEntity.setName(rpVO.getName());
                erpEntity.setDate(rpVO.getDate());
                erpEntity.setRecord(edbEntity);
                manager.persist(erpEntity);
            }
        }
    }

    private List<Integer> getIds(List<EntireDBEntity> entities) {
        List<Integer> ids = new ArrayList<Integer>(entities.size());
        for (EntireDBEntity entity : entities) {
            ids.add(entity.getId());
        }
        return ids;
    }
}