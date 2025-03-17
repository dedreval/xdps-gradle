package com.wiley.cms.cochrane.cmanager;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.data.DbPage;
import com.wiley.cms.cochrane.cmanager.data.StatsUpdater;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/23/2017
 */
@Local(ICochranePageManager.class)
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CochranePageManager implements ICochranePageManager {

    private static final DbPage EMPTY_PAGE = new DbPage(0, 0);

    @EJB(beanName = "DbStorage")
    private IDbStorage dbs;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    public int getRecordListCount(String dbName) {
        return getDatabaseStats(dbName).getAllCount();
    }

    public DbPage getDatabaseStats(String dbName) {

        if (dbName == null) {
            return EMPTY_PAGE;
        }

        DbPage stats = StatsUpdater.getStats(dbName);
        if (!stats.hasInitializedCounter()) {
            initDatabaseStats(stats, dbs.getDatabaseAllCounter(stats.dbId));
        }
        return stats;
    }

    public void updateDatabaseStats(DbPage stats) {
        int count = edbs.getRecordListCount(stats.dbId);
        dbs.updateDatabaseAllCounter(stats.dbId, count);
        initDatabaseStats(stats, count);
    }

    private void initDatabaseStats(DbPage stats, int count) {
        stats.setAllCount(count);
        stats.setFirstPageRecords(null);
    }
}
