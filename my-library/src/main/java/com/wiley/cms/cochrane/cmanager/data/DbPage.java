package com.wiley.cms.cochrane.cmanager.data;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.process.task.UpTimes;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/24/2017
 */
public class DbPage {

    public final int dbId;

    private final UpTimes upTimes = new UpTimes();

    private int allCount = -1;
    private List<EntireRecordWrapper> firstPageRecords = null;

    public DbPage(int dbId) {
        this.dbId = dbId;
    }

    public DbPage(int id, int allCount) {
        this(id);
        this.allCount = allCount;
    }

    public boolean hasFirstPage() {
        return firstPageRecords != null;
    }

    public List<EntireRecordWrapper> getFirstPageRecords() {
        return firstPageRecords;
    }

    public void setFirstPageRecords(List<EntireRecordWrapper> records) {

        firstPageRecords = records;
    }

    public void setAllCount(int count) {
        allCount = count;
    }

    public int getAllCount() {
        return allCount;
    }

    public boolean hasInitializedCounter() {
        return allCount >= 0;
    }

    public UpTimes upTimes() {
        return upTimes;
    }
}
