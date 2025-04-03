package com.wiley.cms.cochrane.cmanager.data.stats;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.entity.DbStatsEntity;
import com.wiley.cms.cochrane.process.ModelController;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.XLSHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/16/2020
 */
@Local(IStatsManager.class)
@Singleton
@Lock(LockType.READ)
public class StatsManager extends ModelController implements IStatsManager {
    private static final Logger LOG = Logger.getLogger(StatsManager.class);

    private static final int TABLE_ENTIRE_SIZE = 5;
    private static final int TABLE_ISSUE_SIZE = 14;

    private static final String FIELD_DATE = "Date";
    private static final String FIELD_CODE = "Code";
    private static final String FIELD_TOTAL = "Total";
    private static final String FIELD_WITHDRAWN_R = "Withdrawn Reviews";
    private static final String FIELD_WITHDRAWN_P = "Withdrawn Protocols";

    private static final ReentrantLock STATS_UPDATE_LOCK = new ReentrantLock();

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "ActivityLogService")
    private IActivityLogService logService;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean isStatsUpdating() {
        return STATS_UPDATE_LOCK.isLocked();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void generateReport(int issue, List<IDbStats> entireStats, List<IDbStats> issueStats)  {
        try {
            File tempFile = File.createTempFile(Constants.TEMP, "");
            XLSHelper xlsHelper = new XLSHelper(tempFile, "Overall", false, 1);

            int row = 0;
            xlsHelper.addTitle("Entire databases", 0, row);
            xlsHelper.mergeCells(0, 0, TABLE_ENTIRE_SIZE - 1, row++);
            int col = 0;
            xlsHelper.addTitle(FIELD_DATE, col++, row);
            xlsHelper.addTitle(FIELD_CODE, col++, row);
            xlsHelper.addTitle(FIELD_TOTAL, col++, row);
            xlsHelper.addTitle("Total Reviews", col++, row);
            xlsHelper.addTitle("Total Protocols", col, row);
            xlsHelper.addTitle(FIELD_WITHDRAWN_R, col++, row);
            xlsHelper.addTitle(FIELD_WITHDRAWN_P, col, row++);

            for (IDbStats dbStats: entireStats) {
                addEntireStatsToReport(dbStats, row++, xlsHelper);
            }
                         
            xlsHelper.addTitle("Issue databases", 0, row);
            xlsHelper.mergeCells(0, row, TABLE_ISSUE_SIZE - 1, row++);
            col = 0;
            xlsHelper.addTitle(FIELD_DATE, col++, row);
            xlsHelper.addTitle(FIELD_CODE, col++, row);
            xlsHelper.addTitle("Total DOIs", col++, row);
            xlsHelper.addTitle("New", col++, row);
            xlsHelper.addTitle("Updated", col++, row);
            xlsHelper.addTitle(FIELD_WITHDRAWN_R, col++, row);
            xlsHelper.addTitle(FIELD_WITHDRAWN_P, col++, row);
            xlsHelper.addTitle("New Reviews", col++, row);
            xlsHelper.addTitle("New Protocols", col++, row);
            xlsHelper.addTitle("Updated Protocols", col++, row);
            xlsHelper.addTitle("NS only", col++, row);
            xlsHelper.addTitle("NS and CC", col++, row);
            xlsHelper.addTitle("CC only", col++, row);
            xlsHelper.addTitle("Translations", col, row++);
            //xlsHelper.addTitle("Updated with MeshTerms", col, row++);
            
            for (IDbStats dbStats: issueStats) {
                addIssueStatsToReport(dbStats, row++, xlsHelper);
            }

            xlsHelper.closeAndSaveToRepository(tempFile, CochraneCMSPropertyNames.getStatsReportPath(issue), false,
                    RepositoryFactory.getRepository());

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void addEntireStatsToReport(IDbStats dbStats, int row, XLSHelper xlsHelper) throws Exception {
        int col = 0;
        xlsHelper.addValue(dbStats.getGenerationDateStr(), col++, row);
        xlsHelper.addValue(dbStats.getShortDbName(), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotal()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotalReviews()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotalProtocols()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getReviewsWithdrawn()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getProtocolsWithdrawn()), col, row);
    }

    private void addIssueStatsToReport(IDbStats dbStats, int row, XLSHelper xlsHelper) throws Exception {
        int col = 0;
        xlsHelper.addValue(dbStats.getGenerationDateStr(), col++, row);
        xlsHelper.addValue(dbStats.getShortDbName(), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotal()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotalNew()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTotalUpdated()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getReviewsWithdrawn()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getProtocolsWithdrawn()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getReviewsNew()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getProtocolsNew()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getProtocolsUpdated()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getOnlyNS()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getNSAndCC()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getOnlyCC()), col++, row);
        xlsHelper.addValue(getStatItem(dbStats.getTranslations()), col++, row);
        //xlsHelper.addValue(getStatItem(dbStats.getMeshUpdated()), col, row);
    }

    private String getStatItem(int statItem) {
        return  statItem >= 0 ? "" + statItem : Constants.NA;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateStats(int issueYear, int issueNumber, String user, boolean lastIssue) throws CmsException {
        int issueId = rs.findIssue(issueYear, issueNumber);
        if (issueId == DbEntity.NOT_EXIST_ID) {
            throw new CmsException(String.format("Can't find issue by year: %d and number: %d",
                    issueYear, issueNumber));
        }
        updateStats(issueId, user);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateStats(Integer issueId, String user) {
        if (STATS_UPDATE_LOCK.tryLock()) {
            try {
                LOG.info(String.format("Statistic for Entire and Issue [%d] databases is updating ...", issueId));

                Map<Integer, ClDbEntity> dbMap = getDatabases(issueId);
                if (dbMap.isEmpty()) {
                    LOG.warn(String.format("No Entire and Issue databases for Issue [%d]", issueId));
                    return;
                }
                Date date = new Date();
                IssueEntity issue = dbMap.values().iterator().next().getIssue();
                int fullIssueNumber = issue.getFullNumber();
                logService.log(LogEntity.LogLevel.INFO, LogEntity.EntityLevel.ISSUE, ILogEvent.UPDATE_STATS,
                        issue.getId(), issue.getTitle(), user, null);
                updateStats(fullIssueNumber, date, dbMap, (List<DbStatsEntity>) DbStatsEntity.queryStatsByIssue(
                                        fullIssueNumber, getManager()).getResultList());

                LOG.info(String.format("Statistic for Entire and Issue [%d] databases updated", issueId));
            } finally {
                STATS_UPDATE_LOCK.unlock();
            }
        } else {
            LOG.info("Statistic is currently updating within another thread ...");
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<IDbStats> getIssueStats(Integer issueId) {
        return (List<IDbStats>) DbStatsEntity.queryStats(getDatabaseIds(issueId), getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<IDbStats> getLastEntireStats() {
        List<IDbStats> ret = new ArrayList<>();
        addStatsToList(DatabaseEntity.CDSR_KEY, ret);
        addStatsToList(DatabaseEntity.EDITORIAL_KEY, ret);
        addStatsToList(DatabaseEntity.CCA_KEY,  ret);
        addStatsToList(DatabaseEntity.CENTRAL_KEY,  ret);
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<IDbStats> getEntireStats(Integer issueId) {
        List<ClDbEntity> list = rs.getDbList(issueId);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        int fullIssueNumber = list.get(0).getIssue().getFullNumber();
        List<IDbStats> ret = new ArrayList<>();
        addStatsToList(fullIssueNumber, DatabaseEntity.CDSR_KEY, ret);
        addStatsToList(fullIssueNumber, DatabaseEntity.EDITORIAL_KEY, ret);
        addStatsToList(fullIssueNumber, DatabaseEntity.CCA_KEY,  ret);
        addStatsToList(fullIssueNumber, DatabaseEntity.CENTRAL_KEY,  ret);
        return ret;
    }

    private void addStatsToList(int issue, Integer dbId, List<IDbStats> ret) {
        List<IDbStats> list = (List<IDbStats>) DbStatsEntity.queryStats(issue, dbId, getManager()).getResultList();
        if (!list.isEmpty()) {
            ret.add(list.get(0));
        }
    }

    private void addStatsToList(Integer dbId, List<IDbStats> ret) {
        List<IDbStats> list = (List<IDbStats>) DbStatsEntity.queryStats(dbId, getManager()).getResultList();
        if (!list.isEmpty()) {
            ret.add(list.get(0));
        }
    }

    private void updateStats(int fullIssueNumber, Date date, Map<Integer, ClDbEntity> dbMap,
                             List<DbStatsEntity> existingStats) {
        DbStatsEntity[] cdsrStats = {null, null};
        DbStatsEntity[] centralStats = {null, null};
        DbStatsEntity[] editorialStats = {null, null};
        DbStatsEntity[] ccaStats = {null, null};

        for (DbStatsEntity dbStats : existingStats) {
            ClDbEntity clDb = dbStats.getClDbEntity();
            int dbType = clDb.getDatabase().getId();
            switch (dbType) {
                case DatabaseEntity.CDSR_KEY:
                    cdsrStats[clDb.getId() == dbType ? 0 : 1] = dbStats;
                    break;
                case DatabaseEntity.CENTRAL_KEY:
                    centralStats[clDb.getId() == dbType ? 0 : 1] = dbStats;
                    break;
                case DatabaseEntity.EDITORIAL_KEY:
                    editorialStats[clDb.getId() == dbType ? 0 : 1] = dbStats;
                    break;
                case DatabaseEntity.CCA_KEY:
                    ccaStats[clDb.getId() == dbType ? 0 : 1] = dbStats;
                    break;
                default:
                    break;
            }
        }
        List<IDbStats> entireList = new ArrayList<>();

        updateEntireStats(fullIssueNumber,
            checkExist(fullIssueNumber, cdsrStats[0], DatabaseEntity.CDSR_KEY, date, dbMap, true, entireList),
            checkExist(fullIssueNumber, centralStats[0], DatabaseEntity.CENTRAL_KEY, date, dbMap, true, entireList),
            checkExist(fullIssueNumber, editorialStats[0], DatabaseEntity.EDITORIAL_KEY, date, dbMap, true, entireList),
            checkExist(fullIssueNumber, ccaStats[0], DatabaseEntity.CCA_KEY, date, dbMap, true, entireList));
        List<IDbStats> issueList = new ArrayList<>();
        updateIssueStats(fullIssueNumber,
            checkExist(fullIssueNumber, cdsrStats[1], DatabaseEntity.CDSR_KEY, date, dbMap, false, issueList),
            checkExist(fullIssueNumber, centralStats[1], DatabaseEntity.CENTRAL_KEY, date, dbMap, false, issueList),
            checkExist(fullIssueNumber, editorialStats[1], DatabaseEntity.EDITORIAL_KEY, date, dbMap, false, issueList),
            checkExist(fullIssueNumber, ccaStats[1], DatabaseEntity.CCA_KEY, date, dbMap, false, issueList));

        generateReport(fullIssueNumber, entireList, issueList);
    }

    private void updateIssueStats(int fullIssueNumber, DbStatsEntity cdsrStats, DbStatsEntity centralStats,
                                  DbStatsEntity editorialStats, DbStatsEntity ccaStats) {

        DbStatsEntity.procedureIssueStats(fullIssueNumber, centralStats, editorialStats, ccaStats, getManager());
        DbStatsEntity.procedureCDSRIssueStats(fullIssueNumber, cdsrStats, getManager());

        commit(cdsrStats);
        commit(centralStats);
        commit(editorialStats);
        commit(ccaStats);
    }

    private void updateEntireStats(int fullIssueNumber, DbStatsEntity cdsrStats, DbStatsEntity centralStats,
                                   DbStatsEntity editorialStats, DbStatsEntity ccaStats) {
        boolean spd = CmsUtils.isScheduledIssueNumber(fullIssueNumber);
        int issue = spd ? CmsUtils.getFullIssueNumber(LocalDate.now()) : fullIssueNumber;

        DbStatsEntity.procedureEntireStats(issue, cdsrStats, centralStats, editorialStats, ccaStats, getManager());

        commit(cdsrStats);
        commit(centralStats);
        commit(editorialStats);
        commit(ccaStats);
    }

    private DbStatsEntity checkExist(int fullIssueNumber, DbStatsEntity statsEntity, int dbKey, Date date, Map<Integer,
            ClDbEntity> databases, boolean entire, List<IDbStats> list) {
        DbStatsEntity ret;
        if (statsEntity == null) {
            ClDbEntity issueClDbEntity = databases.get(dbKey);
            ClDbEntity clDbEntity = entire 
                    ? (ClDbEntity) ClDbEntity.queryClDb(issueClDbEntity.getDatabase(), getManager()).getSingleResult()
                    : issueClDbEntity;
            ret = new DbStatsEntity(fullIssueNumber, clDbEntity, date);
        } else {
            ret = statsEntity;
            ret.setGenerationDate(date);
        }
        if (list != null) {
            list.add(ret);
        }
        return ret;
    }

    private void commit(DbStatsEntity stats) {
        if (stats.exists()) {
            getManager().merge(stats);
        }  else {
            getManager().persist(stats);
        }
    }

    private List<Integer> getDatabaseIds(Integer issueId) {
        List<Integer> ret = new ArrayList<>();
        List<ClDbEntity> list = rs.getDbList(issueId);
        list.forEach(clDb -> ret.add(clDb.getId()));
        return ret;
    }

    private Map<Integer, ClDbEntity> getDatabases(Integer issueId) {
        Map<Integer, ClDbEntity> ret = new HashMap<>();
        List<ClDbEntity> list = rs.getDbList(issueId);
        list.forEach(clDb -> ret.put(clDb.getDatabase().getId(), clDb));
        return ret;
    }
}
