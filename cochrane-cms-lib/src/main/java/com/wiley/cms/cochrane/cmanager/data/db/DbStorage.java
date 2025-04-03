package com.wiley.cms.cochrane.cmanager.data.db;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.ErrorLogEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entity.DbRecordEntity;
import com.wiley.cms.cochrane.cmanager.export.data.ExportEntity;
import org.apache.commons.io.filefilter.TrueFileFilter;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.ModelController;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

@Stateless
@Local(IDbStorage.class)
public class DbStorage extends ModelController implements IDbStorage {
    private static final Logger LOG = Logger.getLogger(DbStorage.class);

    private static final String REPOSITORY_DIR = CochraneCMSProperties.getProperty(
            CochraneCMSPropertyNames.PREFIX_REPOSITORY);
    //private static final String REPOSITORY_SHADOW_DIR =
    //        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY_SHADOW);

    //private static final int ATHOUSAND = 1000;
    //private static final int AHUNDRED = 100;
    //private static final int ATEN = 10;
    private static final String DB_PARAM = "db";
    private static final String DB_ID_PARAM = "dbId";
    private static final String VALUE_PARAM = "value";
    private static final String ISSUE_PARAM = "issue";

    @EJB
    IRecordStorage recordStorage;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DbVO getDbVO(int clDbId) {
        ClDbEntity entity = find(ClDbEntity.class, clDbId);
        return new DbVO(entity);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateDatabaseAllCounter(int dbId, int count) {
        ClDbEntity entity = find(ClDbEntity.class, dbId);
        if (entity == null) {
            LOG.warn(String.format("cannot find db entity by [%d] to update", dbId));
            return;
        }
        entity.setAllCount(count);
        getManager().merge(entity);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Integer getDatabaseAllCounter(int dbId) {
        ClDbEntity entity = find(ClDbEntity.class, dbId);
        if (entity == null) {
            LOG.warn(String.format("cannot find db entity by [%d]", dbId));
            return 0;
        }
        return entity.getAllCount();
    }

    @SuppressWarnings("unchecked")
    public DbVO getNextDbVOByNameAndIssue(String dbName, int issueId) {
        try {
            IssueEntity issue = find(IssueEntity.class, issueId);
            List<Integer> list = em.createNamedQuery("selectNextIssues")
                    .setParameter("year", issue.getYear())
                    .setParameter("number", issue.getNumber())
                    .getResultList();

            ClDbEntity nextDb = findEntityByTitleAndIssue(getManager().find(IssueEntity.class, list.get(0)), dbName);
//            ClDbEntity nextDb = (ClDbEntity) manager.createNamedQuery("dbByTitleAndIssue")
//                    .setParameter("db", dbName)
//                    .setParameter("issue", manager.find(IssueEntity.class, list.get(0)))
//                    .getSingleResult();
            return new DbVO(nextDb);
        } catch (Exception e) {
            return null;
        }
    }

    public DbVO getDbVOByNameAndIssue(String dbName, int issueId) {
        try {
            IssueEntity issue = find(IssueEntity.class, issueId);
            ClDbEntity nextDb = findEntityByTitleAndIssue(issue, dbName);
            return new DbVO(nextDb);
        } catch (Exception e) {
            return null;
        }
    }

    private void clearDbTablesForArchiving(int id) {
        ClDbEntity db = find(ClDbEntity.class, id);
        if (db == null) {
            return;
        }

        LOG.debug(String.format("Issue job db tables cleaning started for %s", db.getTitle()));

        em.createNamedQuery("deleteQaNextJobByDb").setParameter(DB_PARAM, db).executeUpdate();
        em.createNamedQuery("deleteRndNextJobByDb").setParameter(DB_PARAM, db).executeUpdate();
        em.createNamedQuery("deleteSpecRndFilesByDb").setParameter(DB_ID_PARAM, id).executeUpdate();
        em.createNamedQuery("deleteSpecRndByDb").setParameter(DB_ID_PARAM, id).executeUpdate();
        em.createNamedQuery("deleteStartedJobByDb").setParameter(DB_PARAM, db).executeUpdate();
        em.createNamedQuery("deleteStartedJobQaByDb").setParameter(DB_PARAM, db).executeUpdate();

        ExportEntity.qDeleteByClDbId(db.getId(), em).executeUpdate();

        ErrorLogEntity.deleteErrorLog(BaseType.find(db.getTitle()).get().getDbId(), db.getIssue().getFullNumber(), em);

        db.setStatusDate(null);
        db.setAllCount(0);
        db.setRenderedCount(0);
        db.setApproved(false);

        LOG.debug(String.format("Issue job db tables clearing finished for %s", db.getTitle()));
    }

    private void clearDbTables(int id) {
        clearDbTablesForArchiving(id);
        
        ClDbEntity db = find(ClDbEntity.class, id);
        if (db == null) {
            return;
        }
        BaseType bt = BaseType.find(db.getTitle()).get();
        LOG.debug(String.format("Issue db tables clearing started for %s", db.getTitle()));

        em.createNamedQuery("deletePublishByDb").setParameter(DB_PARAM, db).executeUpdate();
        em.createNamedQuery("deletePublishWhenReadyByDb").setParameter(DB_PARAM, db).executeUpdate();
        em.createNamedQuery("deleteRecordByDb").setParameter(DB_PARAM, db).executeUpdate();

        PublishedAbstractEntity.queryDeletePublishedAbstractsByDb(id, em).executeUpdate();
        deletePublishedRecords(PublishRecordEntity.queryPublishedRecordsByDb(id, em).getResultList());
        em.createNamedQuery("deleteDfByDb").setParameter(DB_PARAM, db).executeUpdate();

        db.setInitialPackageDelivered(false);

        if (bt.isCentral()) {
            DbRecordEntity.deleteRecordsByDb(id, em).executeUpdate();
        }

        LOG.debug(String.format("Issue db tables clearing finished for %s", db.getTitle()));
    }

    private void deletePublishedRecords(List<PublishRecordEntity> list) {
        for (PublishRecordEntity pre: list) {
            em.remove(pre);
        }
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public boolean unArchiveIssueDb(int dbId) {
        ClDbEntity db = find(ClDbEntity.class, dbId);
        if (db != null) {
            updateAllRecordCount(dbId);
            updateAllAndRenderedRecordCount(dbId);

            db.setStatusDate(new Date());
            db.setArchiving(false);
            db.setArchived(false);
            getManager().merge(db);
            return true;
        }
        return false;
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void clearIssueDb(int dbId, boolean onlyForArchive) {
        if (onlyForArchive) {
            clearDbTablesForArchiving(dbId);

        } else {
            clearDbTables(dbId);
        }
        clearDbFolders(dbId, TrueFileFilter.INSTANCE);
    }

    private void clearDbFolders(int dbId, FilenameFilter filter) {
        ClDbEntity db = find(ClDbEntity.class, dbId);
        try {
            removeDirs(db, filter);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        setClearing(dbId, false);
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void clearDb(Integer clDbId) {
        clearDbTables(clDbId);
        String dbName = find(ClDbEntity.class, clDbId).getTitle();
        boolean clearPdf = BaseType.find(dbName).get().canPdfFopConvert();
        boolean cdsr = dbName.equals(CochraneCMSPropertyNames.getCDSRDbName());
        Collection<String> resultCdNumbers = null;
        if (cdsr || dbName.equals(CochraneCMSPropertyNames.getEditorialDbName())
                || dbName.equals(CochraneCMSPropertyNames.getCentralDbName())
                || dbName.equals(CochraneCMSPropertyNames.getCcaDbName())) {
            try {
                resultCdNumbers = EntireDBStorageFactory.getFactory().getInstance().clearDb(clDbId, clearPdf);
            } catch (Exception e) {
                LOG.error(e, e);
                return;
            }
        }

        clearDbFolders(clDbId, new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return !(name.equals("input") || name.equals("wml3g"));
            }
        });
        Hooks.captureRecords(clDbId, resultCdNumbers, Hooks.CLEAR_DB_END);
    }

    private void removeDirs(ClDbEntity db, FilenameFilter filter) throws Exception {
        LOG.debug("Removing dirs started.." + db.getTitle());
        IRepository rp = RepositoryFactory.getRepository();

        File[] files =
                rp.getFilesFromDir("/" + REPOSITORY_DIR + "/" + db.getIssue().getId() + "/" + db.getTitle() + "/");
        ArrayList<String> srcDir = new ArrayList<String>(2);
        if (files != null) {
            removeDirs(db, filter, rp, files, srcDir);
        }
        LOG.debug("Removing dirs finished.." + db.getTitle());
    }

    private void removeDirs(ClDbEntity db, FilenameFilter filter, IRepository rp, File[] files,
        ArrayList<String> srcDir) throws Exception {

        for (File file : files) {
            if (!filter.accept(file.getParentFile(), file.getName())) {
                continue;
            }
            if (db.getTitle().equals(CochraneCMSPropertyNames.getCentralDbName())) {
                if (file.getName().matches("[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]")) {
                    srcDir.add(file.getName());
                }
            }

            rp.deleteDir(
                    "/" + REPOSITORY_DIR + "/" + db.getIssue().getId() + "/" + db.getTitle() + "/" + file.getName());
        }
    }

    public void setClearing(int dbId, boolean clearing) {
        ClDbEntity db = find(ClDbEntity.class, dbId);
        db.setClearing(clearing);
        db.setStatusDate(new Date());
    }

    public void updateRecordCount(int id) {
        updateAllRecordCount(id);
        updateRenderedRecordCount(id);
    }

    public void updateAllRecordCount(int id) {
        int count = recordStorage.getDbRecordListCount(id, null, 0);
        updateAllRecordCount(id, count);
    }

    public void updateAllRecordCount(int id, int count) {
        ClDbEntity db = find(ClDbEntity.class, id);
        db.setAllCount(count);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateAllAndRenderedRecordCount(int id) {
        int count = recordStorage.getDbRecordListCount(id, null, 0);
        int renderedCount = recordStorage.getDbRecordListCount(id, null, SearchRecordStatus.RENDER_PASSED);
        ClDbEntity db = find(ClDbEntity.class, id);
        db.setAllCount(count);
        db.setRenderedCount(renderedCount);
        getManager().merge(db);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateRenderedRecordCount(int id) {
        int count = recordStorage.getDbRecordListCount(id, null, SearchRecordStatus.RENDER_PASSED);
        updateRenderedRecordCount(id, count);
    }

    public void updateRenderedRecordCount(int id, int size) {
        ClDbEntity db = find(ClDbEntity.class, id);
        db.setRenderedCount(size);
    }

    public void setDbArchiving(ClDbEntity db, boolean value) {
        em.createNamedQuery("dbSetArchiving")
                .setParameter(DB_PARAM, db)
                .setParameter(VALUE_PARAM, value)
                .executeUpdate();
        em.flush();
    }

    public void setDbArchived(ClDbEntity db, boolean value) {
        em.createNamedQuery("dbSetArchived")
                .setParameter(DB_PARAM, db)
                .setParameter(VALUE_PARAM, value)
                .executeUpdate();
    }

    public List<ClDbEntity> getDbByIssue(IssueEntity issue) {
        return ClDbEntity.queryClDb(issue.getId(), em).getResultList();
    }

    public void deleteDbByIssue(IssueEntity issue) {
        em.createNamedQuery("deleteDbsByIssue")
                .setParameter(ISSUE_PARAM, issue)
                .executeUpdate();
    }

    public void setInitialPackageDeliveredByIssueIdAndTitle(int issueId, String title) {
        IssueEntity ie = em.find(IssueEntity.class, issueId);
        ClDbEntity entity = findEntityByTitleAndIssue(ie, title);

        entity.setInitialPackageDelivered(true);
        em.merge(entity);
        em.flush();
    }

    public int findEditorialDbIdByCDSRDbId(int sysrevDbId) {
        ClDbEntity sysrevClDb = em.find(ClDbEntity.class, sysrevDbId);
        ClDbEntity editorialClDb = findEntityByTitleAndIssue(sysrevClDb.getIssue(),
                CochraneCMSPropertyNames.getEditorialDbName());
        //(ClDbEntity) manager.createNamedQuery("dbByTitleAndIssue").setParameter("issue",
        //        sysrevClDb.getIssue()).setParameter("db", CLEDITORIAL).getSingleResult();
        return editorialClDb.getId();
    }

    public boolean isClearingInProgress(int dbId) {
        ClDbEntity db = em.find(ClDbEntity.class, dbId);
        return db != null && db.isClearing();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ClDbEntity getDbAfterDbForClearing(int dbType, int issueYear, int issueNumber) {
        List<ClDbEntity> list = ClDbEntity.queryClDb(dbType, issueYear, issueNumber, getManager()).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    private ClDbEntity findEntityByTitleAndIssue(IssueEntity issueId, String title) {
        return (ClDbEntity) em.createNamedQuery("dbByTitleAndIssue")
                .setParameter(ISSUE_PARAM, issueId).setParameter(DB_PARAM, title).getSingleResult();
    }
}