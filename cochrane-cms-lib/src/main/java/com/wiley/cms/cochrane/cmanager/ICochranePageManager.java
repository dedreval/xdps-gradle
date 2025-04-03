package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.data.DbPage;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/23/2017
 */
public interface ICochranePageManager {

    int getRecordListCount(String dbName);

    DbPage getDatabaseStats(String dbName);

    void updateDatabaseStats(DbPage dbStats);
}
