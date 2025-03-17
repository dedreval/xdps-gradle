package com.wiley.cms.cochrane.cmanager.ebch.process;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class GenProcessorParameters {
    private int dbId;
    private String userName;
    private String dbName;

    public GenProcessorParameters(String dbName, String userName) {
        dbId = 0;
        this.userName = userName;
        this.dbName = dbName;
    }

    public GenProcessorParameters(int dbId, String userName) {
        this.dbId = dbId;
        this.userName = userName;
        dbName = null;
    }

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String name) {
        dbName = name;
    }

    public boolean isEntire() {
        return dbId == 0;
    }
}

