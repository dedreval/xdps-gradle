package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.InputUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 01.12.2009
 */
public class EntireRecordWrapper extends RecordWrapper {
    private static final long serialVersionUID = 1L;

    private EntireDBEntity entity;
    private EntireDbWrapper db;
    private String groupName;

    public EntireRecordWrapper() {
        super();
    }

    public EntireRecordWrapper(int recordId) {
        super();
        askEntity(recordId);
        extraInit();
    }

    public EntireRecordWrapper(EntireDBEntity entity) {
        super();
        this.entity = entity;
        extraInit();
    }

    @Override
    public void askEntity(int recordId, boolean initMeta) {
        askEntity(recordId);
        extraInit();
    }

    private void extraInit() {
        if (entity != null) {
            db = new EntireDbWrapper(entity.getDbName());
            setNumber();
            initPrevious(true, getFullIssueNumber(null));
        }
    }

    @Override
    public boolean isQasSuccessful() {
        return true;
    }

    @Override
    public boolean isRenderingSuccessful() {
        return true;
    }

    private void askEntity(int recordId) {
        entity = DbUtils.exists(recordId) ? getEntireDbStorage().findRecord(recordId) : null;
    }

    public static List<EntireRecordWrapper> getRecordWrapperList(String dbName) {
        return getRecordWrapperList(dbName, 0, 0, null, null, SearchRecordOrder.NONE, false);
    }

    public static List<EntireRecordWrapper> getRecordWrapperList(String dbName, int beginIndex, int amount) {
        return getRecordWrapperList(dbName, beginIndex, amount, null, null, SearchRecordOrder.NONE, false);
    }

    public static List<EntireRecordWrapper> getProcessEntireRecordWrapperList(String dbName, int beginIndex,
        int limit, int orderField, boolean orderDesc, int processId) {

        return getEntireRecordWrapperList(
            getEntireDbStorage().getRecordList(dbName, beginIndex, limit, null, null, orderField, orderDesc,
                    processId));
    }

    public static List<EntireRecordWrapper> getRecordWrapperList(String dbName, int beginIndex, int amount,
        String[] items, String fileStatus, int orderField, boolean orderDesc) {
        return getEntireRecordWrapperList(
                getEntireDbStorage().getRecordList(dbName, beginIndex, amount, items, fileStatus, orderField,
                        orderDesc));
    }

    public static List<EntireRecordWrapper> getEntireRecordWrapperList(List<EntireDBEntity> list) {
        List<EntireRecordWrapper> result = new ArrayList<>();
        for (EntireDBEntity entity : list) {
            result.add(new EntireRecordWrapper(entity));
        }
        return result;
    }

    public static int getRecordListCount(String dbName, String[] items, String fileStatus) {
        return getEntireDbStorage().getRecordListCount(dbName, items, fileStatus);
    }

    @Override
    public String getName() {
        return entity.getName();
    }

    @Override
    public String getUnitTitle() {
        return entity.getUnitTitle();
    }

    @Override
    public Integer getId() {
        return entity.getId();
    }

    @Override
    public String getDbName() {
        return db.getDbName();
    }

    @Override
    public Integer getIssueId() {
        return null;
    }

    @Override
    public Integer getDeliveryFileId() {
        return null;
    }

    @Override
    public String getDbFullName() {
        return db.getFullName();
    }

    @Override
    public String getDbShortName() {
        return db.getShortName();
    }

    @Override
    public boolean isRawDataExists() {
        String rawDataURIString = getRawDataURI();
        IRepository rps = RepositoryFactory.getRepository();
        try {
            return rps.isFileExists(rawDataURIString);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getRevManStatsDataURI() {
        //String statsDataUriString = getUriString("StatsDataOnly.rm5");
        String statsDataUriString = FilePathBuilder.buildStatsDataPathByUri(getRecordPath(), getName());
        IRepository rps = RepositoryFactory.getRepository();
        try {
            return rps.isFileExists(statsDataUriString) ? statsDataUriString : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getRecordPath() {
        return FilePathCreator.getFilePathToSourceEntire(entity.getDbName(), entity.getName());
    }

    @Override
    public String getWML3GPath() {
        return FilePathBuilder.ML3G.getPathToEntireMl3gRecord(getDbName(), getName(), false);
    }

    @Override
    public String getRawDataFile() throws IOException {
        String rawDataURIString = getRawDataURI();
        IRepository rps = RepositoryFactory.getRepository();
        return InputUtils.readStreamToString(rps.getFile(rawDataURIString));
    }

    public int getLastPublishedIssue() {
        return entity.getLastIssuePublished();
    }

    @Override
    public String getUnitStatus() {
        return entity.getUnitStatus() == null ? "" : entity.getUnitStatus().getName();
    }

    @Override
    public String getProductSubtitle() {
        return entity.getProductSubtitle() == null ? "" : entity.getProductSubtitle().getName();
    }

    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public boolean isTranslationUpdated() {
        return entity.getUnitStatus() != null && entity.getUnitStatus().isTranslationUpdated();
    }
}
