package com.wiley.cms.cochrane.cmanager.parser;

import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.Record;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 28-Apr-2007
 */
public class ParsingResult {

    private List<Record> records;
    private boolean isCompleted;
    private boolean isSuccessful;
    private int issueId;
    private String dbName;

    private int badCount;

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }


    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }


    public List<Record> getRecords() {
        return records == null ? Collections.emptyList() : records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }


    public int getIssueId() {
        return issueId;
    }

    public void setIssueId(int issueId) {
        this.issueId = issueId;
    }


    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }


    public int getBadCount() {
        return badCount;
    }

    public void setBadCount(int badCount) {
        this.badCount = badCount;
    }
}
